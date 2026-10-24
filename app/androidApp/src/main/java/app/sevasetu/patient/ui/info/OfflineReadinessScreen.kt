package app.sevasetu.patient.ui.info

import androidx.compose.foundation.layout.Column
import app.sevasetu.patient.ui.i18n.tr
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.ScreenBand
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.symptom.loadSymptomRules
import app.sevasetu.patient.ui.theme.Accent
import app.sevasetu.patient.ui.theme.ColorPriorityEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What works with no signal on this phone right now (PRD 7.2), checked, not assumed; and what is waiting to send. */
@Composable
fun OfflineReadinessScreen(
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit,
    onOpenDemoPanel: () -> Unit
) {
    val app = SevaApplication.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val directory by app.directory.data.collectAsState()
    val visit by app.visits.active.collectAsState()
    val hospitalId = visit?.hospital?.id ?: app.prefs.current.hospitalId
    val hasMap = remember(hospitalId) { app.nav.graph(hospitalId) != null }
    val rules = remember { runCatching { loadSymptomRules(context) }.getOrNull() }
    var pending by remember { mutableIntStateOf(0) }
    var failed by remember { mutableIntStateOf(0) }
    var sending by remember { mutableStateOf(false) }

    suspend fun count() = withContext(Dispatchers.IO) {
        val all = app.database.dao().getAllOutboxItems()
        pending = all.count { it.state != "FAILED" }
        failed = all.count { it.state == "FAILED" }
    }
    LaunchedEffect(Unit) { count() }

    SevaScaffold(title = tr("Ready offline?"), onBack = onBack, onEmergencyClick = onEmergencyClick) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ScreenBand(
                title = tr("Works with no signal"),
                tone = "mint",
                lead = tr("Emergency, the map, the symptom check and your last ticket all live on this phone.")
            )
            Spacer(modifier = Modifier.height(16.dp))

            InfoCard {
                Text(tr("ON THIS PHONE"), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Check(true, tr("Emergency screen, 112 and 108"))
                Check(hasMap, if (hasMap) tr("Indoor map and directions") else tr("Indoor map (not drawn for this hospital yet)"))
                Check(rules != null, tr("Symptom check, {n} warning signs (rules {version})", "n" to (rules?.warningSigns?.size ?: 0), "version" to (rules?.version ?: "–")))
                Check(directory.fetchedAt > 0, if (directory.fetchedAt > 0) tr("Hospitals, departments and doctors") else tr("Hospital list (built-in copy; connect once to update)"))
                Check(visit != null, if (visit != null) tr("Your ticket {token} and its QR slip", "token" to visit?.token) else tr("No ticket saved yet"))
            }

            Spacer(modifier = Modifier.height(16.dp))

            InfoCard {
                Text(tr("WAITING TO SEND"), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    when {
                        pending == 0 && failed == 0 -> tr("Nothing. Everything has reached the hospital.")
                        else -> tr("{n} waiting", "n" to pending) + if (failed > 0) ", " + tr("{n} refused by the hospital", "n" to failed) else ""
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    tr("Sent in this order when signal returns: urgent help first, then Priority and Routine registrations, then the rest."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pending > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    BigButton(
                        text = if (sending) "Sending…" else tr("Send now"),
                        icon = Icons.Default.CloudUpload,
                        enabled = !sending,
                        onClick = {
                            sending = true
                            scope.launch {
                                runCatching { app.outboxManager.flushOutbox() }
                                count()
                                sending = false
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                tr("To try it: turn on airplane mode, then register, scan stickers or press urgent help. ") +
                    tr("Tickets made offline show \"Waiting to be confirmed\" until the hospital confirms them."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!app.isLive) {
                Spacer(modifier = Modifier.height(12.dp))
                BigButton(text = tr("Simulator controls"), type = ButtonType.SECONDARY, onClick = onOpenDemoPanel)
            }
        }
    }
}

@Composable
private fun Check(ok: Boolean, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = if (ok) tr("Ready") else tr("Not ready"),
            tint = if (ok) Accent else ColorPriorityEdge,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

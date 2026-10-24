package app.sevasetu.patient.ui.info

import app.sevasetu.patient.ui.i18n.floorLabel
import android.content.Intent
import app.sevasetu.patient.ui.i18n.tr
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.ScreenBand
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StatusPill
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.LocalExtraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SERVICE_NAMES = mapOf("lab" to "Laboratory", "xray" to "X-ray", "pharmacy" to "Pharmacy", "ultrasound" to "Ultrasound", "ecg" to "ECG")

/**
 * Before you visit (PRD 8.1, 10): is my doctor in, is the X-ray working, and the way to the gate. From the hospital's
 * public tables, refreshed when online and kept for offline use with the time it was last checked.
 */
@Composable
fun BeforeYouVisitScreen(
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val context = LocalContext.current
    val app = SevaApplication.instance
    val directory by app.directory.data.collectAsState()
    var hospitalId by rememberSaveable { mutableStateOf(app.visits.active.value?.hospital?.id ?: app.prefs.current.hospitalId) }
    var refreshing by rememberSaveable { mutableStateOf(true) }
    var fresh by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        fresh = app.directory.refresh()
        refreshing = false
    }

    val hospital = directory.hospitals.firstOrNull { it.id == hospitalId }
    val doctors = directory.doctors.filter { it.id.startsWith("$hospitalId-") }
    val checked = directory.fetchedAt.takeIf { it > 0 }?.let { SimpleDateFormat("HH:mm, d MMM", Locale.US).format(Date(it)) }

    SevaScaffold(
        title = tr("Before you visit"),
        onBack = onBack,
        isOffline = !refreshing && !fresh && app.isLive,
        lastUpdatedTime = checked,
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                directory.hospitals.forEach { h ->
                    val on = h.id == hospitalId
                    Text(
                        text = h.name.substringBefore(" ").uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) Color.White else Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) LocalExtraColors.current.frame else LocalExtraColors.current.field)
                            .clickable(onClickLabel = h.name) { hospitalId = h.id }
                            .padding(vertical = 14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            hospital?.let { h ->
                ScreenBand(title = h.name.removeSuffix(" (Demo)"), tone = "ward", lead = h.address.removeSuffix(" (fictional)"))
                Spacer(modifier = Modifier.height(12.dp))
                BigButton(
                    text = tr("Directions to the gate"),
                    icon = Icons.Default.Map,
                    type = ButtonType.SECONDARY,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${h.lat},${h.lng}")))
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (h.services.isNotEmpty()) {
                    InfoCard {
                        Text(tr("SERVICES TODAY"), style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        h.services.entries.forEachIndexed { i, (key, state) ->
                            if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = LocalExtraColors.current.edge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr(SERVICE_NAMES[key] ?: key.replaceFirstChar { it.uppercase() }), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                StatusPill(label = if (state == "up") tr("Working") else tr("Not working"), type = if (state == "up") "green" else "yellow")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                InfoCard {
                    Text(tr("IS MY DOCTOR IN?"), style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (doctors.isEmpty()) {
                        Text(
                            if (refreshing) "Checking…" else tr("The doctor list is not on this phone yet. Connect once to download it."),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    doctors.forEachIndexed { i, doc ->
                        if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = LocalExtraColors.current.edge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${doc.room} · ${floorLabel(doc.floor)}" + (doc.statusNote?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                label = when (doc.status) { "in" -> tr("In"); "delayed" -> tr("Delayed"); "break" -> tr("On break"); "off" -> tr("Off today"); else -> tr("Away") },
                                type = if (doc.status == "in") "green" else "yellow"
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                checked?.let {
                    Text(tr("Checked at {time}", "time" to it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

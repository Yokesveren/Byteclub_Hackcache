package app.sevasetu.patient.ui.sms

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.model.SmsDto
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.ScreenBand
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.displayTime
import app.sevasetu.patient.ui.i18n.deptName
import app.sevasetu.patient.ui.i18n.tr
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.LocalExtraColors

/** Opens the phone's own SMS app with the hospital number and the text filled in: a real SMS, no data needed. */
fun composeSms(context: Context, number: String, body: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number.filter { it.isDigit() || it == '+' }))
            .putExtra("sms_body", body))
    }
}

/**
 * No data? Use SMS (PRD 7.2 step 3). The same commands work from any phone, even without this app: SLIP for the
 * token and place, REG to register, HELP when feeling worse, or a missed call for the menu. Texts the hospital sent to
 * the patient's number are listed below.
 */
@Composable
fun SimulatedSmsScreen(
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val app = SevaApplication.instance
    val context = LocalContext.current
    val visit by app.visits.active.collectAsState()
    val directory by app.directory.data.collectAsState()
    val hospitalId = visit?.hospital?.id ?: app.prefs.current.hospitalId
    val hospital = directory.hospitals.firstOrNull { it.id == hospitalId }
    val number = hospital?.phone
    val departments = directory.departments.filter { it.id.startsWith("$hospitalId-") }
        .sortedBy { listOf("M", "P", "A", "O").indexOf(it.code).let { i -> if (i < 0) 9 else i } }
    var texts by remember { mutableStateOf<List<SmsDto>?>(null) }

    LaunchedEffect(visit?.visitId) {
        texts = visit?.visitId?.let { id -> runCatching { app.backendGateway.getMySms(id) }.getOrNull() }
    }

    SevaScaffold(title = tr("No data? Use SMS"), onBack = onBack, onEmergencyClick = onEmergencyClick) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ScreenBand(
                title = tr("Any phone works"),
                tone = "linen",
                lead = tr("No internet needed. Text the hospital and get your token, your place in the queue and alerts by SMS.")
            )
            Spacer(Modifier.height(16.dp))

            InfoCard {
                Text(tr("HOSPITAL NUMBER"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(number ?: tr("Ask at the counter"), style = MaterialTheme.typography.headlineLarge)
                Text(hospital?.name?.removeSuffix(" (Demo)") ?: hospitalId, style = MaterialTheme.typography.bodyMedium)
                if (number != null) {
                    Spacer(Modifier.height(12.dp))
                    BigButton(
                        text = tr("Give a missed call"),
                        icon = Icons.Default.Call,
                        type = ButtonType.SECONDARY,
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) } }
                    )
                    Text(
                        tr("Ring once and hang up. The hospital texts you the menu, free."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(tr("WHAT TO TEXT"), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Command(
                code = "SLIP",
                what = tr("Your token, the doctor's room and how many are ahead of you."),
                number = number,
                body = "SLIP" + (visit?.token?.let { " $it" } ?: "")
            )
            Command(
                code = "REG 1 Asha 34",
                what = tr("Register: the department number, your first name and age."),
                number = number,
                body = "REG 1 "
            )
            Command(
                code = "HELP",
                what = tr("Feeling worse while waiting: tells the nurses at once."),
                number = number,
                body = "HELP" + (visit?.token?.let { " $it" } ?: "")
            )

            if (departments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                InfoCard {
                    Text(tr("DEPARTMENT NUMBERS FOR REG"), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    departments.forEachIndexed { i, d ->
                        Text("${i + 1}  ${deptName(d)}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            InfoCard {
                Text(tr("TEXTS TO YOUR PHONE"), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                when {
                    visit == null -> Text(tr("Register with your phone number to get SMS alerts."), style = MaterialTheme.typography.bodyMedium)
                    texts == null -> Text(tr("Texts show here when you are online."), style = MaterialTheme.typography.bodyMedium)
                    texts!!.isEmpty() -> Text(tr("No texts yet. Add a phone number when you register to get them."), style = MaterialTheme.typography.bodyMedium)
                    else -> texts!!.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = LocalExtraColors.current.edge)
                        Text(
                            (if (m.direction == "in") tr("You") else hospital?.name?.substringBefore(" ") ?: "SevaSetu") + " · " + (displayTime(m.createdAt) ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(m.body, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr("Chest pain, breathing trouble, fits or heavy bleeding: go to Emergency now. Do not wait for an SMS."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun Command(code: String, what: String, number: String?, body: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalExtraColors.current.field)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(code, style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text(what, style = MaterialTheme.typography.bodyMedium)
        }
        if (number != null) {
            Spacer(Modifier.width(8.dp))
            BigButton(
                text = tr("Text"),
                icon = Icons.Default.Sms,
                modifier = Modifier.width(130.dp),
                onClick = { composeSms(context, number, body) }
            )
        }
    }
}

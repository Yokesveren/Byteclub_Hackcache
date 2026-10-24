package app.sevasetu.patient.ui.slip

import androidx.compose.foundation.background
import app.sevasetu.patient.ui.i18n.tr
import app.sevasetu.patient.ui.i18n.trNow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.gateway.NetworkException
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.service.QueueWatcherService
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.ScreenBand
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.theme.ColorError
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch

/** "M17", "m-17" and "M-017" all mean M-017. */
private fun normaliseToken(raw: String): String {
    val t = raw.trim().uppercase().replace(" ", "")
    val m = Regex("^([A-Z])-?(\\d{1,3})$").find(t) ?: return t
    return "${m.groupValues[1]}-${m.groupValues[2].padStart(3, '0')}"
}

/**
 * I have my slip (PRD 8.7): follow a token from the counter by typing it (today only) or by scanning the QR on the
 * paper slip (`SS1:SLIP:<hospital>:<visit_id>`, works any day while the visit exists).
 */
@Composable
fun IHaveMySlipScreen(
    scanned: String?,
    onScannedConsumed: () -> Unit,
    onSlipLinked: (MyTurnDto) -> Unit,
    onOpenScanner: () -> Unit,
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val app = SevaApplication.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val directory by app.directory.data.collectAsState()

    var hospitalId by rememberSaveable { mutableStateOf(app.prefs.current.hospitalId) }
    var token by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun follow(turn: MyTurnDto) {
        app.visits.save(turn)
        app.prefs.update { it.copy(hospitalId = turn.hospital.id) }
        app.nav.newSession()
        app.nav.setDestination(turn.doctor.roomWaypointId)
        QueueWatcherService.start(context)
        onSlipLinked(turn)
    }

    fun failure(e: Exception) {
        error = if (e is NetworkException) trNow("No connection to the hospital. Try again when you have signal, or show your slip at the help desk.")
        else e.localizedMessage ?: trNow("Something went wrong. Please try again.")
    }

    LaunchedEffect(scanned) {
        val code = scanned ?: return@LaunchedEffect
        onScannedConsumed()
        val visitId = code.split(":").getOrNull(3) ?: return@LaunchedEffect
        busy = true
        error = null
        try {
            follow(app.backendGateway.getMyTurn(visitId))
        } catch (e: Exception) {
            failure(e)
        }
        busy = false
    }

    fun link() {
        val t = normaliseToken(token)
        busy = true
        error = null
        scope.launch {
            try {
                val turn = app.backendGateway.linkSlip(hospitalId, t)
                if (turn != null) follow(turn)
                else error = trNow("We could not find {token} at this hospital today. Check the number and hospital, or ask at the help desk.", "token" to t)
            } catch (e: Exception) {
                failure(e)
            }
            busy = false
        }
    }

    SevaScaffold(
        title = tr("I have my slip"),
        onBack = onBack,
        onListen = { app.speaker.speak("Type the token number on your slip, or scan its QR code.", "अपनी पर्ची का टोकन नंबर लिखें या उसका QR कोड स्कैन करें।") },
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ScreenBand(
                title = tr("Follow your token"),
                tone = "linen",
                lead = tr("Got a paper slip at the counter? Follow your place in the queue here.")
            )
            Spacer(modifier = Modifier.height(20.dp))

            BigButton(text = tr("Scan the QR on my slip"), icon = Icons.Default.QrCodeScanner, onClick = onOpenScanner, enabled = !busy)

            Spacer(modifier = Modifier.height(24.dp))
            Text(tr("OR TYPE THE TOKEN"), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                directory.hospitals.forEach { h ->
                    val on = h.id == hospitalId
                    Text(
                        text = h.name.substringBefore(" ").uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) Color.White else Ink,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) LocalExtraColors.current.frame else LocalExtraColors.current.field)
                            .clickable(onClickLabel = h.name) { hospitalId = h.id }
                            .padding(vertical = 14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it.take(8); error = null },
                label = { Text(tr("Token, for example M-017")) },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (token.isNotBlank()) link() }),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            BigButton(
                text = if (busy) "Looking…" else tr("Follow this token"),
                type = ButtonType.SECONDARY,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                enabled = token.isNotBlank() && !busy,
                onClick = { link() }
            )
            error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = ColorError)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                tr("Typed tokens work on the day of the visit. The QR code works any day."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

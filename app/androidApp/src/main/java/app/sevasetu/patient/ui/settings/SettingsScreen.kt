package app.sevasetu.patient.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.prefs.LANGUAGES
import app.sevasetu.patient.data.prefs.Settings
import app.sevasetu.patient.service.QueueWatcherService
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.theme.ColorEmergency
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch

/** Settings (PRD 8.8). Everything here is saved on the phone and applies at once. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenReadiness: () -> Unit,
    onOpenStickerSheet: () -> Unit,
    onOpenDemoPanel: () -> Unit,
) {
    val app = SevaApplication.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by app.prefs.settings.collectAsState()
    val visit by app.visits.active.collectAsState()
    fun set(change: (Settings) -> Settings) = app.prefs.update(change)

    SevaScaffold(title = "Settings", onBack = onBack, onEmergencyClick = onEmergencyClick) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Section("Reading") {
                LinkRow("Language", LANGUAGES.firstOrNull { it.code == s.language }?.let { "${it.native} · ${it.english}" } ?: s.language, onOpenLanguage)
                Divider()
                Text("Text size", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.0f to "A", 1.25f to "A+", 1.5f to "A++", 2.0f to "A+++").forEach { (scale, label) ->
                        val on = s.textScale == scale
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) Color.White else Ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) LocalExtraColors.current.frame else LocalExtraColors.current.field)
                                .clickable(onClickLabel = "Text size ${(scale * 100).toInt()} percent") { set { it.copy(textScale = scale) } }
                                .padding(vertical = 14.dp)
                        )
                    }
                }
                Divider()
                ToggleRow("High contrast", "Black on white with solid edges.", s.highContrast) { on -> set { it.copy(highContrast = on) } }
            }

            Section("Finding your way") {
                ToggleRow("Avoid stairs", "Routes use the lift and ramps only.", s.avoidStairs) { on -> set { it.copy(avoidStairs = on) } }
                Divider()
                ToggleRow(
                    "Share the stickers I scan",
                    "Anonymous. Helps the hospital see crowded corridors.",
                    s.movementConsent
                ) { on -> set { it.copy(movementConsent = on) } }
            }

            Section("Alerts") {
                ToggleRow("Sound", "Speak and ring when it is nearly your turn.", s.alertSound) { on -> set { it.copy(alertSound = on) } }
                Divider()
                ToggleRow("Vibrate", null, s.alertVibration) { on -> set { it.copy(alertVibration = on) } }
                Divider()
                ToggleRow("Flash the screen when called", "For people who are hard of hearing.", s.flashOnCall) { on -> set { it.copy(flashOnCall = on) } }
            }

            Section("This phone") {
                LinkRow("Ready for no signal?", "What works offline on this phone", onOpenReadiness)
                Divider()
                LinkRow("Wall sticker sheet", "Print the hospital's QR stickers", onOpenStickerSheet)
                if (!app.isLive) {
                    Divider()
                    LinkRow("Simulator controls", "Demo only: move the queue on this phone", onOpenDemoPanel)
                }
                if (visit != null) {
                    Divider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    app.visits.clear()
                                    app.nav.newSession()
                                    QueueWatcherService.stop(context)
                                    onBack()
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Column {
                            Text("Forget this visit", style = MaterialTheme.typography.titleMedium, color = ColorEmergency)
                            Text(
                                "Removes token ${visit?.token} from this phone. It does not cancel it at the hospital.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Text(
                if (app.isLive) "Connected to the hospital server." else "Simulator mode: no hospital server is set in this build.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
    InfoCard { content() }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun Divider() = HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = LocalExtraColors.current.edge)

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

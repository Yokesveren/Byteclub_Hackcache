package app.sevasetu.patient.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.SevaScaffold

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit,
    onOpenReadiness: () -> Unit,
    onOpenDemoPanel: () -> Unit,
    onTextScaleChanged: (Float) -> Unit,
    onHighContrastChanged: (Boolean) -> Unit
) {
    var textScale by remember { mutableFloatStateOf(1.0f) }
    var isHighContrast by remember { mutableStateOf(false) }
    var avoidStairs by remember { mutableStateOf(true) }
    var alertVibration by remember { mutableStateOf(true) }
    var alertSound by remember { mutableStateOf(true) }
    var flashScreenForHearing by remember { mutableStateOf(false) }

    SevaScaffold(
        title = "Settings",
        onBack = onBack,
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "App Settings & Accessibility", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Reading & Hearing (Text Size & Contrast)
            InfoCard {
                Text(text = "Reading & Accessibility", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Text Size (Scale)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(1.0f to "100%", 1.25f to "125%", 1.50f to "150%", 2.00f to "200%").forEach { (scale, label) ->
                        OutlinedButton(
                            onClick = {
                                textScale = scale
                                onTextScaleChanged(scale)
                            }
                        ) {
                            Text(text = label, fontWeight = if (textScale == scale) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "High Contrast Mode", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isHighContrast,
                        onCheckedChange = {
                            isHighContrast = it
                            onHighContrastChanged(it)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Options
            InfoCard {
                Text(text = "Navigation Preferences", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Avoid Stairs (Use Lift Only)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Recommended for elderly, walking difficulty, or wheelchair users",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = avoidStairs,
                        onCheckedChange = { avoidStairs = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notifications & Alerts
            InfoCard {
                Text(text = "Queue Alerts & Notifications", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Sound Alerts", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = alertSound, onCheckedChange = { alertSound = it })
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Vibration Alerts", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = alertVibration, onCheckedChange = { alertVibration = it })
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Flash Screen on Call", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(text = "Flashes screen for patients with hearing needs", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = flashScreenForHearing, onCheckedChange = { flashScreenForHearing = it })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Links to Readiness & Simulation
            InfoCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenReadiness() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Offline Readiness Checklist",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDemoPanel() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Simulation & Demo Operator Panel",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

package app.sevasetu.patient.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.ScreenBand
import app.sevasetu.patient.ui.components.SevaScaffold
import kotlinx.coroutines.launch

@Composable
fun DemoPanelScreen(
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit,
    onOpenStickerSheet: () -> Unit
) {
    val app = SevaApplication.instance
    val fakeBackend = app.fakeBackend
    val scope = rememberCoroutineScope()

    var simulateNoInternet by remember { mutableStateOf(fakeBackend.simulateNoInternet) }
    var autopilotEnabled by remember { mutableStateOf(fakeBackend.isAutopilotEnabled) }
    var clockSpeed by remember { mutableIntStateOf(fakeBackend.clockSpeed) }
    var statusMessage by remember { mutableStateOf("") }

    SevaScaffold(
        title = "Simulation & Demo Panel",
        onBack = onBack,
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (app.isLive) {
                // Live: the queue lives on the shared backend, so staff actions happen on the website.
                ScreenBand(
                    title = "Live hospital",
                    tone = "teal",
                    lead = "This phone is connected to the hospital server. Move the queue, call patients and see urgent " +
                        "flags from the website: the Facility, Doctor desk and Demo pages. Changes appear here within seconds."
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "The simulator controls below only affect the on-phone simulator, not the live queue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ScreenBand(
                    title = "Simulator",
                    tone = "night",
                    lead = "Controls here act as hospital staff or system events, for testing offline mode, queue movement and alerts without a server."
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (statusMessage.isNotEmpty()) {
                Text(
                    text = statusMessage,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Environment Switches
            InfoCard {
                Text(text = "Environment & Clock", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Simulate No Internet Connection",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = simulateNoInternet,
                        onCheckedChange = {
                            simulateNoInternet = it
                            fakeBackend.simulateNoInternet = it
                            statusMessage = if (it) "Offline mode simulated" else "Online mode restored"
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Autopilot (Call next every 30s)",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = autopilotEnabled,
                        onCheckedChange = {
                            autopilotEnabled = it
                            fakeBackend.isAutopilotEnabled = it
                            statusMessage = if (it) "Autopilot enabled" else "Autopilot disabled"
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Clock Speed", style = MaterialTheme.typography.bodyLarge)
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = { clockSpeed = 1; fakeBackend.clockSpeed = 1 },
                        modifier = Modifier.weight(1f)
                    ) { Text("1x") }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { clockSpeed = 30; fakeBackend.clockSpeed = 30 },
                        modifier = Modifier.weight(1f)
                    ) { Text("30x") }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { clockSpeed = 120; fakeBackend.clockSpeed = 120 },
                        modifier = Modifier.weight(1f)
                    ) { Text("120x") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Demo Actions
            InfoCard {
                Text(text = "Doctor & Queue Controls", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))

                BigButton(
                    text = "Call Next Patient (Dr. Aamir Lone)",
                    onClick = {
                        scope.launch {
                            fakeBackend.callNext("H01-D01")
                            statusMessage = "Called next patient for Dr. Aamir Lone"
                        }
                    },
                    type = ButtonType.PRIMARY
                )

                Spacer(modifier = Modifier.height(10.dp))

                BigButton(
                    text = "Load Demo Patient Kamla (Token M-017)",
                    onClick = {
                        scope.launch {
                            fakeBackend.loadDemoPatientKamla()
                            statusMessage = "Loaded demo patient Kamla (Token M-017)"
                        }
                    },
                    type = ButtonType.SECONDARY
                )

                Spacer(modifier = Modifier.height(10.dp))

                BigButton(
                    text = "Add 5 Fictional Patients",
                    onClick = {
                        scope.launch {
                            fakeBackend.addPatients(5)
                            statusMessage = "Added 5 patients to hospital queues"
                        }
                    },
                    type = ButtonType.SECONDARY
                )

                Spacer(modifier = Modifier.height(10.dp))

                BigButton(
                    text = "Mark Urgent Flag Seen by Staff",
                    onClick = {
                        scope.launch {
                            fakeBackend.markFlagSeen("kamla-demo-visit")
                            statusMessage = "Marked urgent flag as SEEN by staff"
                        }
                    },
                    type = ButtonType.SECONDARY
                )

                Spacer(modifier = Modifier.height(10.dp))

                BigButton(
                    text = "View Wall Sticker QR Sheet",
                    onClick = onOpenStickerSheet,
                    type = ButtonType.SECONDARY
                )
            }
        }
    }
}

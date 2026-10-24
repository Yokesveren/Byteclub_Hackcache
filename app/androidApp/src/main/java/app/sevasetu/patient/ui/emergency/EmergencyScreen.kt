package app.sevasetu.patient.ui.emergency

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.gateway.NetworkException
import app.sevasetu.patient.data.model.UrgentFlagDto
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.ListenButton
import app.sevasetu.patient.ui.theme.ColorEmergency
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

@Composable
fun EmergencyScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = SevaApplication.instance

    var urgentFlagState by remember { mutableStateOf<UrgentFlagDto?>(null) }
    var flagStatusText by remember { mutableStateOf("Press below if you need immediate hospital staff help") }

    fun triggerVibration() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun dialNumber(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        context.startActivity(intent)
    }

    // Honest three-state lifecycle (S5): not yet received (offline) -> sent to staff -> staff have seen it.
    LaunchedEffect(urgentFlagState?.id) {
        urgentFlagState?.id?.let { flagId ->
            while (true) {
                delay(3000)
                val updated = try {
                    app.backendGateway.getUrgentFlag(flagId)
                } catch (e: NetworkException) {
                    // Still offline: the outbox resends it first (priority 0) as soon as the phone is back online.
                    runCatching { app.outboxManager.flushOutbox() }
                    null
                } catch (e: Exception) {
                    null
                }
                if (updated != null) {
                    urgentFlagState = updated
                    if (updated.seenAt != null) {
                        flagStatusText = "Staff have seen your request. Help is coming."
                        triggerVibration()
                        break
                    } else if (updated.receivedAt != null) {
                        flagStatusText = "Sent to staff. Waiting for a staff member to see it."
                    }
                }
            }
        }
    }

    // A full red field (the website's emergency colour), white capitals, white panels on top.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ColorEmergency
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "◆ EMERGENCY / आपातकालीन",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                ListenButton(onClick = {})
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Emergency Banner (S2 Rule)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ColorEmergency,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Go to Emergency now.",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                            color = ColorEmergency,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You do NOT need a token or appointment. Please go straight to Casualty.",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Steps to Emergency Room
            InfoCard {
                Text(
                    text = "Directions to Casualty (Ground Floor)",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = ColorEmergency)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "1. From Main Gate: Walk straight 20m into Entrance Hall",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "2. Turn left at Red Doors marked 'Emergency (Casualty)'",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons: Call 112 & 108
            Row(modifier = Modifier.fillMaxWidth()) {
                BigButton(
                    text = "Call 112",
                    onClick = { dialNumber("112") },
                    type = ButtonType.SECONDARY,
                    icon = Icons.Default.Call,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                BigButton(
                    text = "Call 108",
                    onClick = { dialNumber("108") },
                    type = ButtonType.SECONDARY,
                    icon = Icons.Default.Call,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // "I Need Urgent Help" Button & Honest Lifecycle Status (S5 Rule)
            InfoCard {
                Text(
                    text = "Hospital Staff Assistance",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = flagStatusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                BigButton(
                    text = "I Need Urgent Help",
                    onClick = {
                        if (urgentFlagState != null) return@BigButton
                        scope.launch {
                            val visit = app.visits.active.value
                            val flag = UrgentFlagDto(
                                id = UUID.randomUUID().toString(),
                                visitId = visit?.visitId,
                                hospitalId = visit?.hospital?.id ?: "H01",
                                // Without a visit the server needs an anonymous session id (contract: raise_urgent_flag).
                                sessionId = if (visit == null) UUID.randomUUID().toString() else null,
                                source = "patient_app",
                                message = "Patient pressed urgent help on phone",
                                waypointId = "SS:H01:F0:W001",
                                raisedAt = Instant.now().toString()
                            )
                            urgentFlagState = flag
                            flagStatusText = try {
                                urgentFlagState = app.backendGateway.raiseUrgentFlag(flag)
                                "Sent to staff. Waiting for a staff member to see it."
                            } catch (e: NetworkException) {
                                app.outboxManager.enqueueUrgentFlag(flag)
                                "Not yet received by staff. Please tell a staff member."
                            } catch (e: Exception) {
                                app.outboxManager.enqueueUrgentFlag(flag)
                                "Not yet received by staff. Please tell a staff member."
                            }
                        }
                    },
                    type = ButtonType.DANGER
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            BigButton(
                text = "Back to Home",
                onClick = onBack,
                type = ButtonType.SECONDARY
            )
        }
    }
}

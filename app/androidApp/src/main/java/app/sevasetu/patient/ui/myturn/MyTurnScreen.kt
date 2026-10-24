package app.sevasetu.patient.ui.myturn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.EmptyState
import app.sevasetu.patient.ui.components.displayTime
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.LastUpdatedText
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StatusPill
import app.sevasetu.patient.ui.theme.ColorEmergency
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import app.sevasetu.patient.ui.theme.ColorPriorityContent
import app.sevasetu.patient.ui.theme.ColorPriorityContainer

fun translateReasonCode(code: String): String {
    return when (code.lowercase()) {
        "priority_patient" -> "A priority patient was moved ahead based on clinical urgency."
        "clinical_review" -> "Queue position updated after clinical review by staff."
        "doctor_delayed" -> "Doctor is delayed due to an emergency procedure."
        "doctor_unavailable" -> "Doctor is currently away or on a break."
        "wrong_department" -> "Transferred to correct department by staff."
        "patient_request" -> "Updated per patient request."
        "no_show" -> "Position updated after missed call."
        "token_confirmed" -> "Offline token confirmed by hospital server."
        else -> "Queue position updated by hospital staff."
    }
}

@Composable
fun MyTurnScreen(
    visitId: String?,
    onNavigateToCalled: (MyTurnDto) -> Unit,
    onNavigateToRoom: (destinationWaypointId: String) -> Unit,
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val app = SevaApplication.instance
    // Start from the ticket saved on the phone, so My Turn opens instantly and still reads offline.
    var myTurnState by remember(visitId) { mutableStateOf(app.visits.active.value?.takeIf { it.visitId == visitId }) }
    var isOffline by remember { mutableStateOf(false) }

    if (visitId == null) {
        SevaScaffold(title = "My Turn", onBack = onBack, onEmergencyClick = onEmergencyClick) {
            EmptyState("No ticket yet. Register for OPD, or use \"I have my slip\" to follow your token.")
        }
        return
    }

    LaunchedEffect(visitId) {
        // Realtime (contract section 5) wakes the loop at once when staff move the queue; polling every
        // 5 seconds remains the fallback when live updates are unavailable.
        val wake = Channel<Unit>(Channel.CONFLATED)
        launch {
            app.backendGateway.observeVisit(visitId, myTurnState?.hospital?.id)
                .retryWhen { _, attempt -> delay(minOf(30_000L, 2_000L shl attempt.toInt().coerceAtMost(4))); true }
                .catch { }
                .collect { wake.trySend(Unit) }
        }
        while (true) {
            try {
                val updated = app.backendGateway.getMyTurn(visitId)
                myTurnState = updated
                isOffline = false
                app.visits.save(updated, confirmed = updated.offlineToken == null || updated.token != updated.offlineToken)

                if (updated.status == "called") {
                    onNavigateToCalled(updated)
                    break
                }
                if (updated.status == "emergency") {
                    // Staff sent the patient to Emergency (contract enum): show the Emergency screen.
                    onEmergencyClick()
                    break
                }
            } catch (e: Exception) {
                isOffline = true
            }
            withTimeoutOrNull(5000) { wake.receive() }
        }
    }

    val myTurn = myTurnState

    SevaScaffold(
        title = "My Turn",
        onBack = onBack,
        onEmergencyClick = onEmergencyClick,
        isOffline = isOffline,
        lastUpdatedTime = displayTime(myTurn?.lastUpdated)
    ) {
        if (myTurn == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Loading queue status...", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Hero Queue Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Token ${myTurn.token}",
                                style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            StatusPill(label = myTurn.bandLabel, type = myTurn.band)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        val estimate = myTurn.estimate
                        if (estimate != null) {
                            Text(
                                text = "PEOPLE AHEAD",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${estimate.peopleAhead}",
                                style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (estimate.paused) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = ColorPriorityContainer)
                                ) {
                                    Text(
                                        text = "Doctor is currently on a break or away. Queue estimate is paused.",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyLarge.copy(color = ColorPriorityContent)
                                    )
                                }
                            } else if (estimate.windowStart != null && estimate.windowEnd != null) {
                                Text(
                                    text = "Likely between ${displayTime(estimate.windowStart)} and ${displayTime(estimate.windowEnd)}",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Doctor Info & Status
                InfoCard {
                    Text(text = "Doctor & Location", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Doctor: ${myTurn.doctor.name}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(text = "Room: ${myTurn.doctor.room} (Floor ${myTurn.doctor.floor})", style = MaterialTheme.typography.bodyLarge)
                    Text(text = "Now serving: ${myTurn.nowServing ?: "None"}", style = MaterialTheme.typography.bodyLarge)
                }

                // Reason Chip (F25)
                myTurn.lastReason?.let { reason ->
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Why did my place change?",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = translateReasonCode(reason.reasonCode),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Urgent Help Action (S4 Rule)
                BigButton(
                    text = "Feeling worse? Press Urgent help",
                    onClick = onEmergencyClick,
                    type = ButtonType.DANGER,
                    icon = Icons.Default.Warning
                )

                Spacer(modifier = Modifier.height(12.dp))

                BigButton(
                    text = "Take me to my room",
                    onClick = { onNavigateToRoom(myTurn.doctor.roomWaypointId) },
                    type = ButtonType.SECONDARY,
                    icon = Icons.Default.Navigation
                )
            }
        }
    }
}

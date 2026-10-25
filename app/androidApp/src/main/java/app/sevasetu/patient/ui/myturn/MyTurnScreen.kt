package app.sevasetu.patient.ui.myturn

import app.sevasetu.patient.ui.i18n.deptName
import app.sevasetu.patient.ui.i18n.floorLabel
import androidx.compose.foundation.background
import app.sevasetu.patient.ui.i18n.tr
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.gateway.BackendException
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.service.QueueWatcherService
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.CardShape
import app.sevasetu.patient.ui.components.EmptyState
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.LoadingState
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StatusPill
import app.sevasetu.patient.ui.components.displayTime
import app.sevasetu.patient.ui.theme.ColorPriorityContainer
import app.sevasetu.patient.ui.theme.ColorPriorityContent
import app.sevasetu.patient.ui.theme.Frame
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.LocalExtraColors
import app.sevasetu.patient.ui.theme.Mint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** S10: every change of place carries a reason the patient can read (contract reason codes). */
fun translateReasonCode(code: String, fallback: String? = null): String = when (code.lowercase()) {
    "priority_patient", "priority_join" -> "A patient who needs care sooner joined ahead of you."
    "clinical_review", "band_confirmed", "band_changed" -> "A nurse reviewed the queue."
    "doctor_delayed" -> "The doctor is delayed by an emergency."
    "doctor_unavailable", "doctor_break", "doctor_away" -> "The doctor is away for a short time. Your place is kept."
    "doctor_back" -> "The doctor is back."
    "wrong_department", "department_changed", "moved" -> "Staff moved you to the right doctor."
    "patient_request" -> "Changed at your request."
    "no_show" -> "You missed your call. Staff put you back in the queue."
    "token_confirmed" -> "The hospital confirmed your offline token."
    "rebalanced", "doctor_changed" -> "You were moved to a doctor with a shorter queue."
    else -> fallback ?: "Hospital staff updated the queue."
}

@Composable
fun MyTurnScreen(
    visitId: String?,
    onNavigateToCalled: (MyTurnDto) -> Unit,
    onNavigateToRoom: (destinationWaypointId: String) -> Unit,
    onShowSlip: () -> Unit,
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val app = SevaApplication.instance
    val context = LocalContext.current
    // Start from the ticket saved on the phone, so My Turn opens instantly and still reads offline.
    var turn by remember(visitId) { mutableStateOf(app.visits.active.value?.takeIf { it.visitId == visitId }) }
    var offline by remember { mutableStateOf(false) }

    if (visitId == null) {
        SevaScaffold(title = tr("My turn"), onBack = onBack, onEmergencyClick = onEmergencyClick) {
            EmptyState(tr("No token yet.\n\nRegister for OPD, or use \"I have my slip\" to follow a token from the counter."))
        }
        return
    }

    LaunchedEffect(visitId) {
        QueueWatcherService.start(context)
        // Realtime (contract section 5) wakes the loop at once when staff move the queue; checking every
        // 10 seconds remains the fallback when live updates are unavailable.
        val wake = Channel<Unit>(Channel.CONFLATED)
        launch {
            app.backendGateway.observeVisit(visitId, turn?.hospital?.id)
                .retryWhen { _, attempt -> delay(minOf(30_000L, 2_000L shl attempt.toInt().coerceAtMost(4))); true }
                .catch { }
                .collect { wake.trySend(Unit) }
        }
        while (true) {
            try {
                val updated = app.backendGateway.getMyTurn(visitId)
                turn = updated
                offline = false
                app.visits.save(updated, confirmed = updated.offlineToken == null || updated.token != updated.offlineToken)
                if (updated.status == "called") {
                    onNavigateToCalled(updated); break
                }
                if (updated.status == "emergency") {
                    onEmergencyClick(); break
                }
            } catch (e: BackendException) {
                // Not found at the hospital (the demo was reset): drop the stale ticket instead of retrying forever.
                if (e.status == 404 && app.visits.isConfirmed(visitId)) {
                    app.visits.clear(); QueueWatcherService.stop(context); onBack(); break
                }
            } catch (e: Exception) {
                offline = true
            }
            withTimeoutOrNull(10_000) { wake.receive() }
        }
    }

    val t = turn
    SevaScaffold(
        title = tr("My turn"),
        onBack = onBack,
        onListen = t?.let { v ->
            {
                val ahead = v.estimate?.peopleAhead ?: 0
                app.speaker.speak(
                    "Token ${v.token}. $ahead people ahead of you. " +
                        (v.estimate?.windowStart?.let { "Likely between ${displayTime(it)} and ${displayTime(v.estimate.windowEnd)}. " } ?: "") +
                        "${v.doctor.name}, ${v.doctor.room}.",
                    "टोकन ${v.token}। आपसे पहले $ahead लोग हैं। ${v.doctor.room}।"
                )
            }
        },
        onEmergencyClick = onEmergencyClick,
        isOffline = offline,
        lastUpdatedTime = displayTime(t?.lastUpdated)
    ) {
        if (t == null) {
            LoadingState(tr("Finding your place in the queue…"))
            return@SevaScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val est = t.estimate
            val confirmed = t.offlineToken == null || t.token != t.offlineToken

            // The big number: how many are ahead
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(Frame)
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("TOKEN"), style = MaterialTheme.typography.labelLarge, color = Mint)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(t.token, style = MaterialTheme.typography.headlineLarge, color = Color.White, modifier = Modifier.weight(1f))
                    StatusPill(label = t.bandLabel, type = t.band, provisional = t.bandProvisional)
                }
                if (!confirmed) {
                    Text(tr("Waiting to be confirmed by the hospital"), style = MaterialTheme.typography.labelMedium, color = Color(0xFFF2B01E))
                }
                Spacer(modifier = Modifier.height(18.dp))
                when {
                    est == null -> Text(
                        tr("Your place will show once the hospital confirms your token."),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    else -> {
                        Text(
                            if (est.peopleAhead == 0) tr("YOU ARE NEXT") else tr("PEOPLE AHEAD OF YOU"),
                            style = MaterialTheme.typography.labelLarge,
                            color = Mint
                        )
                        if (est.peopleAhead > 0) {
                            Text("${est.peopleAhead}", style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.6f), color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (!est.paused && est.windowStart != null && est.windowEnd != null) {
                            Text(
                                tr("Likely between {from} and {to}", "from" to displayTime(est.windowStart), "to" to displayTime(est.windowEnd)),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        t.nowServing?.let {
                            Text(tr("Now serving {token}", "token" to it), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9ECE6))
                        }
                    }
                }
            }

            if (est?.paused == true) {
                Spacer(modifier = Modifier.height(12.dp))
                Notice(
                    icon = Icons.Default.PauseCircle,
                    title = tr("THE DOCTOR IS AWAY FOR NOW"),
                    text = (t.doctor.statusNote?.let { "$it. " } ?: "") + tr("Your place is kept. The time will show again when the doctor is back.")
                )
            }

            t.lastReason?.let { reason ->
                Spacer(modifier = Modifier.height(12.dp))
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tr("WHY DID MY PLACE CHANGE?"), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(tr(translateReasonCode(reason.reasonCode, reason.message)), style = MaterialTheme.typography.bodyLarge)
                    displayTime(reason.at)?.let {
                        Text(tr("At {time}", "time" to it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Where to go
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(LocalExtraColors.current.field)
                    .padding(20.dp)
            ) {
                Text(tr("YOUR DOCTOR"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(t.doctor.name, style = MaterialTheme.typography.titleLarge, color = Ink)
                Text(
                    "${t.doctor.room} · ${floorLabel(t.doctor.floor)} · ${deptName(t.department)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink
                )
                if (t.doctor.stairFree.not()) {
                    Text(tr("Stairs only to this room. Ask staff for help if you need it."), style = MaterialTheme.typography.bodyMedium, color = ColorPriorityContent)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                t.doctor.roomWaypointId?.let { room ->
                    BigButton(
                        text = tr("Take me there"),
                        onClick = { onNavigateToRoom(room) },
                        icon = Icons.Default.Navigation,
                        modifier = Modifier.weight(1f)
                    )
                }
                BigButton(
                    text = tr("Show slip"),
                    onClick = onShowSlip,
                    type = ButtonType.SECONDARY,
                    icon = Icons.Default.QrCode2,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // S4: on every queue screen, never "you can wait"
            BigButton(
                text = tr("Feeling worse? Press Urgent help"),
                onClick = onEmergencyClick,
                type = ButtonType.DANGER,
                icon = Icons.Default.Warning
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                tr("We will alert you when 3 people are ahead and when it is your turn, even with the phone in your pocket."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ColorPriorityContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = ColorPriorityContent, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = ColorPriorityContent)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = ColorPriorityContent)
        }
    }
}

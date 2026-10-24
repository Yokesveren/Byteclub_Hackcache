package app.sevasetu.patient.ui.emergency

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.gateway.NetworkException
import app.sevasetu.patient.data.model.UrgentFlagDto
import app.sevasetu.patient.data.navigation.Directions
import app.sevasetu.patient.data.navigation.GraphRouter
import app.sevasetu.patient.data.navigation.RouteOptions
import app.sevasetu.patient.data.navigation.displayName
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.CardShape
import app.sevasetu.patient.ui.components.ListenButton
import app.sevasetu.patient.ui.components.displayTime
import app.sevasetu.patient.ui.theme.ColorEmergency
import app.sevasetu.patient.ui.theme.Frame
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.Mint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import java.time.Instant
import java.util.UUID

/** The honest delivery state of an urgent request (S5). */
private enum class FlagState { NONE, QUEUED, SENT, SEEN }

/**
 * Emergency (PRD 8.4, S1, S2): works with no network. The route to Casualty comes from the map on the phone;
 * 112 and 108 dial directly. "I need urgent help" tells staff on the dashboard and says truthfully whether it
 * has reached them. [autoRaise] is set when the symptom check found a warning sign.
 */
@Composable
fun EmergencyScreen(
    onBack: () -> Unit,
    autoRaise: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = SevaApplication.instance

    var flag by remember { mutableStateOf<UrgentFlagDto?>(null) }
    var state by remember { mutableStateOf(FlagState.NONE) }
    var raisedOnce by rememberSaveable { mutableStateOf(false) }

    val visit = app.visits.active.value
    val hospitalId = visit?.hospital?.id ?: app.prefs.current.hospitalId
    val graph = remember(hospitalId) { app.nav.graph(hospitalId) }
    val position = app.nav.position.value
    val steps = remember(graph, position) {
        graph?.let { g ->
            GraphRouter.route(g, position ?: g.entrance, g.emergency, RouteOptions(emergency = true))
                ?.let { Directions.steps(g, it) }
        }.orEmpty()
    }
    val emergencyName = graph?.nodes?.firstOrNull { it.id == graph.emergency }?.displayName() ?: "Emergency (Casualty)"

    fun vibrate() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
    }

    fun dial(number: String) = context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))

    fun raise(source: String) {
        if (flag != null) return
        val f = UrgentFlagDto(
            id = UUID.randomUUID().toString(),
            visitId = visit?.visitId,
            hospitalId = hospitalId,
            // Without a visit the server needs an anonymous session id (contract: raise_urgent_flag).
            sessionId = if (visit == null) UUID.randomUUID().toString() else null,
            source = source,
            message = if (source == "symptom_check") "Warning sign in the symptom check" else "Patient pressed urgent help",
            waypointId = position,
            raisedAt = Instant.now().toString()
        )
        flag = f
        raisedOnce = true
        state = FlagState.QUEUED
        scope.launch {
            state = try {
                flag = app.backendGateway.raiseUrgentFlag(f)
                if (flag?.seenAt != null) FlagState.SEEN else FlagState.SENT
            } catch (e: Exception) {
                // Offline or refused: queued first in the outbox, sent the moment the phone is back online.
                app.outboxManager.enqueueUrgentFlag(f)
                FlagState.QUEUED
            }
        }
    }

    LaunchedEffect(Unit) {
        if (autoRaise && !raisedOnce) raise("symptom_check")
    }

    // Seen by staff: live on the flag's channel, checked every 3 seconds as a fallback (S5).
    LaunchedEffect(flag?.id) {
        val id = flag?.id ?: return@LaunchedEffect
        val wake = Channel<Unit>(Channel.CONFLATED)
        launch {
            val topic = if (flag?.visitId != null) "visit:${flag?.visitId}" else "flag:$id"
            (app.backendGateway as? app.sevasetu.patient.data.gateway.RemoteBackend)
                ?.broadcasts(listOf(topic))?.catch { }?.collect { wake.trySend(Unit) }
        }
        while (state != FlagState.SEEN) {
            val updated = try {
                app.backendGateway.getUrgentFlag(id)
            } catch (e: NetworkException) {
                runCatching { app.outboxManager.flushOutbox() }
                null
            } catch (e: Exception) {
                null
            }
            if (updated != null) {
                flag = updated
                if (updated.seenAt != null) {
                    state = FlagState.SEEN
                    vibrate()
                    app.speaker.speak("Staff have seen your request. Help is coming.", "स्टाफ ने आपका अनुरोध देख लिया है। मदद आ रही है।")
                    break
                } else if (updated.receivedAt != null) state = FlagState.SENT
            }
            withTimeoutOrNull(3_000) { wake.receive() }
        }
    }

    fun speakAll() {
        val route = steps.joinToString(" ") { it.instruction }
        app.speaker.speak(
            "Go to Emergency now. You do not need a token. $route",
            "अभी इमरजेंसी जाइए। आपको टोकन की ज़रूरत नहीं है।",
            "ابھی ایمرجنسی جائیں۔ آپ کو ٹوکن کی ضرورت نہیں ہے۔"
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(ColorEmergency)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FFFFFF))
                        .clickable(onClickLabel = "Go back", onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                ListenButton(onClick = { speakAll() })
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("◆ EMERGENCY", style = MaterialTheme.typography.labelLarge, color = Color.White)
            Text("GO TO EMERGENCY NOW.", style = MaterialTheme.typography.displayMedium, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "You do not need a token. Go straight to $emergencyName. It is open day and night.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text("अभी इमरजेंसी जाइए। टोकन की ज़रूरत नहीं है।", style = MaterialTheme.typography.bodyLarge, color = Color.White)

            Spacer(modifier = Modifier.height(20.dp))

            // Call buttons: large, white on red
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CallTile("112", "Emergency", Modifier.weight(1f)) { dial("112") }
                CallTile("108", "Ambulance", Modifier.weight(1f)) { dial("108") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Urgent help, with its honest state
            Column(
                modifier = Modifier.fillMaxWidth().clip(CardShape).background(Color.White).padding(20.dp)
            ) {
                Text("TELL THE STAFF", style = MaterialTheme.typography.titleLarge, color = Ink)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Already in the hospital and feeling worse? This alerts the nurses' screen at once.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                when (state) {
                    FlagState.NONE -> BigButton(
                        text = "I need urgent help",
                        type = ButtonType.DANGER,
                        icon = Icons.Default.NotificationsActive,
                        onClick = { raise("patient_app") }
                    )
                    FlagState.QUEUED -> FlagStatus(
                        Icons.Default.WifiOff, Color(0xFFFFF1C2), Color(0xFF5C3D00),
                        "NOT YET RECEIVED BY STAFF",
                        "No connection yet. It will be sent the moment there is signal. Please tell a staff member now."
                    )
                    FlagState.SENT -> FlagStatus(
                        Icons.Default.Schedule, Color(0xFFDCECF7), Ink,
                        "SENT TO STAFF",
                        "Waiting for a nurse to see it" + (flag?.receivedAt?.let { " · sent at ${displayTime(it)}" } ?: "") + "."
                    )
                    FlagState.SEEN -> FlagStatus(
                        Icons.Default.CheckCircle, Mint, Ink,
                        "STAFF HAVE SEEN YOUR REQUEST",
                        "Help is coming. Stay where you are if you can."
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // The way to Casualty, from the map on the phone (works offline)
            if (steps.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().clip(CardShape).background(Color.White).padding(20.dp)) {
                    Text("THE WAY TO EMERGENCY", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text(
                        if (position != null) "From where you last scanned." else "From the main gate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    steps.forEachIndexed { i, s ->
                        Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier.size(28.dp).clip(CircleShape).background(ColorEmergency),
                                contentAlignment = Alignment.Center
                            ) { Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium) }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(s.instruction, style = MaterialTheme.typography.titleMedium, color = Ink)
                                s.landmark?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            BigButton(text = "Back", type = ButtonType.SECONDARY, onClick = onBack)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CallTile(number: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(Color.White)
            .clickable(onClickLabel = "Call $number", onClick = onClick)
            .padding(18.dp)
    ) {
        Icon(Icons.Default.Call, contentDescription = null, tint = ColorEmergency, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text("CALL $number", style = MaterialTheme.typography.headlineMedium, color = ColorEmergency)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Frame)
    }
}

@Composable
private fun FlagStatus(icon: ImageVector, bg: Color, ink: Color, title: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = ink)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = ink)
        }
    }
}

package app.sevasetu.patient.ui.myturn

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.CardShape
import app.sevasetu.patient.ui.components.ListenButton
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.Mint

/**
 * Called (PRD 8.6): the waiting-room board's mint, the room in the largest type the screen holds, vibration and a
 * spoken call. With "Flash screen on call" on (hearing needs) the screen pulses until the patient looks.
 */
@Composable
fun CalledScreen(
    myTurn: MyTurnDto,
    onNavigateToRoom: (destinationWaypointId: String) -> Unit,
    onHome: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val context = LocalContext.current
    val app = SevaApplication.instance
    val settings = app.prefs.current

    fun announce() = app.speaker.speak(
        "It is your turn. Token ${myTurn.token}. Please go to ${myTurn.doctor.room}, ${myTurn.doctor.name}.",
        "आपकी बारी है। टोकन ${myTurn.token}। कृपया ${myTurn.doctor.room} जाइए।",
        "آپ کی باری ہے۔ ٹوکن ${myTurn.token}۔"
    )

    LaunchedEffect(Unit) {
        if (settings.alertVibration) {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 800), -1))
        }
        if (settings.alertSound) announce()
    }

    val pulse = rememberInfiniteTransition(label = "call")
    val flash by pulse.animateColor(
        initialValue = Mint,
        targetValue = if (settings.flashOnCall) Color.White else Mint,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "flash"
    )

    Box(modifier = Modifier.fillMaxSize().background(flash)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)) { ListenButton(onClick = { announce() }) }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("■ CALLED", style = MaterialTheme.typography.labelLarge, color = Ink)
            Text("IT'S YOUR TURN.", style = MaterialTheme.typography.displayMedium, color = Ink)
            Spacer(modifier = Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(Color.White)
                    .padding(24.dp)
            ) {
                Text("GO TO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(myTurn.doctor.room.uppercase(), style = MaterialTheme.typography.displayLarge, color = Ink)
                Text(
                    "${myTurn.doctor.name} · ${if (myTurn.doctor.floor == 0) "ground floor" else "floor ${myTurn.doctor.floor}"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("TOKEN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(myTurn.token, style = MaterialTheme.typography.headlineLarge, color = Ink)
            }
            Spacer(modifier = Modifier.height(20.dp))
            myTurn.doctor.roomWaypointId?.let { room ->
                BigButton(text = "Take me there", onClick = { onNavigateToRoom(room) }, icon = Icons.Default.Navigation)
                Spacer(modifier = Modifier.height(12.dp))
            }
            BigButton(text = "Home", onClick = onHome, type = ButtonType.SECONDARY, icon = Icons.Default.Home)
            Spacer(modifier = Modifier.height(12.dp))
            BigButton(text = "Feeling worse? Urgent help", onClick = onEmergencyClick, type = ButtonType.DANGER, icon = Icons.Default.Warning)
        }
    }
}

package app.sevasetu.patient.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.sevasetu.patient.MainActivity
import app.sevasetu.patient.R
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.ui.i18n.trNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps watching the patient's place while the phone is in a pocket (PRD 8.6): a quiet ongoing notification with
 * the token and people ahead, an alert when 3 or fewer are ahead, and a loud one when called or sent to Emergency.
 * Live updates wake it at once; it also checks every 20 seconds in case they drop.
 */
class QueueWatcherService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var alertedNear: String? = null
    private var alertedFinal: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val visit = SevaApplication.instance.visits.active.value
        ServiceCompat.startForeground(
            this, ONGOING_ID, ongoing(visit),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        if (job?.isActive != true) startWatching()
        return START_STICKY
    }

    private fun startWatching() {
        job = scope.launch {
            val app = SevaApplication.instance
            val visitId = app.visits.active.value?.visitId ?: return@launch stop()
            val wake = Channel<Unit>(Channel.CONFLATED)
            launch {
                app.backendGateway.observeVisit(visitId, app.visits.active.value?.hospital?.id)
                    .retryWhen { _, attempt -> delay(minOf(30_000L, 2_000L shl attempt.toInt().coerceAtMost(4))); true }
                    .catch { }
                    .collect { wake.trySend(Unit) }
            }
            while (true) {
                val turn = runCatching { app.backendGateway.getMyTurn(visitId) }.getOrNull()
                if (turn != null) {
                    app.visits.save(turn, confirmed = turn.offlineToken == null || turn.token != turn.offlineToken)
                    if (handle(turn)) return@launch stop()
                }
                withTimeoutOrNull(20_000) { wake.receive() }
            }
        }
    }

    /** Shows the right alert for this state. True when there is nothing left to watch. */
    private fun handle(turn: MyTurnDto): Boolean {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ONGOING_ID, ongoing(turn))
        val ahead = turn.estimate?.peopleAhead
        when (turn.status) {
            "called" -> {
                if (alertedFinal != turn.visitId) {
                    alertedFinal = turn.visitId
                    alert(trNow("It's your turn: {token}", "token" to turn.token), trNow("Please go to {room} ({doctor}) now.", "room" to turn.doctor.room, "doctor" to turn.doctor.name))
                }
                return true
            }
            "emergency" -> {
                if (alertedFinal != turn.visitId) {
                    alertedFinal = turn.visitId
                    alert(trNow("Go to Emergency now"), trNow("Staff have asked you to go to Emergency (Casualty). You do not need a token."))
                }
                return true
            }
            "done", "cancelled", "no_show" -> return true
        }
        if (ahead != null && ahead <= 3 && alertedNear != turn.visitId) {
            alertedNear = turn.visitId
            alert(
                trNow("Your turn is coming: {token}", "token" to turn.token),
                if (ahead == 0) trNow("You are next. Please wait near {room}.", "room" to turn.doctor.room)
                else trNow("Only {n} ahead of you. Please come near {room}.", "n" to ahead, "room" to turn.doctor.room)
            )
        }
        return false
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun ongoing(turn: MyTurnDto?) = NotificationCompat.Builder(this, ONGOING_CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_seva)
        .setContentTitle(if (turn != null) trNow("Token {token} · {room}", "token" to turn.token, "room" to turn.doctor.room) else trNow("SevaSetu"))
        .setContentText(
            when {
                turn == null -> trNow("Watching your place in the queue")
                turn.estimate?.paused == true -> trNow("The doctor is away for now. Your place is kept.")
                turn.estimate != null -> trNow("{n} ahead of you", "n" to turn.estimate.peopleAhead) + " · " +
                    trNow("Now serving {token}", "token" to (turn.nowServing ?: "–"))
                else -> trNow("Watching your place in the queue")
            }
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openApp())
        .build()

    private fun alert(title: String, text: String) {
        val prefs = SevaApplication.instance.prefs.current
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_seva)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .setFullScreenIntent(openApp(), true)
        if (!prefs.alertSound) builder.setSilent(true)
        manager.notify(ALERT_ID, builder.build())
        if (prefs.alertVibration) vibrate()
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 250, 600, 250, 900), -1))
    }

    private fun createChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(ONGOING_CHANNEL, trNow("Your place in the queue"), NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, trNow("Your turn alerts"), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
        )
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ONGOING_CHANNEL = "seva_queue"
        private const val ALERT_CHANNEL = "seva_alerts"
        private const val ONGOING_ID = 1
        private const val ALERT_ID = 2

        /** Starts watching the saved visit. Safe to call repeatedly; does nothing without a visit. */
        fun start(context: Context) {
            if (SevaApplication.instance.visits.active.value == null) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, QueueWatcherService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, QueueWatcherService::class.java))
        }
    }
}

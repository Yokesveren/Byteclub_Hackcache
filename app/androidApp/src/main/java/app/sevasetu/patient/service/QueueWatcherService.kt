package app.sevasetu.patient.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.sevasetu.patient.SevaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QueueWatcherService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private var alertedAt3Ahead = false
    private var alertedCalled = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SevaSetu Queue Watcher")
            .setContentText("Watching your queue position...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
        startWatching()

        return START_STICKY
    }

    private fun startWatching() {
        job?.cancel()
        job = scope.launch {
            val app = SevaApplication.instance
            while (true) {
                delay(5000)
                try {
                    val turn = app.backendGateway.getMyTurn("kamla-demo-visit")
                    val ahead = turn.estimate?.peopleAhead ?: 10

                    if (ahead <= 3 && !alertedAt3Ahead) {
                        alertedAt3Ahead = true
                        sendAlertNotification("Queue Alert", "Only $ahead people ahead! Please stay near the room.")
                    }

                    if (turn.status == "called" && !alertedCalled) {
                        alertedCalled = true
                        sendAlertNotification("It's Your Turn!", "Please proceed to ${turn.doctor.room} now.")
                        break
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun sendAlertNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(2, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Queue Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "SevaQueueWatcherChannel"
    }
}

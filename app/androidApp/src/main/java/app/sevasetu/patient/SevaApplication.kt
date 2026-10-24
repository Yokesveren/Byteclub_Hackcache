package app.sevasetu.patient

import android.app.Application
import androidx.room.Room
import app.sevasetu.patient.data.db.SevaDatabase
import app.sevasetu.patient.data.gateway.BackendGateway
import app.sevasetu.patient.data.gateway.FakeBackend
import app.sevasetu.patient.data.gateway.RemoteBackend
import app.sevasetu.patient.data.outbox.OutboxManager
import app.sevasetu.patient.data.visit.VisitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SevaApplication : Application() {

    lateinit var database: SevaDatabase
        private set

    /** The on-phone simulator: the backend when no server is configured, and the demo panel's controls. */
    lateinit var fakeBackend: FakeBackend
        private set

    lateinit var backendGateway: BackendGateway
        private set

    lateinit var outboxManager: OutboxManager
        private set

    lateinit var visits: VisitRepository
        private set

    /** True when the app talks to the shared hospital backend (the same one the staff website uses). */
    val isLive: Boolean get() = backendGateway is RemoteBackend

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(
            applicationContext,
            SevaDatabase::class.java,
            "seva_patient_db"
        ).fallbackToDestructiveMigration().build()

        fakeBackend = FakeBackend(database.dao())
        // sevasetu.supabaseUrl / sevasetu.supabaseAnonKey in app/local.properties switch on the live backend.
        backendGateway = if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            RemoteBackend(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        } else {
            fakeBackend
        }
        visits = VisitRepository(database.dao())
        outboxManager = OutboxManager(database.dao(), backendGateway, applicationContext, visits)

        appScope.launch {
            visits.load()
            // Anything queued while offline goes out as soon as the app starts again (urgent flags first).
            runCatching { outboxManager.flushOutbox() }
        }
        outboxManager.schedulePeriodicWork()
    }

    companion object {
        lateinit var instance: SevaApplication
            private set
    }
}

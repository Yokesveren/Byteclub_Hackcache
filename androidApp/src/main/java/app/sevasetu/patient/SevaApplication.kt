package app.sevasetu.patient

import android.app.Application
import androidx.room.Room
import app.sevasetu.patient.data.db.SevaDatabase
import app.sevasetu.patient.data.gateway.BackendGateway
import app.sevasetu.patient.data.gateway.FakeBackend
import app.sevasetu.patient.data.outbox.OutboxManager

class SevaApplication : Application() {

    lateinit var database: SevaDatabase
        private set

    lateinit var fakeBackend: FakeBackend
        private set

    lateinit var backendGateway: BackendGateway
        private set

    lateinit var outboxManager: OutboxManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(
            applicationContext,
            SevaDatabase::class.java,
            "seva_patient_db"
        ).fallbackToDestructiveMigration().build()

        fakeBackend = FakeBackend(database.dao())
        backendGateway = fakeBackend
        outboxManager = OutboxManager(database.dao(), backendGateway, applicationContext)

        outboxManager.schedulePeriodicWork()
    }

    companion object {
        lateinit var instance: SevaApplication
            private set
    }
}

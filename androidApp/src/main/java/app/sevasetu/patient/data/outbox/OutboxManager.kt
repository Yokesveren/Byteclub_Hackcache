package app.sevasetu.patient.data.outbox

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.sevasetu.patient.data.db.OutboxItemEntity
import app.sevasetu.patient.data.db.SevaDao
import app.sevasetu.patient.data.gateway.BackendGateway
import app.sevasetu.patient.data.model.JourneyEventDto
import app.sevasetu.patient.data.model.RegisterVisitParamDto
import app.sevasetu.patient.data.model.SymptomCheckDto
import app.sevasetu.patient.data.model.UrgentFlagDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutboxManager @Inject constructor(
    private val dao: SevaDao,
    private val backendGateway: BackendGateway,
    private val context: Context
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun enqueueUrgentFlag(flag: UrgentFlagDto) {
        val args = json.encodeToString(flag)
        val item = OutboxItemEntity(
            id = flag.id,
            functionName = "raiseUrgentFlag",
            argsJson = args,
            priority = 0,
            createdAt = System.currentTimeMillis()
        )
        dao.enqueueOutbox(item)
        flushOutbox()
    }

    suspend fun enqueueRegisterVisit(param: RegisterVisitParamDto, isPriority: Boolean) {
        val args = json.encodeToString(param)
        val priority = if (isPriority) 1 else 2
        val item = OutboxItemEntity(
            id = param.visitId,
            functionName = "registerVisit",
            argsJson = args,
            priority = priority,
            createdAt = System.currentTimeMillis()
        )
        dao.enqueueOutbox(item)
        flushOutbox()
    }

    suspend fun enqueueSymptomCheck(check: SymptomCheckDto) {
        val args = json.encodeToString(check)
        val item = OutboxItemEntity(
            id = check.id,
            functionName = "recordSymptomCheck",
            argsJson = args,
            priority = 3,
            createdAt = System.currentTimeMillis()
        )
        dao.enqueueOutbox(item)
        flushOutbox()
    }

    suspend fun enqueueJourneyEvent(event: JourneyEventDto) {
        val args = json.encodeToString(event)
        val item = OutboxItemEntity(
            id = event.id,
            functionName = "ingestJourneyEvents",
            argsJson = args,
            priority = 4,
            createdAt = System.currentTimeMillis()
        )
        dao.enqueueOutbox(item)
        flushOutbox()
    }

    suspend fun flushOutbox(): Boolean = withContext(Dispatchers.IO) {
        val pending = dao.getPendingOutboxItems()
        var allSuccess = true

        for (item in pending) {
            val updatedSending = item.copy(state = "SENDING", attempts = item.attempts + 1)
            dao.updateOutboxItem(updatedSending)

            try {
                when (item.functionName) {
                    "raiseUrgentFlag" -> {
                        val flag = json.decodeFromString<UrgentFlagDto>(item.argsJson)
                        backendGateway.raiseUrgentFlag(flag)
                    }
                    "registerVisit" -> {
                        val reg = json.decodeFromString<RegisterVisitParamDto>(item.argsJson)
                        backendGateway.registerVisit(reg)
                    }
                    "recordSymptomCheck" -> {
                        val check = json.decodeFromString<SymptomCheckDto>(item.argsJson)
                        backendGateway.recordSymptomCheck(check)
                    }
                    "ingestJourneyEvents" -> {
                        val event = json.decodeFromString<JourneyEventDto>(item.argsJson)
                        backendGateway.ingestJourneyEvents(listOf(event))
                    }
                }
                dao.updateOutboxItem(item.copy(state = "DONE"))
            } catch (e: Exception) {
                allSuccess = false
                dao.updateOutboxItem(item.copy(state = "FAILED", lastError = e.localizedMessage ?: "Sync error"))
            }
        }
        allSuccess
    }

    fun schedulePeriodicWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<OutboxWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "SevaOutboxWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

class OutboxWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Background sync task
        return Result.success()
    }
}

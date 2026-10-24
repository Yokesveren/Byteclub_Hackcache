package app.sevasetu.patient.data.visit

import app.sevasetu.patient.data.db.SevaDao
import app.sevasetu.patient.data.db.StoredVisitEntity
import app.sevasetu.patient.data.model.MyTurnDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The patient's current visit, kept on the phone (S8: works offline, survives restarts).
 * Registration, "I have my slip" and every refresh save here; Home, My Turn, the slip, Health Journey
 * and the queue watcher all read the same visit instead of a hard-coded demo patient.
 */
class VisitRepository(private val dao: SevaDao) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _active = MutableStateFlow<MyTurnDto?>(null)

    /** The latest saved visit, or null before the patient has registered or linked a slip. */
    val active: StateFlow<MyTurnDto?> = _active.asStateFlow()

    suspend fun load(): MyTurnDto? = withContext(Dispatchers.IO) {
        val stored = dao.getAllStoredVisits().firstOrNull()
        val turn = stored?.let { runCatching { json.decodeFromString<MyTurnDto>(it.jsonContent) }.getOrNull() }
        _active.value = turn
        turn
    }

    /** Saves a ticket from the server, or a provisional offline one ([confirmed] = false, S8). */
    suspend fun save(turn: MyTurnDto, confirmed: Boolean = true) = withContext(Dispatchers.IO) {
        dao.upsertStoredVisit(
            StoredVisitEntity(
                visitId = turn.visitId,
                hospitalId = turn.hospital.id,
                departmentCode = turn.department.code,
                token = turn.token,
                offlineToken = turn.offlineToken,
                isConfirmed = confirmed,
                jsonContent = json.encodeToString(MyTurnDto.serializer(), turn),
            )
        )
        _active.value = turn
    }

    /** Ends the visit on this phone (seen by the doctor, or the patient starts again). */
    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clearStoredVisits()
        _active.value = null
    }

    /** True while the shown token is still the phone's own and the hospital has not confirmed it. */
    suspend fun isConfirmed(visitId: String): Boolean = withContext(Dispatchers.IO) {
        dao.getStoredVisit(visitId)?.isConfirmed ?: true
    }
}

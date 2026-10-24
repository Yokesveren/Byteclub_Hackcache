package app.sevasetu.patient.data.navigation

import android.content.Context
import app.sevasetu.patient.data.model.JourneyEventDto
import app.sevasetu.patient.data.outbox.OutboxManager
import app.sevasetu.patient.data.prefs.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

/** What a scan meant, so the screen can say it (PRD 9.5). */
enum class ScanResult { STARTED, ON_ROUTE, REROUTED, ARRIVED, UNKNOWN_STICKER, OTHER_HOSPITAL }

/**
 * Where the patient is inside the hospital: the last sticker scanned and where they are going.
 * Scan events go to the backend (via the outbox) only with movement consent, under a random session id that is
 * never the visit or patient id (S9).
 */
class NavSession(
    private val context: Context,
    private val prefs: Prefs,
    private val outbox: OutboxManager,
    private val scope: CoroutineScope,
) {

    private val graphs = mutableMapOf<String, HospitalGraphDto?>()

    /** The indoor map of a hospital, or null where none has been drawn yet (only H01 so far). */
    fun graph(hospitalId: String): HospitalGraphDto? = graphs.getOrPut(hospitalId) {
        runCatching {
            val text = context.assets.open("graph/$hospitalId.json").bufferedReader().use { it.readText() }
            Json { ignoreUnknownKeys = true }.decodeFromString<HospitalGraphDto>(text)
        }.getOrNull()
    }

    private val _position = MutableStateFlow<String?>(null)
    val position: StateFlow<String?> = _position.asStateFlow()

    private val _destination = MutableStateFlow<String?>(null)
    val destination: StateFlow<String?> = _destination.asStateFlow()

    private val _lastScanAt = MutableStateFlow(0L)
    val lastScanAt: StateFlow<Long> = _lastScanAt.asStateFlow()

    private var sessionId = UUID.randomUUID().toString()
    private var entered = false

    fun setDestination(waypointId: String?) {
        _destination.value = waypointId
    }

    /** A new visit starts a new anonymous session, so two visits are never linked in the flow data. */
    fun newSession() {
        sessionId = UUID.randomUUID().toString()
        entered = false
        _position.value = null
    }

    private val _lastResult = MutableStateFlow<ScanResult?>(null)
    /** What the latest scan meant, for the route screen to say once. */
    val lastResult: StateFlow<ScanResult?> = _lastResult.asStateFlow()
    fun consumeResult() { _lastResult.value = null }

    /** The route from [from] to the destination, stair-free when the patient asked (falls back if none exists). */
    fun plan(graph: HospitalGraphDto, from: String, to: String): List<String>? {
        val avoid = prefs.current.avoidStairs
        return GraphRouter.route(graph, from, to, RouteOptions(avoidStairs = avoid))
            ?: if (avoid) GraphRouter.route(graph, from, to, RouteOptions()) else null
    }

    /** Records a scanned sticker and says what it meant against the route the patient was following. */
    fun scan(stickerId: String): ScanResult {
        val hospital = stickerId.split(":").getOrNull(1) ?: return ScanResult.UNKNOWN_STICKER
        val graph = graph(hospital) ?: return ScanResult.OTHER_HOSPITAL
        val node = graph.nodes.firstOrNull { it.id == stickerId && it.sticker } ?: return ScanResult.UNKNOWN_STICKER

        val previous = _position.value
        val dest = _destination.value
        val plannedRemaining = if (previous != null && dest != null) plan(graph, previous, dest) else null
        _position.value = stickerId
        _lastScanAt.value = System.currentTimeMillis()

        val result = when {
            stickerId == _destination.value -> ScanResult.ARRIVED
            previous == null -> ScanResult.STARTED
            plannedRemaining != null && stickerId !in plannedRemaining -> ScanResult.REROUTED
            else -> ScanResult.ON_ROUTE
        }
        val type = when {
            !entered && ("entrance" in node.tags || previous == null) -> "entry".also { entered = true }
            result == ScanResult.ARRIVED -> "room"
            result == ScanResult.REROUTED -> "wrong_turn"
            "exit" in node.tags && previous != null -> "exit"
            else -> "scan"
        }
        _lastResult.value = result
        if (prefs.current.movementConsent) {
            val event = JourneyEventDto(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                hospitalId = hospital,
                type = type,
                waypointId = stickerId,
                occurredAt = Instant.now().toString(),
            )
            // Queued first, so a scan made offline is kept; the screen does not wait for the network.
            scope.launch { outbox.enqueueJourneyEvent(event) }
        }
        return result
    }
}

package app.sevasetu.patient

import app.sevasetu.patient.data.gateway.BackendException
import app.sevasetu.patient.data.gateway.NetworkException
import app.sevasetu.patient.data.gateway.RemoteBackend
import app.sevasetu.patient.data.model.JourneyEventDto
import app.sevasetu.patient.data.model.PatientDto
import app.sevasetu.patient.data.model.RegisterVisitParamDto
import app.sevasetu.patient.data.model.SymptomCheckDto
import app.sevasetu.patient.data.model.UrgentFlagDto
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs the app's real [RemoteBackend] against the shared backend (docs/CONTRACT.md). Skipped unless
 * SEVASETU_TEST_URL and SEVASETU_TEST_ANON_KEY are set, e.g. against `npx supabase start` from the repo root:
 *   SEVASETU_TEST_URL=http://127.0.0.1:54321 SEVASETU_TEST_ANON_KEY=<ANON_KEY> ./gradlew :androidApp:testDebugUnitTest
 * It creates fictional visits in that database, so point it at a local or demo backend only.
 */
class RemoteBackendLiveTest {
    private lateinit var backend: RemoteBackend

    @Before
    fun setUp() {
        val url = System.getenv("SEVASETU_TEST_URL").orEmpty()
        val key = System.getenv("SEVASETU_TEST_ANON_KEY").orEmpty()
        assumeTrue("Set SEVASETU_TEST_URL and SEVASETU_TEST_ANON_KEY to run the live backend test", url.isNotBlank() && key.isNotBlank())
        backend = RemoteBackend(url, key)
    }

    private fun visit(name: String, department: String = "M", band: String = "green") = RegisterVisitParamDto(
        visitId = UUID.randomUUID().toString(),
        hospitalId = "H01",
        departmentCode = department,
        suggestedBand = band,
        doctorNote = "App live test",
        patient = PatientDto(id = UUID.randomUUID().toString(), firstName = name, ageGroup = "adult", ageYears = 40, language = "en"),
        symptomCheck = SymptomCheckDto(id = UUID.randomUUID().toString(), cluster = "fever"),
    )

    @Test
    fun publicTablesAndClock() = runBlocking {
        val hospitals = backend.listHospitals()
        assertTrue(hospitals.any { it.id == "H01" }, "H01 is listed")
        assertTrue(backend.listDepartments().any { it.code == "M" }, "General Medicine is listed")
        assertTrue(backend.listDoctors().any { it.roomWaypointId.startsWith("SS:H01:") }, "doctors carry map waypoints")
        assertTrue(backend.getClock().today.isNotBlank())
    }

    @Test
    fun registerReadAndLinkAVisit() = runBlocking {
        val p = visit("Testa")
        val turn = backend.registerVisit(p)
        assertEquals(p.visitId, turn.visitId)
        assertEquals("waiting", turn.status)
        assertTrue(turn.token.startsWith("M-"), "official token ${turn.token}")
        assertEquals("SS1:SLIP:H01:${p.visitId}", turn.slipQr)
        assertNotNull(turn.estimate, "a waiting visit has an estimate")

        // Idempotent on the device id: resending from the outbox returns the same ticket.
        assertEquals(turn.token, backend.registerVisit(p).token)

        val again = backend.getMyTurn(p.visitId)
        assertEquals(turn.token, again.token)

        val linked = backend.linkSlip("H01", turn.token)
        assertEquals(p.visitId, linked?.visitId)
        assertEquals(null, backend.linkSlip("H01", "Z-999"))
    }

    @Test
    fun offlineTokenIsConfirmed() = runBlocking {
        val p = visit("Offlina").copy(offlineToken = "M-xT3", createdAt = Instant.now().minusSeconds(120).toString())
        val turn = backend.registerVisit(p)
        assertEquals("M-xT3", turn.offlineToken)
        assertTrue(turn.token != "M-xT3", "the hospital issues its own token")
        assertEquals("token_confirmed", turn.lastReason?.reasonCode)
    }

    @Test
    fun urgentFlagLifecycle() = runBlocking {
        val turn = backend.registerVisit(visit("Flaga"))
        val flag = UrgentFlagDto(
            id = UUID.randomUUID().toString(),
            visitId = turn.visitId,
            hospitalId = "H01",
            message = "App live test",
            raisedAt = Instant.now().toString(),
        )
        val sent = backend.raiseUrgentFlag(flag)
        assertNotNull(sent.receivedAt, "the server records when it received the flag")
        assertEquals(null, sent.seenAt)
        assertEquals(flag.id, backend.getUrgentFlag(flag.id)?.id)
    }

    @Test
    fun symptomCheckAndJourneyEvents() = runBlocking {
        backend.recordSymptomCheck(
            SymptomCheckDto(id = UUID.randomUUID().toString(), hospitalId = "H01", outcome = "emergency", cluster = "respiratory", createdAt = Instant.now().toString())
        )
        val session = UUID.randomUUID().toString()
        val events = listOf("entry" to "SS:H01:F0:W001", "scan" to "SS:H01:F0:W004").map { (type, waypoint) ->
            JourneyEventDto(UUID.randomUUID().toString(), session, "H01", type, waypoint, Instant.now().toString())
        }
        assertEquals(2, backend.ingestJourneyEvents(events))
        assertEquals(0, backend.ingestJourneyEvents(events), "duplicates are ignored")
    }

    @Test
    fun realtimeTellsTheAppWhenTheQueueMoves() = runBlocking {
        val mine = backend.registerVisit(visit("Livea"))
        val update = async { withTimeout(15_000) { backend.observeVisit(mine.visitId, "H01").first() } }
        delay(2_000) // let the socket join its channels
        backend.registerVisit(visit("Otherb")) // someone else joins the H01 queue -> queue_changed on hospital:H01
        update.await()
    }

    @Test
    fun serverRuleRejectionIsNotTreatedAsOffline() {
        runBlocking {
            assertFailsWith<BackendException> { backend.registerVisit(visit("Badd", department = "Z")) }
        }
    }

    @Test
    fun unreachableServerIsOffline() {
        runBlocking {
            assertFailsWith<NetworkException> { RemoteBackend("http://127.0.0.1:9", "key").getClock() }
        }
    }
}

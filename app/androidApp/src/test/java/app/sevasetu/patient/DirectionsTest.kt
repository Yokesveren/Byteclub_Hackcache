package app.sevasetu.patient

import app.sevasetu.patient.data.navigation.Directions
import app.sevasetu.patient.data.navigation.GraphRouter
import app.sevasetu.patient.data.navigation.HospitalGraphDto
import app.sevasetu.patient.data.navigation.RouteOptions
import app.sevasetu.patient.data.navigation.Turn
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DirectionsTest {

    private lateinit var graph: HospitalGraphDto

    @Before
    fun setUp() {
        graph = Json { ignoreUnknownKeys = true }.decodeFromString(File("src/main/assets/graph/H01.json").readText())
    }

    private fun steps(from: String, to: String, avoidStairs: Boolean = false) =
        Directions.steps(graph, GraphRouter.route(graph, from, to, RouteOptions(avoidStairs = avoidStairs))!!)

    @Test
    fun gateToRoom3StairFreeTakesTheLiftAndEndsAtTheRoom() {
        val s = steps("SS:H01:F0:W001", "SS:H01:F1:W103", avoidStairs = true)
        assertTrue("uses the lift", s.any { it.turn == Turn.LIFT })
        assertTrue("never the stairs", s.none { it.turn == Turn.STAIRS })
        assertEquals("SS:H01:F1:W103", s.last().target)
        assertEquals(1, s.last().floor)
        assertEquals(Turn.START, s.first().turn)
    }

    @Test
    fun everyStepEndsAtAStickerThePatientCanScan() {
        val byId = graph.nodes.associateBy { it.id }
        val s = steps("SS:H01:F0:W001", "SS:H01:F1:W114")
        assertTrue(s.all { byId[it.target]?.sticker == true })
        assertTrue(s.all { it.instruction.isNotBlank() })
    }

    @Test
    fun turnsFollowThePlanCoordinates() {
        // Walking east along the ground-floor corridor, the help desk (north, y smaller) is on the left
        // and the waiting area (south) is on the right.
        val left = steps("SS:H01:F0:W020", "SS:H01:F0:W004")
        assertNotNull(left.firstOrNull { it.turn == Turn.LEFT || it.turn == Turn.RIGHT || it.turn == Turn.START })
        val east = Directions.steps(graph, listOf("SS:H01:F0:V310", "SS:H01:F0:W006", "SS:H01:F0:W004"))
        assertEquals(Turn.LEFT, east.last().turn)
        val south = Directions.steps(graph, listOf("SS:H01:F0:V310", "SS:H01:F0:W006", "SS:H01:F0:V440", "SS:H01:F0:W016"))
        assertEquals(Turn.RIGHT, south.last().turn)
    }

    @Test
    fun distanceIgnoresLiftRides() {
        val path = GraphRouter.route(graph, "SS:H01:F0:W016", "SS:H01:F1:W016", RouteOptions(avoidStairs = true))!!
        assertEquals(0, Directions.distance(graph, path))
    }
}

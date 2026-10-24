package app.sevasetu.patient

import app.sevasetu.patient.data.navigation.GraphRouter
import app.sevasetu.patient.data.navigation.HospitalGraphDto
import app.sevasetu.patient.data.navigation.RouteOptions
import app.sevasetu.patient.ui.myturn.translateReasonCode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class GraphRouterTest {

    private lateinit var graph: HospitalGraphDto

    @Before
    fun setUp() {
        val jsonString = File("src/main/assets/graph/H01.json").readText()
        val json = Json { ignoreUnknownKeys = true }
        graph = json.decodeFromString<HospitalGraphDto>(jsonString)
    }

    @Test
    fun testTenPlacePairsRoutingTable() {
        val pairs = listOf(
            Pair("SS:H01:F0:W001", "SS:H01:F0:W005"), // Gate to Emergency
            Pair("SS:H01:F0:W001", "SS:H01:F0:W003"), // Gate to Registration
            Pair("SS:H01:F0:W001", "SS:H01:F0:W012"), // Gate to Pharmacy
            Pair("SS:H01:F0:W001", "SS:H01:F1:W103"), // Gate to Room 3 (Floor 1)
            Pair("SS:H01:F0:W003", "SS:H01:F0:W004"), // Registration to Help Desk
            Pair("SS:H01:F0:W004", "SS:H01:F1:W103"), // Help Desk to Room 3
            Pair("SS:H01:F0:W012", "SS:H01:F0:W014"), // Pharmacy to X-Ray
            Pair("SS:H01:F0:W016", "SS:H01:F1:W016"), // Lift ground to Lift first floor
            Pair("SS:H01:F0:W017", "SS:H01:F1:W017"), // Stairs ground to Stairs first floor
            Pair("SS:H01:F1:W103", "SS:H01:F1:W114")  // Room 3 to Room 14 (Floor 1)
        )

        println("\n=== ROUTING UNIT TEST RESULTS TABLE ===")
        println("| #  | From -> To | Avoid Stairs | Path Length | Path Nodes |")
        println("|----|------------|--------------|-------------|------------|")

        var index = 1
        pairs.forEach { (from, to) ->
            val normalPath = GraphRouter.route(graph, from, to, RouteOptions(avoidStairs = false))
            val stairFreePath = GraphRouter.route(graph, from, to, RouteOptions(avoidStairs = true))

            assertNotNull("Normal path from $from to $to should exist", normalPath)
            println("| $index | $from -> $to | False | ${normalPath?.size} | ${normalPath?.joinToString(" -> ")} |")

            if (stairFreePath != null) {
                println("| $index | $from -> $to | True  | ${stairFreePath.size} | ${stairFreePath.joinToString(" -> ")} |")
            } else {
                println("| $index | $from -> $to | True  | NO ROUTE (Stairs Avoided) |")
            }
            index++
        }
    }

    @Test
    fun testLiftVsStairsCostDifference() {
        val from = "SS:H01:F0:W016"
        val to = "SS:H01:F1:W016"

        val normalPath = GraphRouter.route(graph, from, to, RouteOptions(avoidStairs = false))
        val stairFreePath = GraphRouter.route(graph, from, to, RouteOptions(avoidStairs = true))

        assertNotNull(normalPath)
        assertNotNull(stairFreePath)
        assertEquals(from, normalPath?.first())
        assertEquals(to, normalPath?.last())
    }

    @Test
    fun testReasonCodeTranslations() {
        val t1 = translateReasonCode("priority_patient")
        val t2 = translateReasonCode("clinical_review")
        val t3 = translateReasonCode("token_confirmed")

        assertTrue(t1.contains("needs care sooner", ignoreCase = true))
        assertTrue(t2.contains("nurse reviewed", ignoreCase = true))
        assertTrue(t3.contains("confirmed your offline token", ignoreCase = true))
        // An unknown code falls back to the server's English message (contract section 4).
        assertEquals("Moved by staff", translateReasonCode("something_new", "Moved by staff"))
    }
}

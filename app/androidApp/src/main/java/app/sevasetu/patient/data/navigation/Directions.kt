package app.sevasetu.patient.data.navigation

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/** One step of a route (PRD 9.4): walk from one sticker to the next, then scan it. */
data class RouteStep(
    val instruction: String,
    val target: String,           // the sticker that ends this step
    val targetName: String,
    val landmark: String?,
    val distanceM: Int,
    val turn: Turn,
    val floor: Int,               // the floor the step ends on
)

enum class Turn { START, STRAIGHT, LEFT, RIGHT, LIFT, STAIRS, ARRIVE }

fun GraphNodeDto.displayName(): String = name["en"] ?: id
fun GraphNodeDto.landmarkText(): String? = landmark?.get("en")?.takeIf { it.isNotBlank() && it != "None" }

fun HospitalGraphDto.floorName(floor: Int): String =
    floors.firstOrNull { it.floor == floor }?.name?.lowercase() ?: "floor $floor"

/**
 * Turns a node path into steps that end at stickers, worked out from the plan's coordinates
 * (x grows east, y grows south, so a positive cross product is a right turn on screen).
 */
object Directions {

    fun steps(graph: HospitalGraphDto, path: List<String>): List<RouteStep> {
        if (path.size < 2) return emptyList()
        val byId = graph.nodes.associateBy { it.id }
        val edges = graph.edges.associateBy { setOf(it.from, it.to) }
        val nodes = path.mapNotNull { byId[it] }
        if (nodes.size != path.size) return emptyList()

        val steps = mutableListOf<RouteStep>()
        var legStart = 0
        for (i in 1 until nodes.size) {
            val n = nodes[i]
            val last = i == nodes.lastIndex
            if (!n.sticker && !last) continue

            val leg = nodes.subList(legStart, i + 1)
            val legEdges = leg.zipWithNext().mapNotNull { (a, b) -> edges[setOf(a.id, b.id)] }
            val distance = legEdges.sumOf { if (it.lift || it.stairs) 0.0 else it.distanceM }.roundToInt()
            val lift = legEdges.any { it.lift }
            val stairs = legEdges.any { it.stairs }

            val startTurn = when {
                lift -> Turn.LIFT
                stairs -> Turn.STAIRS
                legStart == 0 -> Turn.START
                else -> turnAt(nodes[legStart - 1], nodes[legStart], nodes[legStart + 1])
            }
            // A corner between stickers (at an unmarked corridor point) still needs saying.
            val laterTurn = if (startTurn == Turn.STRAIGHT || startTurn == Turn.START) {
                (legStart + 1 until i).asSequence()
                    .map { k -> turnAt(nodes[k - 1], nodes[k], nodes[k + 1]) }
                    .firstOrNull { it == Turn.LEFT || it == Turn.RIGHT }
            } else null
            val turn = if (startTurn == Turn.STRAIGHT && laterTurn != null) laterTurn else startTurn
            val name = n.displayName()
            val walk = if (distance > 0) "walk about $distance m to $name" else "go to $name"
            val ahead = laterTurn?.let { "walk ahead, then turn ${side(it)} to reach $name" + if (distance > 0) " ($distance m)" else "" }
            val instruction = when (turn) {
                Turn.LIFT -> "Take the lift to the ${graph.floorName(n.floor)}."
                Turn.STAIRS -> "Take the stairs to the ${graph.floorName(n.floor)}."
                Turn.START -> "From here, ${ahead ?: walk}."
                Turn.STRAIGHT -> "Go straight on and $walk."
                Turn.LEFT, Turn.RIGHT ->
                    if (startTurn == Turn.STRAIGHT) "${ahead!!.replaceFirstChar { it.uppercase() }}."
                    else "Turn ${side(turn)} and $walk."
                Turn.ARRIVE -> "You have arrived."
            }
            steps += RouteStep(instruction, n.id, name, n.landmarkText(), distance, turn, n.floor)
            legStart = i
        }
        return steps
    }

    private fun side(t: Turn) = if (t == Turn.LEFT) "left" else "right"

    private fun turnAt(a: GraphNodeDto, b: GraphNodeDto, c: GraphNodeDto): Turn {
        val h1x = (b.x - a.x).toDouble(); val h1y = (b.y - a.y).toDouble()
        val h2x = (c.x - b.x).toDouble(); val h2y = (c.y - b.y).toDouble()
        if ((h1x == 0.0 && h1y == 0.0) || (h2x == 0.0 && h2y == 0.0)) return Turn.STRAIGHT
        val angle = Math.toDegrees(atan2(h1x * h2y - h1y * h2x, h1x * h2x + h1y * h2y))
        return when {
            abs(angle) < 30 -> Turn.STRAIGHT
            angle > 0 -> Turn.RIGHT
            else -> Turn.LEFT
        }
    }

    /** Total walking distance of a path in metres (lift and stair rides not counted). */
    fun distance(graph: HospitalGraphDto, path: List<String>): Int {
        val edges = graph.edges.associateBy { setOf(it.from, it.to) }
        return path.zipWithNext().sumOf { (a, b) ->
            edges[setOf(a, b)]?.let { if (it.lift || it.stairs) 0.0 else it.distanceM } ?: 0.0
        }.roundToInt()
    }
}

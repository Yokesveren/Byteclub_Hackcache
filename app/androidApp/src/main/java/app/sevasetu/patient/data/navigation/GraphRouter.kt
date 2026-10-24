package app.sevasetu.patient.data.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GraphNodeDto(
    @SerialName("id") val id: String,
    @SerialName("floor") val floor: Int,
    @SerialName("x") val x: Int,
    @SerialName("y") val y: Int,
    @SerialName("sticker") val sticker: Boolean,
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("name") val name: Map<String, String> = emptyMap(),
    @SerialName("landmark") val landmark: Map<String, String>? = null
)

@Serializable
data class GraphEdgeDto(
    @SerialName("from") val from: String,
    @SerialName("to") val to: String,
    @SerialName("distance_m") val distanceM: Double,
    @SerialName("stairs") val stairs: Boolean = false,
    @SerialName("lift") val lift: Boolean = false,
    @SerialName("ramp") val ramp: Boolean = false,
    @SerialName("crowd_prone") val crowdProne: Boolean = false
)

@Serializable
data class GraphAreaDto(
    @SerialName("id") val id: String,
    @SerialName("floor") val floor: Int,
    @SerialName("label") val label: String,
    @SerialName("kind") val kind: String,
    @SerialName("x") val x: Int,
    @SerialName("y") val y: Int,
    @SerialName("w") val w: Int,
    @SerialName("h") val h: Int,
    @SerialName("capacity") val capacity: Int = 10,
    @SerialName("waypoints") val waypoints: List<String> = emptyList()
)

@Serializable
data class FloorDto(
    @SerialName("floor") val floor: Int,
    @SerialName("name") val name: String,
    @SerialName("width") val width: Int,
    @SerialName("height") val height: Int
)

@Serializable
data class HospitalGraphDto(
    @SerialName("hospital_id") val hospitalId: String,
    @SerialName("name") val name: String,
    @SerialName("version") val version: Int = 1,
    @SerialName("floors") val floors: List<FloorDto> = emptyList(),
    @SerialName("entrance") val entrance: String,
    @SerialName("emergency") val emergency: String,
    @SerialName("nodes") val nodes: List<GraphNodeDto> = emptyList(),
    @SerialName("edges") val edges: List<GraphEdgeDto> = emptyList(),
    @SerialName("areas") val areas: List<GraphAreaDto> = emptyList()
)

data class RouteOptions(
    val avoidStairs: Boolean = false,
    val emergency: Boolean = false
)

object GraphRouter {

    private const val LIFT_WAIT_M = 10.0
    private const val CROWD_PENALTY_M = 3.0

    private fun edgeCost(e: GraphEdgeDto, options: RouteOptions): Double {
        if (options.emergency) return e.distanceM
        return e.distanceM + (if (e.lift) LIFT_WAIT_M else 0.0) + (if (e.crowdProne) CROWD_PENALTY_M else 0.0)
    }

    fun route(graph: HospitalGraphDto, from: String, to: String, options: RouteOptions = RouteOptions()): List<String>? {
        if (from == to) return listOf(from)

        val adj = mutableMapOf<String, MutableList<Pair<String, Double>>>()
        graph.nodes.forEach { adj[it.id] = mutableListOf() }

        graph.edges.forEach { e ->
            if (!(options.avoidStairs && e.stairs)) {
                val w = edgeCost(e, options)
                adj[e.from]?.add(Pair(e.to, w))
                adj[e.to]?.add(Pair(e.from, w))
            }
        }

        val dist = mutableMapOf<String, Double>().apply { put(from, 0.0) }
        val prev = mutableMapOf<String, String>()
        val done = mutableSetOf<String>()

        while (true) {
            var u: String? = null
            var best = Double.POSITIVE_INFINITY

            for ((id, d) in dist) {
                if (!done.contains(id) && d < best) {
                    u = id
                    best = d
                }
            }

            if (u == null) return null
            if (u == to) break
            done.add(u)

            adj[u]?.forEach { (v, w) ->
                val nd = best + w
                if (nd < (dist[v] ?: Double.POSITIVE_INFINITY)) {
                    dist[v] = nd
                    prev[v] = u
                }
            }
        }

        val path = mutableListOf(to)
        while (path.first() != from) {
            val p = prev[path.first()] ?: return null
            path.add(0, p)
        }
        return path
    }

    fun stickersOn(graph: HospitalGraphDto, path: List<String>): List<String> {
        val byId = graph.nodes.associateBy { it.id }
        return path.filter { byId[it]?.sticker == true }
    }
}

package app.sevasetu.patient.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sevasetu.patient.data.navigation.GraphRouter
import app.sevasetu.patient.data.navigation.HospitalGraphDto
import app.sevasetu.patient.data.navigation.RouteOptions
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.ListenButton
import app.sevasetu.patient.ui.components.SevaScaffold
import kotlinx.serialization.json.Json

fun loadH01GraphFromAssets(context: android.content.Context): HospitalGraphDto {
    val jsonString = context.assets.open("graph/H01.json").bufferedReader().use { it.readText() }
    val json = Json { ignoreUnknownKeys = true }
    return json.decodeFromString<HospitalGraphDto>(jsonString)
}

@Composable
fun IndoorNavigationScreen(
    destinationWaypointId: String,
    onOpenScanner: () -> Unit,
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val context = LocalContext.current
    val graph = remember { loadH01GraphFromAssets(context) }
    var avoidStairs by remember { mutableStateOf(true) }
    var currentFloor by remember { mutableIntStateOf(0) }
    var lastScannedSticker by remember { mutableStateOf(graph.entrance) }

    val path = remember(lastScannedSticker, destinationWaypointId, avoidStairs) {
        GraphRouter.route(graph, lastScannedSticker, destinationWaypointId, RouteOptions(avoidStairs = avoidStairs))
            ?: GraphRouter.route(graph, lastScannedSticker, destinationWaypointId, RouteOptions(avoidStairs = false))
            ?: listOf(lastScannedSticker, destinationWaypointId)
    }

    val nodesById = remember(graph) { graph.nodes.associateBy { it.id } }
    val destNode = nodesById[destinationWaypointId]
    val destName = destNode?.name?.get("en") ?: "Destination"

    SevaScaffold(
        title = "Find Your Way",
        onBack = onBack,
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Step Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Destination: $destName",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        ListenButton(onClick = {})
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Walk straight along corridor to Floor $currentFloor",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Landmark: Next to green notice board",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Scan the sticker at the end of this corridor",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Floor Plan Map Renderer (F34)
            InfoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Floor Plan (Floor $currentFloor)", style = MaterialTheme.typography.titleLarge)
                    Row {
                        OutlinedButton(
                            onClick = { currentFloor = 0 },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Floor 0") }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { currentFloor = 1 },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Floor 1") }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Color(0xFFE0F2F1), RoundedCornerShape(16.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height
                        val mapW = 1000f
                        val mapH = 560f
                        val scaleX = canvasW / mapW
                        val scaleY = canvasH / mapH

                        // Draw areas
                        graph.areas.filter { it.floor == currentFloor }.forEach { area ->
                            val rx = area.x * scaleX
                            val ry = area.y * scaleY
                            val rw = area.w * scaleX
                            val rh = area.h * scaleY
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(rx, ry),
                                size = Size(rw, rh)
                            )
                            drawRect(
                                color = Color(0xFFB8C7C4),
                                topLeft = Offset(rx, ry),
                                size = Size(rw, rh),
                                style = Stroke(width = 2f)
                            )
                        }

                        // Draw route highlight
                        val floorPathNodes = path.mapNotNull { nodesById[it] }.filter { it.floor == currentFloor }
                        for (i in 0 until floorPathNodes.size - 1) {
                            val n1 = floorPathNodes[i]
                            val n2 = floorPathNodes[i + 1]
                            drawLine(
                                color = Color(0xFF0B6E6E),
                                start = Offset(n1.x * scaleX, n1.y * scaleY),
                                end = Offset(n2.x * scaleX, n2.y * scaleY),
                                strokeWidth = 8f
                            )
                        }

                        // Draw "You Are Here" Dot
                        val startNode = nodesById[lastScannedSticker]
                        if (startNode != null && startNode.floor == currentFloor) {
                            drawCircle(
                                color = Color(0xFFC62828),
                                radius = 14f,
                                center = Offset(startNode.x * scaleX, startNode.y * scaleY)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            BigButton(
                text = "I reached the sticker (Scan QR)",
                onClick = onOpenScanner,
                icon = Icons.Default.QrCodeScanner,
                type = ButtonType.PRIMARY
            )

            Spacer(modifier = Modifier.height(12.dp))

            BigButton(
                text = "I'm Lost (Call Helper / Reset Route)",
                onClick = {
                    lastScannedSticker = graph.entrance
                },
                type = ButtonType.SECONDARY
            )
        }
    }
}

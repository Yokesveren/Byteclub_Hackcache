package app.sevasetu.patient.ui.scanner

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.sevasetu.patient.data.navigation.HospitalGraphDto
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.navigation.loadH01GraphFromAssets

@Composable
fun StickerScannerScreen(
    onStickerScanned: (stickerNodeId: String) -> Unit,
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val graph = remember { loadH01GraphFromAssets(context) }

    var showSimulateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val stickerNodes = remember(graph) { graph.nodes.filter { it.sticker } }

    SevaScaffold(
        title = "Scan Wall Sticker",
        onBack = onBack,
        onEmergencyClick = onEmergencyClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!showSimulateDialog) {
                // Camera Preview
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                            } catch (_: Exception) {}
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Square frame overlay & Text
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xCC000000))
                    ) {
                        Text(
                            text = "Point camera at the QR sticker on the wall",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 18.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .background(Color(0x22FFFFFF), RoundedCornerShape(20.dp))
                    )

                    BigButton(
                        text = "Simulate Scan (Select Sticker from List)",
                        onClick = { showSimulateDialog = true },
                        type = ButtonType.SECONDARY,
                        icon = Icons.Default.QrCode
                    )
                }
            } else {
                // Searchable Sticker Picker Dialog / Sheet
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Select Sticker Node to Scan", style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { showSimulateDialog = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search sticker name or floor...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val filteredNodes = stickerNodes.filter {
                        val name = it.name["en"] ?: ""
                        val id = it.id
                        name.contains(searchQuery, ignoreCase = true) || id.contains(searchQuery, ignoreCase = true)
                    }

                    LazyColumn {
                        items(filteredNodes) { node ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        showSimulateDialog = false
                                        onStickerScanned(node.id)
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = node.name["en"] ?: node.id,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Floor ${node.floor} • ID: ${node.id}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    node.landmark?.get("en")?.let { landmark ->
                                        Text(
                                            text = "Landmark: $landmark",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

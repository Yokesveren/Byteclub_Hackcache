package app.sevasetu.patient.ui.scanner

import app.sevasetu.patient.ui.i18n.LocalLang
import app.sevasetu.patient.ui.i18n.floorLabel
import android.Manifest
import app.sevasetu.patient.ui.i18n.tr
import app.sevasetu.patient.ui.i18n.trNow
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.navigation.displayName
import app.sevasetu.patient.data.navigation.landmarkText
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.theme.Accent
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.LocalExtraColors
import app.sevasetu.patient.ui.theme.Mint
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** What the scanner is for: a wall sticker (`SS:H01:F1:W103`), a slip (`SS1:SLIP:H01:<visit>`), or either. */
enum class ScanMode { STICKER, SLIP, ANY }

fun isSticker(text: String) = Regex("^SS:H\\d{2}:F\\d+:W\\w+$").matches(text)
fun isSlip(text: String) = text.startsWith("SS1:SLIP:") && text.split(":").size == 4

@Composable
fun StickerScannerScreen(
    mode: ScanMode,
    onResult: (String) -> Unit,
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCamera = it
        askedOnce = true
    }
    LaunchedEffect(Unit) { if (!hasCamera) permission.launch(Manifest.permission.CAMERA) }

    var showList by remember { mutableStateOf(false) }
    var warning by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(warning) { if (warning != null) { delay(2500); warning = null } }

    val title = when (mode) {
        ScanMode.SLIP -> tr("Scan your slip")
        else -> tr("Scan a sticker")
    }

    SevaScaffold(title = title, onBack = onBack, onEmergencyClick = onEmergencyClick) {
        if (showList && mode != ScanMode.SLIP) {
            StickerList(onPick = { onResult(it) }, onClose = { showList = false })
            return@SevaScaffold
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (hasCamera) {
                QrCamera(
                    accept = { text ->
                        val ok = when (mode) {
                            ScanMode.STICKER -> isSticker(text)
                            ScanMode.SLIP -> isSlip(text)
                            ScanMode.ANY -> isSticker(text) || isSlip(text)
                        }
                        if (!ok) warning = if (mode == ScanMode.SLIP) trNow("That is not a SevaSetu slip.") else trNow("That is not a SevaSetu sticker.")
                        ok
                    },
                    onFound = onResult
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        !hasCamera -> tr("SevaSetu needs the camera to read QR codes.")
                        mode == ScanMode.SLIP -> tr("Hold the QR code on your slip inside the square.")
                        else -> tr("Hold the QR sticker on the wall inside the square.")
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC0B2A3C))
                        .padding(16.dp)
                )

                if (hasCamera) {
                    Box(
                        modifier = Modifier
                            .size(250.dp)
                            .border(4.dp, if (warning != null) Color(0xFFF2B01E) else Mint, RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        warning?.let {
                            Text(
                                text = it,
                                color = Ink,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF2B01E))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                } else {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Mint, modifier = Modifier.size(96.dp))
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (!hasCamera) {
                        BigButton(
                            text = tr("Allow camera"),
                            icon = Icons.Default.QrCodeScanner,
                            onClick = {
                                if (askedOnce) {
                                    // Denied twice: Android will not ask again, so open the app's settings page.
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.fromParts("package", context.packageName, null)
                                        )
                                    )
                                } else permission.launch(Manifest.permission.CAMERA)
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (mode != ScanMode.SLIP) {
                        BigButton(
                            text = tr("Can't scan? Choose where you are"),
                            type = ButtonType.SECONDARY,
                            icon = Icons.Default.FormatListBulleted,
                            onClick = { showList = true }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun QrCamera(accept: (String) -> Boolean, onFound: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val done = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    var lastRejected by remember { mutableStateOf("") }
    val bound = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    DisposableEffect(Unit) {
        onDispose {
            // Stop the camera before its worker thread, so no frame arrives at a closed executor.
            done.set(true)
            runCatching { bound[0]?.unbindAll() }
            executor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = runCatching { providerFuture.get() }.getOrNull() ?: return@addListener
                if (done.get()) return@addListener
                bound[0] = provider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || done.get()) {
                        proxy.close(); return@setAnalyzer
                    }
                    val task = try {
                        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    } catch (e: Exception) {
                        proxy.close(); return@setAnalyzer // the reader closed as the screen left
                    }
                    task
                        .addOnSuccessListener(mainExecutor) { codes ->
                            val text = codes.firstNotNullOfOrNull { it.rawValue?.trim() } ?: return@addOnSuccessListener
                            if (done.get()) return@addOnSuccessListener
                            if (accept(text)) {
                                if (done.compareAndSet(false, true)) onFound(text)
                            } else if (text != lastRejected) {
                                lastRejected = text
                            }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, mainExecutor)
            previewView
        }
    )
}

/** For when a sticker is missing or will not scan: pick the nearest place by name. Same result as a scan. */
@Composable
private fun StickerList(onPick: (String) -> Unit, onClose: () -> Unit) {
    val app = SevaApplication.instance
    val hospitalId = app.visits.active.value?.hospital?.id ?: app.prefs.current.hospitalId
    val graph = remember(hospitalId) { app.nav.graph(hospitalId) ?: app.nav.graph("H01") }
    val stickers = remember(graph) { graph?.nodes?.filter { it.sticker }?.sortedWith(compareBy({ it.floor }, { it.displayName() })).orEmpty() }
    val x = LocalExtraColors.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = tr("WHERE ARE YOU?"), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = tr("Choose the place nearest to you. It works the same as scanning its sticker."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            var lastFloor = -1
            stickers.forEach { node ->
                if (node.floor != lastFloor) {
                    lastFloor = node.floor
                    item(key = "floor-${node.floor}") {
                        Text(
                            text = floorLabel(node.floor).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                        )
                    }
                }
                item(key = node.id) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(x.field)
                            .clickable { onPick(node.id) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = node.displayName(LocalLang.current), style = MaterialTheme.typography.titleMedium)
                            node.landmarkText(LocalLang.current)?.let {
                                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Accent)
                    }
                }
            }
        }
        BigButton(text = tr("Back to the camera"), type = ButtonType.SECONDARY, onClick = onClose, modifier = Modifier.padding(vertical = 12.dp))
    }
}

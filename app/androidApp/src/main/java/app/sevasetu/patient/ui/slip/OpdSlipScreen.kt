package app.sevasetu.patient.ui.slip

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.LastUpdatedText
import app.sevasetu.patient.ui.components.ListenButton
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StatusPill
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

fun generateQrBitmap(content: String, size: Int = 400): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bmp
}

@Composable
fun OpdSlipScreen(
    myTurn: MyTurnDto,
    onNavigateToRoom: (destinationWaypointId: String) -> Unit,
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(myTurn.slipQr) { generateQrBitmap(myTurn.slipQr) }

    fun openGoogleMaps() {
        val lat = myTurn.hospital.lat
        val lng = myTurn.hospital.lng
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"))
        context.startActivity(intent)
    }

    SevaScaffold(
        title = "OPD Slip",
        onBack = onBack,
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ticket Shaped Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = myTurn.hospital.name,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "OPD Registration Slip",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // Token Display (56sp bold)
                    Text(
                        text = myTurn.token,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    if (myTurn.offlineToken != null) {
                        Text(
                            text = "(Waiting to be confirmed)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    StatusPill(
                        label = if (myTurn.bandProvisional) "${myTurn.bandLabel} (provisional)" else myTurn.bandLabel,
                        type = myTurn.band
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(text = "Patient: ${myTurn.firstName}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(text = "Department: ${myTurn.department.name}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Doctor: ${myTurn.doctor.name}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Room: ${myTurn.doctor.room} (Floor ${myTurn.doctor.floor})", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

                        myTurn.estimate?.let { est ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "People ahead: ${est.peopleAhead}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            if (est.windowStart != null && est.windowEnd != null) {
                                Text(
                                    text = "Estimated window: ${est.windowStart} - ${est.windowEnd}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // QR Code Image
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "OPD Slip QR Code",
                            modifier = Modifier.size(200.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    LastUpdatedText(time = myTurn.lastUpdated)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ListenButton(onClick = {})
                OutlinedButton(onClick = { openGoogleMaps() }) {
                    Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Google Maps")
                }
                OutlinedButton(onClick = {}) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share QR")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            BigButton(
                text = "Take me to my room",
                onClick = { onNavigateToRoom(myTurn.doctor.roomWaypointId) },
                icon = Icons.Default.Navigation,
                type = ButtonType.PRIMARY
            )
        }
    }
}

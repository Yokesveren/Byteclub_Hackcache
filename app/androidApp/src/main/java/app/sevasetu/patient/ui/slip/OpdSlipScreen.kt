package app.sevasetu.patient.ui.slip

import app.sevasetu.patient.ui.i18n.deptName
import app.sevasetu.patient.ui.i18n.floorLabel
import android.content.Intent
import app.sevasetu.patient.ui.i18n.tr
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.CardShape
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StatusPill
import app.sevasetu.patient.ui.components.displayTime
import app.sevasetu.patient.ui.theme.ColorPriorityContainer
import app.sevasetu.patient.ui.theme.ColorPriorityContent
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.LocalExtraColors
import app.sevasetu.patient.ui.theme.Mint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
}

/**
 * The OPD slip (PRD 8.5): token, first name, department and room, the time window and a QR code that carries no
 * health data (S9). Staff scan it at the counter; the patient can show it instead of a paper slip.
 */
@Composable
fun OpdSlipScreen(
    myTurn: MyTurnDto,
    onNavigateToRoom: (destinationWaypointId: String) -> Unit,
    onOpenMyTurn: () -> Unit,
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val context = LocalContext.current
    val app = SevaApplication.instance
    val qr = remember(myTurn.slipQr) { generateQrBitmap(myTurn.slipQr) }
    val est = myTurn.estimate
    val unconfirmed = myTurn.offlineToken != null && myTurn.token == myTurn.offlineToken
    val window = if (est != null && !est.paused && est.windowStart != null && est.windowEnd != null)
        "${displayTime(est.windowStart)} – ${displayTime(est.windowEnd)}" else null

    fun share() {
        val text = buildString {
            append("SevaSetu OPD token ${myTurn.token}\n")
            append("${myTurn.firstName} · ${myTurn.department.name}\n")
            append("${myTurn.doctor.name}, ${myTurn.doctor.room}\n")
            append(myTurn.hospital.name.removeSuffix(" (Demo)"))
            window?.let { append("\nLikely between $it") }
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share slip"))
    }

    SevaScaffold(
        title = tr("OPD slip"),
        onBack = onBack,
        onListen = {
            app.speaker.speak(
                "Your token is ${myTurn.token}. ${myTurn.department.name}. ${myTurn.doctor.name}, ${myTurn.doctor.room}." +
                    (window?.let { " Likely between $it." } ?: ""),
                "आपका टोकन ${myTurn.token} है। ${myTurn.doctor.room}।"
            )
        },
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // The ticket: mint head with the token, white body with the QR
            Column(modifier = Modifier.fillMaxWidth().clip(CardShape).background(Color.White)) {
                Column(modifier = Modifier.fillMaxWidth().background(Mint).padding(22.dp)) {
                    Text(myTurn.hospital.name.removeSuffix(" (Demo)").uppercase(), style = MaterialTheme.typography.labelMedium, color = Ink)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(myTurn.token, style = MaterialTheme.typography.displayLarge, color = Ink, modifier = Modifier.weight(1f))
                        StatusPill(label = myTurn.bandLabel, type = myTurn.band, provisional = myTurn.bandProvisional)
                    }
                    if (unconfirmed) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            tr("WAITING TO BE CONFIRMED"),
                            style = MaterialTheme.typography.labelMedium,
                            color = ColorPriorityContent,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ColorPriorityContainer).padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
                    SlipLine(tr("Name"), myTurn.firstName)
                    SlipLine(tr("Department"), deptName(myTurn.department))
                    SlipLine(tr("Doctor"), "${myTurn.doctor.name} · ${myTurn.doctor.room}, ${floorLabel(myTurn.doctor.floor)}")
                    if (est != null) {
                        SlipLine(tr("Ahead of you"), if (est.peopleAhead == 0) tr("You are next") else tr("{n} people", "n" to est.peopleAhead))
                    }
                    window?.let { SlipLine(tr("Likely seen"), it) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = LocalExtraColors.current.edge)
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(bitmap = qr.asImageBitmap(), contentDescription = "Slip QR code for token ${myTurn.token}", modifier = Modifier.size(220.dp))
                    }
                    Text(
                        tr("Show this at the counter or the doctor's door."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    displayTime(myTurn.lastUpdated)?.let {
                        Text(
                            tr("Last updated at {time}", "time" to it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            BigButton(text = tr("Open my turn"), icon = Icons.Default.Schedule, onClick = onOpenMyTurn)
            Spacer(modifier = Modifier.height(12.dp))
            myTurn.doctor.roomWaypointId?.let { room ->
                BigButton(text = tr("Take me to my room"), icon = Icons.Default.Navigation, type = ButtonType.SECONDARY, onClick = { onNavigateToRoom(room) })
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(
                    text = tr("Directions"),
                    icon = Icons.Default.Map,
                    type = ButtonType.SECONDARY,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${myTurn.hospital.lat},${myTurn.hospital.lng}"))
                        )
                    }
                )
                BigButton(text = tr("Share"), icon = Icons.Default.Share, type = ButtonType.SECONDARY, modifier = Modifier.weight(1f), onClick = { share() })
            }
        }
    }
}

@Composable
private fun SlipLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(tr(label).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
    }
}

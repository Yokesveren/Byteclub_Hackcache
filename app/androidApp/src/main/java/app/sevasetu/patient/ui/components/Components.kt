package app.sevasetu.patient.ui.components

import androidx.compose.foundation.BorderStroke
import app.sevasetu.patient.ui.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.ui.theme.Accent
import app.sevasetu.patient.ui.theme.ColorEmergency
import app.sevasetu.patient.ui.theme.Frame
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.Linen
import app.sevasetu.patient.ui.theme.LocalExtraColors
import app.sevasetu.patient.ui.theme.Mint
import app.sevasetu.patient.ui.theme.NightRaised
import app.sevasetu.patient.ui.theme.SevaTheme
import app.sevasetu.patient.ui.theme.Ward

// The SevaSetu website's language, on a phone: a navy frame holds the bar and the always-visible emergency button;
// the work sits on a white sheet with 24dp corners; actions are teal, secondary actions navy, emergency red;
// headings are black-weight capitals. Colour is never the only signal: every status carries a word and an icon.

private val ControlShape = RoundedCornerShape(12.dp)
private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
val CardShape = RoundedCornerShape(24.dp)

/** Setu is a bridge: one arch over the water line (the website's mark). */
@Composable
fun SetuMark(size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size).semantics { contentDescription = "SevaSetu" }) {
        val u = this.size.width / 30f
        drawRoundRect(Accent, cornerRadius = CornerRadius(6 * u))
        val arch = Path().apply {
            moveTo(5 * u, 21 * u)
            quadraticTo(15 * u, 5 * u, 25 * u, 21 * u)
        }
        drawPath(arch, Color.White, style = Stroke(width = 3.4f * u))
        drawRect(Color.White, topLeft = Offset(4 * u, 21 * u), size = Size(22 * u, 3.4f * u))
    }
}

@Composable
fun SevaScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onListen: (() -> Unit)? = null,
    onEmergencyClick: () -> Unit,
    isOffline: Boolean = false,
    lastUpdatedTime: String? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val extraColors = LocalExtraColors.current

    Scaffold(
        containerColor = extraColors.frame,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column(modifier = Modifier.background(extraColors.frame).statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(ControlShape)
                                .background(NightRaised)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Go back"), tint = Color.White)
                        }
                    } else {
                        SetuMark(size = 36.dp, modifier = Modifier.padding(start = 4.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.weight(1f).semantics { heading() },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (onListen != null) {
                        ListenButton(onClick = onListen)
                    }
                }
                if (isOffline) {
                    OfflineBanner(lastUpdatedTime = lastUpdatedTime)
                }
            }
        },
        bottomBar = {
            EmergencyBar(onClick = onEmergencyClick)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clip(SheetShape)
                .background(MaterialTheme.colorScheme.background)
        ) {
            content(innerPadding)
        }
    }
}

/** The emergency button on every screen (PRD 8.4): full width, red, with a word and an icon. */
@Composable
fun EmergencyBar(onClick: () -> Unit) {
    val extraColors = LocalExtraColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = tr("Open emergency help")) { onClick() },
        color = extraColors.emergencyBar,
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .height(60.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "◆", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = tr("EMERGENCY / आपातकालीन"),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

enum class ButtonType { PRIMARY, SECONDARY, DANGER }

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: ButtonType = ButtonType.PRIMARY,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val extraColors = LocalExtraColors.current
    // Website buttons: teal for the main action, navy for the rest, red only for emergencies.
    val container = when (type) {
        ButtonType.PRIMARY -> MaterialTheme.colorScheme.primary
        ButtonType.SECONDARY -> extraColors.frame
        ButtonType.DANGER -> extraColors.emergencyBar
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = Color.White,
            disabledContainerColor = container.copy(alpha = 0.55f),
            disabledContentColor = Color.White,
        ),
        border = if (extraColors.isHighContrast) BorderStroke(2.dp, Color.Black) else null,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(text = text.uppercase(), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

/** A big picture choice, like the dashboard's choice buttons: grey until chosen, navy when chosen. */
@Composable
fun PictureCard(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extraColors = LocalExtraColors.current
    val container = if (isSelected) extraColors.frame else extraColors.field
    val content = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .then(if (extraColors.isHighContrast) Modifier.border(2.dp, Color.Black, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClickLabel = title) { onSelect() }
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) Mint else Accent, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = tr(title), style = MaterialTheme.typography.titleMedium, color = content, textAlign = TextAlign.Center)
        if (isSelected) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = tr("Selected"), style = MaterialTheme.typography.labelMedium, color = Mint)
            }
        }
    }
}

@Composable
fun StepHeader(
    currentStep: Int,
    totalSteps: Int,
    question: String
) {
    val extraColors = LocalExtraColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = tr("STEP {n} OF {total}", "n" to currentStep, "total" to totalSteps),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { currentStep.toFloat() / totalSteps },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = extraColors.field,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = question.uppercase(),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
    }
}

/**
 * Band and status chips, as on the website (BandChip.tsx): pale tint with dark text of the same hue, a word and an
 * icon, and a dashed edge while the band is only suggested and not yet confirmed by staff.
 */
@Composable
fun StatusPill(
    label: String,
    type: String, // routine, priority, emergency, waiting, called, done
    modifier: Modifier = Modifier,
    provisional: Boolean = false,
) {
    val x = LocalExtraColors.current
    data class Chip(val container: Color, val content: Color, val edge: Color, val icon: String)
    val chip = when (type.lowercase()) {
        "routine", "green" -> Chip(x.routineContainer, x.routineContent, x.routineEdge, "●")
        "priority", "yellow" -> Chip(x.priorityContainer, x.priorityContent, x.priorityEdge, "▲")
        "emergency", "red" -> Chip(ColorEmergency, Color.White, ColorEmergency, "◆")
        "called" -> Chip(Mint, Ink, Accent, "■")
        else -> Chip(Ward, Ink, Ward, "●")
    }
    val edge = if (x.isHighContrast) Color.Black else chip.edge
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(chip.container)
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    color = edge,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(width = stroke, pathEffect = if (provisional) PathEffect.dashPathEffect(floatArrayOf(10f, 7f)) else null)
                )
            }
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .semantics { contentDescription = if (provisional) "$label, suggested, not yet confirmed by staff" else label },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = chip.icon, color = chip.content, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = tr(label), color = chip.content, style = MaterialTheme.typography.labelLarge)
        if (provisional) {
            Text(text = tr(" · unconfirmed"), color = chip.content.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The dashboard's panel: white, 24dp corners, a hairline ring. */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val x = LocalExtraColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (x.isHighContrast) 2.dp else 1.dp, x.edge, CardShape)
            .padding(20.dp),
        content = content
    )
}

/**
 * A full-width field of one chapter colour with a black-capital title (the website's PageBand).
 * Tones: "ward", "linen", "mint", "teal", "night".
 */
@Composable
fun ScreenBand(
    title: String,
    modifier: Modifier = Modifier,
    tone: String = "ward",
    lead: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val (bg, ink) = when (tone) {
        "linen" -> Linen to Ink
        "mint" -> Mint to Ink
        "teal" -> Accent to Color.White
        "night" -> Frame to Color(0xFFF2F2F2)
        else -> Ward to Ink
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(bg)
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Text(text = title.uppercase(), style = MaterialTheme.typography.displayMedium, color = ink, modifier = Modifier.semantics { heading() })
        if (lead != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = lead, style = MaterialTheme.typography.bodyLarge, color = ink)
        }
        if (content != null) {
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun ListenButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(ControlShape)
            .background(Mint)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = "Read screen aloud" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = tr("LISTEN"), style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

@Composable
fun LastUpdatedText(time: String) {
    Text(
        text = tr("Last updated at {time}", "time" to displayTime(time)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Honest offline state (PRD 7.3): amber, with the time of the information shown. */
@Composable
fun OfflineBanner(lastUpdatedTime: String? = null) {
    val x = LocalExtraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(x.offlineContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "▲", color = x.offlineContent, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (lastUpdatedTime != null) tr("No internet. Showing saved info (updated {time}).", "time" to lastUpdatedTime) else tr("No internet. Showing saved info."),
            style = MaterialTheme.typography.bodyMedium,
            color = x.offlineContent
        )
    }
}

@Composable
fun EmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(56.dp), tint = Accent)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingState(message: String = "Loading...") {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "◆", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(24.dp))
        BigButton(text = tr("Try again"), onClick = onRetry, type = ButtonType.PRIMARY, icon = Icons.Default.Refresh)
    }
}

// Compose Previews (F50)
@Preview
@Composable
fun ComponentPreviews() {
    SevaTheme {
        Column(modifier = Modifier.background(Color.White).padding(16.dp)) {
            EmergencyBar(onClick = {})
            Spacer(modifier = Modifier.height(10.dp))
            BigButton(text = tr("Register now"), onClick = {})
            Spacer(modifier = Modifier.height(10.dp))
            Row {
                StatusPill(label = tr("Priority"), type = "priority", provisional = true)
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(label = tr("Routine"), type = "routine")
            }
            Spacer(modifier = Modifier.height(10.dp))
            OfflineBanner(lastUpdatedTime = "10:42")
        }
    }
}

package app.sevasetu.patient.ui.navigation

import app.sevasetu.patient.ui.i18n.LocalLang
import app.sevasetu.patient.ui.i18n.floorLabel
import android.graphics.Paint
import app.sevasetu.patient.ui.i18n.tr
import app.sevasetu.patient.ui.i18n.trNow
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.navigation.Directions
import app.sevasetu.patient.data.navigation.GraphNodeDto
import app.sevasetu.patient.data.navigation.HospitalGraphDto
import app.sevasetu.patient.data.navigation.RouteStep
import app.sevasetu.patient.data.navigation.ScanResult
import app.sevasetu.patient.data.navigation.Turn
import app.sevasetu.patient.data.navigation.displayName
import app.sevasetu.patient.data.navigation.displayLabel
import app.sevasetu.patient.data.navigation.landmarkText
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.CardShape
import app.sevasetu.patient.ui.components.EmptyState
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.theme.Accent
import app.sevasetu.patient.ui.theme.ColorEmergency
import app.sevasetu.patient.ui.theme.ColorPriorityContainer
import app.sevasetu.patient.ui.theme.ColorPriorityContent
import app.sevasetu.patient.ui.theme.Control
import app.sevasetu.patient.ui.theme.Frame
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.Linen
import app.sevasetu.patient.ui.theme.LocalExtraColors
import app.sevasetu.patient.ui.theme.Mint
import app.sevasetu.patient.ui.theme.Ward
import kotlinx.coroutines.delay

private const val LOST_AFTER_MS = 90_000L

/** Places a patient may ask for: every sticker that is a destination, not a corridor point. */
private val DESTINATION_TAGS = setOf("room", "pharmacy", "lab", "xray", "registration", "help", "toilet", "waiting", "emergency")

@Composable
fun IndoorNavigationScreen(
    onOpenScanner: () -> Unit,
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val app = SevaApplication.instance
    val visit by app.visits.active.collectAsState()
    val settings by app.prefs.settings.collectAsState()
    val hospitalId = visit?.hospital?.id ?: settings.hospitalId
    val graph = remember(hospitalId) { app.nav.graph(hospitalId) }

    val position by app.nav.position.collectAsState()
    val destination by app.nav.destination.collectAsState()
    val lastScanAt by app.nav.lastScanAt.collectAsState()
    val lastResult by app.nav.lastResult.collectAsState()
    var choosing by remember { mutableStateOf(false) }

    if (graph == null) {
        SevaScaffold(title = tr("Find your way"), onBack = onBack, onEmergencyClick = onEmergencyClick) {
            EmptyState(
                tr("The indoor map for {hospital} is not ready yet.", "hospital" to (visit?.hospital?.name ?: tr("this hospital"))) + "\n\n" +
                    (visit?.let { tr("Your doctor is in {room}, floor {floor}.", "room" to it.doctor.room, "floor" to it.doctor.floor) + " " } ?: "") +
                    tr("Please ask at the help desk.")
            )
        }
        return
    }

    val dest = destination?.takeIf { d -> graph.nodes.any { it.id == d } }
    if (dest == null || choosing) {
        DestinationPicker(
            graph = graph,
            doctorRoom = visit?.doctor?.roomWaypointId,
            doctorLabel = visit?.let { "${it.doctor.room} · ${it.doctor.name}" },
            onPick = {
                app.nav.setDestination(it)
                choosing = false
            },
            onBack = { if (choosing && dest != null) choosing = false else onBack() },
            onEmergencyClick = onEmergencyClick
        )
        return
    }

    val lang = LocalLang.current
    val start = position ?: graph.entrance
    val path = remember(start, dest, settings.avoidStairs) { app.nav.plan(graph, start, dest) }
    val steps = remember(path, lang) { path?.let { Directions.steps(graph, it, lang) }.orEmpty() }
    val byId = remember(graph) { graph.nodes.associateBy { it.id } }
    val destNode = byId[dest]!!
    val arrived = position == dest
    val step = steps.firstOrNull()
    val totalM = remember(path) { path?.let { Directions.distance(graph, it) } ?: 0 }

    var floor by remember { mutableIntStateOf(byId[start]?.floor ?: 0) }
    LaunchedEffect(start) { floor = byId[start]?.floor ?: floor }

    // "Are you lost?" (PRD 9.5): about 90 seconds on a route without a scan.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); now = System.currentTimeMillis() } }
    val routeStartedAt = remember(dest) { System.currentTimeMillis() }
    val lost = !arrived && position != null && now - maxOf(lastScanAt, routeStartedAt) > LOST_AFTER_MS

    fun speakStep() {
        val text = when {
            arrived -> trNow("You have arrived at {place}.", "place" to destNode.displayName(lang))
            step == null -> trNow("Scan the nearest sticker to start.")
            else -> step.instruction + (step.landmark?.let { " " + trNow("Look for: {landmark}", "landmark" to it) + "." } ?: "") + trNow(" Then scan the sticker there.")
        }
        app.speaker.speak(text)
    }
    LaunchedEffect(lastResult) {
        if (lastResult != null) {
            delay(4_000)
            app.nav.consumeResult()
        }
    }

    SevaScaffold(
        title = tr("Find your way"),
        onBack = onBack,
        onListen = { speakStep() },
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Going to
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LocalExtraColors.current.field)
                    .clickable(onClickLabel = tr("Change destination")) { choosing = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Flag, contentDescription = null, tint = Accent)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr("GOING TO"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(destNode.displayName(lang), style = MaterialTheme.typography.titleMedium)
                }
                Text(tr("CHANGE"), style = MaterialTheme.typography.labelLarge, color = Accent)
            }

            Spacer(modifier = Modifier.height(12.dp))

            lastResult?.let { ScanBanner(it, byId[position]?.displayName(lang)) }

            // The one thing to do now
            NowCard(
                arrived = arrived,
                started = position != null,
                step = step,
                destName = destNode.displayName(lang),
                entranceName = byId[graph.entrance]?.displayName(lang) ?: tr("the main gate"),
                remainingM = totalM,
                noRoute = path == null
            )

            if (lost) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(ColorPriorityContainer)
                        .padding(18.dp)
                ) {
                    Text(tr("ARE YOU LOST?"), style = MaterialTheme.typography.titleLarge, color = ColorPriorityContent)
                    Text(
                        tr("Scan the nearest sticker and we will find a new route, or ask a helper at the help desk."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorPriorityContent
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!arrived) {
                BigButton(
                    text = if (position == null) tr("Scan a sticker to start") else tr("Scan the next sticker"),
                    onClick = onOpenScanner,
                    icon = Icons.Default.QrCodeScanner
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Floor plan
            FloorPlan(
                graph = graph,
                floor = floor,
                onFloor = { floor = it },
                path = path.orEmpty(),
                position = position,
                destination = dest
            )

            Spacer(modifier = Modifier.height(16.dp))

            // The whole route
            if (steps.size > 1 && !arrived) {
                InfoCard {
                    Text(tr("THE WAY"), style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    steps.forEachIndexed { i, s ->
                        StepRow(index = i + 1, step = s, current = i == 0)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("Avoid stairs"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            tr("Use the lift and ramps only."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.avoidStairs, onCheckedChange = { on -> app.prefs.update { it.copy(avoidStairs = on) } })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            BigButton(
                text = tr("I'm lost: take me to the help desk"),
                type = ButtonType.SECONDARY,
                icon = Icons.Default.SupportAgent,
                onClick = {
                    graph.nodes.firstOrNull { "help" in it.tags }?.let { app.nav.setDestination(it.id) }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ScanBanner(result: ScanResult, here: String?) {
    val (text, bg, ink) = when (result) {
        ScanResult.STARTED -> Triple(tr("You are at {place}.", "place" to (here ?: tr("this sticker"))), Mint, Ink)
        ScanResult.ON_ROUTE -> Triple("Good, you are on the right way.", Mint, Ink)
        ScanResult.REROUTED -> Triple("No problem. Let's take a new route.", ColorPriorityContainer, ColorPriorityContent)
        ScanResult.ARRIVED -> Triple("You have arrived.", Mint, Ink)
        ScanResult.UNKNOWN_STICKER -> Triple("We do not know that sticker. Try the next one.", ColorPriorityContainer, ColorPriorityContent)
        ScanResult.OTHER_HOSPITAL -> Triple("That sticker is from another hospital.", ColorPriorityContainer, ColorPriorityContent)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = ink,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun NowCard(
    arrived: Boolean,
    started: Boolean,
    step: RouteStep?,
    destName: String,
    entranceName: String,
    remainingM: Int,
    noRoute: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (arrived) Mint else Frame)
            .padding(22.dp)
    ) {
        val ink = if (arrived) Ink else Color.White
        when {
            arrived -> {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Ink, modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text(tr("YOU HAVE ARRIVED"), style = MaterialTheme.typography.headlineMedium, color = ink)
                Text(tr("This is {place}.", "place" to destName), style = MaterialTheme.typography.bodyLarge, color = ink)
            }
            noRoute -> {
                Text(tr("NO ROUTE FOUND"), style = MaterialTheme.typography.headlineMedium, color = ink)
                Text(
                    tr("There is no way there without stairs. Turn off \"Avoid stairs\" or ask a staff member to help you."),
                    style = MaterialTheme.typography.bodyLarge, color = ink
                )
            }
            !started -> {
                Text(tr("START AT A STICKER"), style = MaterialTheme.typography.headlineMedium, color = ink)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    tr("Scan the QR sticker at {gate}, or the nearest sticker on a wall. About {m} m from the gate to {place}.", "gate" to entranceName, "m" to remainingM, "place" to destName),
                    style = MaterialTheme.typography.bodyLarge, color = ink
                )
            }
            step != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Mint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(turnIcon(step.turn), contentDescription = null, tint = Ink, modifier = Modifier.size(34.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        if (remainingM > 0) tr("{m} M TO GO", "m" to remainingM) else tr("NEARLY THERE"),
                        style = MaterialTheme.typography.labelLarge,
                        color = Mint
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(step.instruction, style = MaterialTheme.typography.headlineMedium, color = ink)
                step.landmark?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(tr("Look for: {landmark}", "landmark" to it), style = MaterialTheme.typography.bodyLarge, color = Color(0xFFC9ECE6))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(tr("Then scan the sticker at {place}.", "place" to step.targetName), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9ECE6))
            }
        }
    }
}

private fun turnIcon(turn: Turn): ImageVector = when (turn) {
    Turn.LEFT -> Icons.Default.TurnLeft
    Turn.RIGHT -> Icons.Default.TurnRight
    Turn.LIFT -> Icons.Default.Elevator
    Turn.STAIRS -> Icons.Default.Stairs
    Turn.STRAIGHT -> Icons.Default.Straight
    Turn.START -> Icons.AutoMirrored.Filled.DirectionsWalk
    Turn.ARRIVE -> Icons.Default.Flag
}

@Composable
private fun StepRow(index: Int, step: RouteStep, current: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (current) Accent else LocalExtraColors.current.field),
            contentAlignment = Alignment.Center
        ) {
            Text("$index", style = MaterialTheme.typography.labelMedium, color = if (current) Color.White else Ink)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(step.instruction, style = if (current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
            step.landmark?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FloorPlan(
    graph: HospitalGraphDto,
    floor: Int,
    onFloor: (Int) -> Unit,
    path: List<String>,
    position: String?,
    destination: String
) {
    val byId = remember(graph) { graph.nodes.associateBy { it.id } }
    val lang = LocalLang.current
    val plan = graph.floors.firstOrNull { it.floor == floor }
    val mapW = (plan?.width ?: 1000).toFloat()
    val mapH = (plan?.height ?: 560).toFloat()
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = Ink.toArgb()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Linen)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("FLOOR PLAN"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            graph.floors.forEach { f ->
                val on = f.floor == floor
                Text(
                    text = if (f.floor == 0) tr("GROUND") else tr("FLOOR {n}", "n" to f.floor),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) Color.White else Ink,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) Frame else Color.White)
                        .clickable { onFloor(f.floor) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(mapW / mapH)
                .clip(RoundedCornerShape(16.dp))
                .background(Ward)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val s = size.width / mapW
                labelPaint.textSize = 9.5f * s * 2.4f

                graph.areas.filter { it.floor == floor }.forEach { a ->
                    val fill = when (a.kind) {
                        "emergency" -> Color(0xFFFFE1DE)
                        "corridor" -> Color(0xFFF5F8FA)
                        "waiting" -> Color(0xFFE7F7F0)
                        else -> Color.White
                    }
                    drawRoundRect(fill, Offset(a.x * s, a.y * s), Size(a.w * s, a.h * s), CornerRadius(6f * s))
                    drawRoundRect(Control, Offset(a.x * s, a.y * s), Size(a.w * s, a.h * s), CornerRadius(6f * s), style = Stroke(1.5f))
                    if (a.kind != "corridor" && a.w * s > 40) {
                        drawIntoCanvas { c ->
                            val full = a.displayLabel(lang)
                            val label = if (labelPaint.measureText(full) > a.w * s - 8) full.substringBefore(" (") else full
                            c.nativeCanvas.drawText(label, a.x * s + 6 * s, a.y * s + 14 * s * 1.6f, labelPaint)
                        }
                    }
                }

                // Route on this floor
                val nodes = path.mapNotNull { byId[it] }
                nodes.zipWithNext().forEach { (a, b) ->
                    if (a.floor == floor && b.floor == floor) {
                        drawLine(Accent, Offset(a.x * s, a.y * s), Offset(b.x * s, b.y * s), strokeWidth = 7f * s * 1.4f, cap = StrokeCap.Round)
                    }
                }
                // Stickers on the route
                nodes.filter { it.sticker && it.floor == floor }.forEach { n ->
                    drawCircle(Color.White, 6f * s * 1.4f, Offset(n.x * s, n.y * s))
                    drawCircle(Accent, 6f * s * 1.4f, Offset(n.x * s, n.y * s), style = Stroke(2.5f))
                }
                // Destination
                byId[destination]?.takeIf { it.floor == floor }?.let { d ->
                    drawCircle(ColorEmergency, 11f * s * 1.4f, Offset(d.x * s, d.y * s))
                    drawCircle(Color.White, 4f * s * 1.4f, Offset(d.x * s, d.y * s))
                }
                // You are here
                position?.let { byId[it] }?.takeIf { it.floor == floor }?.let { p ->
                    drawCircle(Frame.copy(alpha = 0.18f), 22f * s * 1.4f, Offset(p.x * s, p.y * s))
                    drawCircle(Frame, 9f * s * 1.4f, Offset(p.x * s, p.y * s))
                    drawCircle(Color.White, 9f * s * 1.4f, Offset(p.x * s, p.y * s), style = Stroke(3f))
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(Frame, tr("You"))
            Legend(Accent, tr("Route"))
            Legend(ColorEmergency, tr("Destination"))
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(tr(label), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DestinationPicker(
    graph: HospitalGraphDto,
    doctorRoom: String?,
    doctorLabel: String?,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val places = remember(graph) {
        graph.nodes.filter { n -> n.sticker && n.tags.any { it in DESTINATION_TAGS } }
            .sortedWith(compareBy({ it.floor }, { it.displayName() }))
    }
    val x = LocalExtraColors.current
    SevaScaffold(title = tr("Where to?"), onBack = onBack, onEmergencyClick = onEmergencyClick) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(tr("WHERE DO YOU WANT TO GO?"), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(14.dp))
            if (doctorRoom != null && graph.nodes.any { it.id == doctorRoom }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(Mint)
                        .clickable { onPick(doctorRoom) }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("YOUR DOCTOR"), style = MaterialTheme.typography.labelMedium, color = Ink)
                        Text(doctorLabel ?: tr("Doctor's room"), style = MaterialTheme.typography.titleLarge, color = Ink)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Ink)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            var lastFloor = -1
            places.forEach { n ->
                if (n.floor != lastFloor) {
                    lastFloor = n.floor
                    Text(
                        floorLabel(n.floor).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                    )
                }
                PlaceRow(n, x.field, onPick)
            }
        }
    }
}

@Composable
private fun PlaceRow(n: GraphNodeDto, field: Color, onPick: (String) -> Unit) {
    val emergency = "emergency" in n.tags
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (emergency) Color(0xFFFFEDEA) else field)
            .then(if (emergency) Modifier.border(2.dp, ColorEmergency, RoundedCornerShape(16.dp)) else Modifier)
            .clickable { onPick(n.id) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(n.displayName(LocalLang.current), style = MaterialTheme.typography.titleMedium, color = if (emergency) ColorEmergency else Ink)
            n.landmarkText(LocalLang.current)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Accent)
    }
}

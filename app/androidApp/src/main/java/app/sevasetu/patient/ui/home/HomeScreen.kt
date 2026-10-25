package app.sevasetu.patient.ui.home

import android.Manifest
import app.sevasetu.patient.ui.i18n.tr
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.gateway.BackendException
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.service.QueueWatcherService
import app.sevasetu.patient.ui.components.CardShape
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StatusPill
import app.sevasetu.patient.ui.components.displayTime
import app.sevasetu.patient.ui.theme.Accent
import app.sevasetu.patient.ui.theme.ColorEmergency
import app.sevasetu.patient.ui.theme.ColorPriorityContainer
import app.sevasetu.patient.ui.theme.ColorPriorityContent
import app.sevasetu.patient.ui.theme.Frame
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.LocalExtraColors
import app.sevasetu.patient.ui.theme.Mint
import app.sevasetu.patient.ui.theme.Ward
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToHaveSlip: () -> Unit,
    onNavigateToMyTurn: () -> Unit,
    onNavigateToSlip: () -> Unit,
    onNavigateToFindPlace: () -> Unit,
    onNavigateToJourney: () -> Unit,
    onNavigateToPreVisit: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToSms: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val app = SevaApplication.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeVisit by app.visits.active.collectAsState()
    val directory by app.directory.data.collectAsState()
    var online by remember { mutableStateOf<Boolean?>(null) }

    // Alerts need notification permission (Android 13+); asked once, when it is first useful.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        val saved = app.visits.load()
        if (saved == null && !app.isLive) {
            // Simulator only: start with the demo patient Kamla, as the standalone app always did.
            runCatching { app.visits.save(app.fakeBackend.loadDemoPatientKamla()) }
        } else if (saved != null) {
            // Refresh the saved ticket; offline, the saved one keeps showing (S8).
            try {
                val fresh = app.backendGateway.getMyTurn(saved.visitId)
                online = true
                app.visits.save(fresh, confirmed = fresh.offlineToken == null || fresh.token != fresh.offlineToken)
            } catch (e: BackendException) {
                // The hospital has no such visit (for example after the demo was reset): forget it on this phone,
                // unless it is still waiting in the outbox to be registered.
                online = true
                if (e.status == 404 && app.visits.isConfirmed(saved.visitId)) app.visits.clear()
            } catch (e: Exception) {
                online = false
            }
        }
        if (online == null) online = app.directory.refresh()
        if (app.visits.active.value?.status == "waiting") {
            if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            QueueWatcherService.start(context)
        }
    }

    val visit = activeVisit
    val finished = visit != null && visit.status in setOf("done", "cancelled", "no_show")
    val hospitalName = (visit?.hospital?.name ?: directory.hospitals.firstOrNull { it.id == app.prefs.current.hospitalId }?.name)
        ?.removeSuffix(" (Demo)")

    SevaScaffold(
        title = tr("SevaSetu"),
        onListen = {
            app.speaker.speak(
                when {
                    visit == null -> "Welcome. Press Register to get a token, or I have my slip if you already have one."
                    visit.status == "called" -> "It is your turn. Please go to ${visit.doctor.room}."
                    else -> "Your token is ${visit.token}. ${visit.estimate?.peopleAhead ?: 0} people are ahead of you."
                },
                when {
                    visit == null -> "स्वागत है। टोकन लेने के लिए पंजीकरण दबाएँ।"
                    visit.status == "called" -> "आपकी बारी है। कृपया ${visit.doctor.room} जाइए।"
                    else -> "आपका टोकन ${visit.token} है। आपसे पहले ${visit.estimate?.peopleAhead ?: 0} लोग हैं।"
                }
            )
        },
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = if (visit != null && !finished) "NAMASTE, ${visit.firstName.uppercase()}" else tr("NAMASTE"),
                style = MaterialTheme.typography.displayMedium
            )
            hospitalName?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(16.dp))

            when {
                visit == null -> StartCard(onNavigateToRegister)
                finished -> FinishedCard(visit, onNewVisit = {
                    scope.launch {
                        app.visits.clear()
                        app.nav.newSession()
                        QueueWatcherService.stop(context)
                        onNavigateToRegister()
                    }
                })
                else -> TicketCard(visit, onOpen = {
                    if (visit.status == "emergency") onEmergencyClick() else onNavigateToMyTurn()
                }, onSlip = onNavigateToSlip)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (visit == null || finished) {
                    GridTile(tr("Register"), Icons.Default.PersonAdd, onNavigateToRegister, Modifier.weight(1f))
                } else {
                    GridTile(tr("My turn"), Icons.Default.Schedule, onNavigateToMyTurn, Modifier.weight(1f))
                }
                GridTile(tr("I have my slip"), Icons.Default.ConfirmationNumber, onNavigateToHaveSlip, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GridTile(tr("Find a place"), Icons.Default.Navigation, onNavigateToFindPlace, Modifier.weight(1f))
                GridTile(tr("Health journey"), Icons.AutoMirrored.Filled.Assignment, onNavigateToJourney, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            InfoCard {
                ListItemRow(title = tr("Before you visit"), subtitle = tr("Is the doctor in? Is the X-ray working?"), icon = Icons.Default.LocalHospital, onClick = onNavigateToPreVisit)
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = LocalExtraColors.current.edge)
                ListItemRow(title = tr("Help chat"), subtitle = tr("Ask where, when, what to bring"), icon = Icons.Default.Forum, onClick = onNavigateToChat)
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = LocalExtraColors.current.edge)
                ListItemRow(title = tr("Help and questions"), subtitle = null, icon = Icons.AutoMirrored.Filled.Help, onClick = onNavigateToHelp)
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = LocalExtraColors.current.edge)
                ListItemRow(title = tr("No data? Use SMS"), subtitle = tr("Token and alerts by text, from any phone"), icon = Icons.Default.Sms, onClick = onNavigateToSms)
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = LocalExtraColors.current.edge)
                ListItemRow(title = tr("Settings"), subtitle = tr("Text size, language, alerts"), icon = Icons.Default.Settings, onClick = onNavigateToSettings)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                val (dot, label) = when {
                    !app.isLive -> Color(0xFF9AA5AD) to "Simulator mode: no hospital server set"
                    online == null -> Color(0xFF9AA5AD) to "Checking the connection…"
                    online == true -> Accent to "Connected to the hospital"
                    else -> Color(0xFFD9A400) to "Offline: showing saved information"
                }
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dot))
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StartCard(onRegister: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Frame)
            .clickable(role = Role.Button, onClickLabel = tr("Register")) { onRegister() }
            .padding(22.dp)
    ) {
        Text(tr("NO QUEUE AT THE COUNTER"), style = MaterialTheme.typography.labelLarge, color = Mint)
        Spacer(modifier = Modifier.height(8.dp))
        Text(tr("Get your OPD token on this phone."), style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            tr("A few questions, one per screen. Then follow your turn live and find the doctor's room."),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFC9ECE6)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("REGISTER NOW"), style = MaterialTheme.typography.labelLarge, color = Mint)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TicketCard(visit: MyTurnDto, onOpen: () -> Unit, onSlip: () -> Unit) {
    val called = visit.status == "called"
    val emergency = visit.status == "emergency"
    val bg = when {
        emergency -> ColorEmergency
        called -> Mint
        else -> Ward
    }
    val ink = if (emergency) Color.White else Ink
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(bg)
            .clickable(onClickLabel = tr("Open my turn"), role = Role.Button) { onOpen() }
            .padding(22.dp)
    ) {
        Text(
            when {
                emergency -> tr("GO TO EMERGENCY NOW")
                called -> tr("IT'S YOUR TURN")
                else -> tr("YOUR TOKEN")
            },
            style = MaterialTheme.typography.labelLarge,
            color = ink
        )
        // The token on one line and the band chip under it, so large text never breaks either.
        Text(visit.token, style = MaterialTheme.typography.displayLarge, color = ink, maxLines = 1, softWrap = false)
        if (!emergency) {
            StatusPill(label = visit.bandLabel, type = visit.band, provisional = visit.bandProvisional)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (visit.offlineToken != null && visit.token == visit.offlineToken) {
            Text(
                tr("Waiting to be confirmed by the hospital"),
                style = MaterialTheme.typography.labelMedium,
                color = ColorPriorityContent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ColorPriorityContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text("${visit.doctor.name} · ${visit.doctor.room}", style = MaterialTheme.typography.titleMedium, color = ink)
        val est = visit.estimate
        if (!called && !emergency && est != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                when {
                    est.paused -> tr("The doctor is away for now. Your place is kept.")
                    est.peopleAhead == 0 -> tr("You are next.")
                    else -> tr("{n} ahead of you", "n" to est.peopleAhead) +
                        (if (est.windowStart != null && est.windowEnd != null) " · " + tr("likely {from}–{to}", "from" to displayTime(est.windowStart), "to" to displayTime(est.windowEnd)) else "")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = ink
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (emergency) tr("SEE THE WAY") else tr("OPEN MY TURN"), style = MaterialTheme.typography.labelLarge, color = ink)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.weight(1f))
            if (!emergency) {
                Text(
                    tr("SHOW SLIP"),
                    style = MaterialTheme.typography.labelLarge,
                    color = ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.dp, ink, RoundedCornerShape(10.dp))
                        .clickable(onClickLabel = tr("Show my slip")) { onSlip() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FinishedCard(visit: MyTurnDto, onNewVisit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(LocalExtraColors.current.field)
            .padding(22.dp)
    ) {
        Text(tr("VISIT FINISHED"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(visit.token, style = MaterialTheme.typography.headlineLarge)
        Text("${visit.doctor.name} · ${visit.department.name}", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Accent)
                .clickable { onNewVisit() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tr("START A NEW VISIT"), style = MaterialTheme.typography.labelLarge, color = Color.White)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** The dashboard's choice-button look at tile size: grey field, teal icon, bold label. */
@Composable
private fun GridTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val x = LocalExtraColors.current
    Column(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(x.field)
            .then(if (x.isHighContrast) Modifier.border(2.dp, Color.Black, RoundedCornerShape(20.dp)) else Modifier)
            .clickable(role = Role.Button, onClickLabel = title) { onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Accent, modifier = Modifier.size(34.dp))
        Text(text = title.uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ListItemRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Accent)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

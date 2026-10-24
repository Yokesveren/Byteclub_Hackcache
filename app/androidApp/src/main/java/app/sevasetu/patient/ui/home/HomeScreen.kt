package app.sevasetu.patient.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.ui.components.CardShape
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.ScreenBand
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StatusPill
import app.sevasetu.patient.ui.theme.Accent
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.LocalExtraColors
import app.sevasetu.patient.ui.theme.Mint

@Composable
fun HomeScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToHaveSlip: () -> Unit,
    onNavigateToMyTurn: () -> Unit,
    onNavigateToFindPlace: () -> Unit,
    onNavigateToJourney: () -> Unit,
    onNavigateToPreVisit: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDemo: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val app = SevaApplication.instance
    val activeVisit by app.visits.active.collectAsState()

    LaunchedEffect(Unit) {
        val saved = app.visits.load()
        if (saved == null && !app.isLive) {
            // Simulator only: start with the demo patient Kamla, as the standalone app always did.
            runCatching { app.visits.save(app.fakeBackend.loadDemoPatientKamla()) }
        } else if (saved != null) {
            // Refresh the saved ticket; offline, the saved one keeps showing (S8).
            runCatching { app.backendGateway.getMyTurn(saved.visitId) }.getOrNull()?.let { app.visits.save(it) }
        }
    }

    val visit = activeVisit
    SevaScaffold(
        title = "SevaSetu / सेवासेतु",
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ScreenBand(
                title = if (visit != null) "Hello, ${visit.firstName}" else "Namaste",
                tone = "ward",
                lead = "Welcome to ${visit?.hospital?.name ?: "Chinar District Hospital"}"
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (visit != null) {
                // The ticket: mint field, the token in big black capitals, the band chip as on the website.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(Mint)
                        .clickable(onClickLabel = "Open My Turn", role = Role.Button) { onNavigateToMyTurn() }
                        .padding(20.dp)
                ) {
                    Text(text = "YOUR TOKEN", style = MaterialTheme.typography.labelMedium, color = Ink)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = visit.token, style = MaterialTheme.typography.displayLarge, color = Ink)
                        StatusPill(label = visit.bandLabel, type = visit.band, provisional = visit.bandProvisional)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${visit.doctor.name} · ${visit.doctor.room}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink
                    )
                    visit.estimate?.let {
                        Text(text = "People ahead of you: ${it.peopleAhead}", style = MaterialTheme.typography.bodyLarge, color = Ink)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "OPEN MY TURN", style = MaterialTheme.typography.labelLarge, color = Ink)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                GridTile(title = "Register", icon = Icons.Default.PersonAdd, onClick = onNavigateToRegister, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                GridTile(title = "I have my slip", icon = Icons.Default.ConfirmationNumber, onClick = onNavigateToHaveSlip, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                GridTile(title = "My turn", icon = Icons.Default.Schedule, onClick = onNavigateToMyTurn, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                GridTile(title = "Find a place", icon = Icons.Default.Navigation, onClick = onNavigateToFindPlace, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(20.dp))

            InfoCard {
                ListItemRow(title = "Health Journey", icon = Icons.AutoMirrored.Filled.Assignment, onClick = onNavigateToJourney)
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = LocalExtraColors.current.edge)
                ListItemRow(title = "Before you visit", icon = Icons.Default.LocalHospital, onClick = onNavigateToPreVisit)
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = LocalExtraColors.current.edge)
                ListItemRow(title = "Help & FAQs", icon = Icons.AutoMirrored.Filled.Help, onClick = onNavigateToHelp)
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = LocalExtraColors.current.edge)
                ListItemRow(title = "Settings", icon = Icons.Default.Settings, onClick = onNavigateToSettings)
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = LocalExtraColors.current.edge)
                ListItemRow(title = "Demo & Simulation Panel", icon = Icons.Default.Info, onClick = onNavigateToDemo)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (app.isLive) "● Connected to the hospital" else "● Simulator mode (no hospital server set)",
                style = MaterialTheme.typography.labelMedium,
                color = if (app.isLive) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
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
            .height(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(x.field)
            .then(if (x.isHighContrast) Modifier.border(2.dp, Color.Black, RoundedCornerShape(20.dp)) else Modifier)
            .clickable(role = Role.Button, onClickLabel = title) { onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Accent, modifier = Modifier.size(36.dp))
        Text(text = title.uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ListItemRow(
    title: String,
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
        Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

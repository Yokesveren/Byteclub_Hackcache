package app.sevasetu.patient.ui.home

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StatusPill

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
    var activeVisit by remember { mutableStateOf<MyTurnDto?>(null) }

    LaunchedEffect(Unit) {
        try {
            activeVisit = app.fakeBackend.loadDemoPatientKamla()
        } catch (_: Exception) {}
    }

    SevaScaffold(
        title = "SevaSetu / सेवासेतु",
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Hello, Kamla",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Welcome to Chinar District Hospital",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Active Visit Card (if present)
            if (activeVisit != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToMyTurn() },
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
                                text = "Token ${activeVisit!!.token}",
                                style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            StatusPill(label = activeVisit!!.bandLabel, type = activeVisit!!.band)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Doctor: ${activeVisit!!.doctor.name} (${activeVisit!!.doctor.room})",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        val ahead = activeVisit!!.estimate?.peopleAhead ?: 0
                        Text(
                            text = "People ahead of you: $ahead",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Tap to open My Turn",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // 2x2 Grid of Main Buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                GridTile(
                    title = "Register",
                    icon = Icons.Default.PersonAdd,
                    onClick = onNavigateToRegister,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                GridTile(
                    title = "I have my slip",
                    icon = Icons.Default.ConfirmationNumber,
                    onClick = onNavigateToHaveSlip,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                GridTile(
                    title = "My turn",
                    icon = Icons.Default.Schedule,
                    onClick = onNavigateToMyTurn,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                GridTile(
                    title = "Find a place",
                    icon = Icons.Default.Navigation,
                    onClick = onNavigateToFindPlace,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Plain secondary list
            InfoCard {
                ListItemRow(title = "Health Journey", icon = Icons.AutoMirrored.Filled.Assignment, onClick = onNavigateToJourney)
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                ListItemRow(title = "Before you visit", icon = Icons.Default.LocalHospital, onClick = onNavigateToPreVisit)
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                ListItemRow(title = "Help & FAQs", icon = Icons.AutoMirrored.Filled.Help, onClick = onNavigateToHelp)
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                ListItemRow(title = "Settings", icon = Icons.Default.Settings, onClick = onNavigateToSettings)
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                ListItemRow(title = "Demo & Simulation Panel", icon = Icons.Default.Info, onClick = onNavigateToDemo)
            }
        }
    }
}

@Composable
private fun GridTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(130.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
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
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

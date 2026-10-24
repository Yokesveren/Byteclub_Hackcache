package app.sevasetu.patient.ui.info

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.model.DoctorDto
import app.sevasetu.patient.data.model.HospitalDto
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.LastUpdatedText
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.components.StatusPill

@Composable
fun BeforeYouVisitScreen(
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val context = LocalContext.current
    val app = SevaApplication.instance

    var hospital by remember { mutableStateOf<HospitalDto?>(null) }
    var doctors by remember { mutableStateOf<List<DoctorDto>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            hospital = app.backendGateway.listHospitals().firstOrNull()
            doctors = app.backendGateway.listDoctors()
        } catch (_: Exception) {}
    }

    SevaScaffold(
        title = "Before You Visit",
        onBack = onBack,
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "Hospital Information & Service Status", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(12.dp))

            hospital?.let { hosp ->
                InfoCard {
                    Text(text = hosp.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = hosp.address, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${hosp.lat},${hosp.lng}"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in Google Maps", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Facilities Status
                InfoCard {
                    Text(text = "Facility Service Status", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Laboratory (Sample collection)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        StatusPill(label = "UP / चालू", type = "green")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "X-Ray Machine", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        StatusPill(label = "DOWN / बंद", type = "red")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Doctor Availability
                InfoCard {
                    Text(text = "Is My Doctor In?", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(12.dp))

                    doctors.forEach { doc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = doc.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                Text(text = "${doc.room} • Floor ${doc.floor}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(
                                label = if (doc.status == "in") "IN / उपस्थित" else "AWAY / अनुपस्थित",
                                type = if (doc.status == "in") "green" else "yellow"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                LastUpdatedText(time = "10:30")
            }
        }
    }
}

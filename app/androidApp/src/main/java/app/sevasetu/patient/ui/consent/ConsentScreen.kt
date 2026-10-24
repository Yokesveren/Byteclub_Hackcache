package app.sevasetu.patient.ui.consent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.data.model.ConsentDto
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.SevaScaffold

@Composable
fun ConsentScreen(
    onContinue: (ConsentDto) -> Unit,
    onEmergencyClick: () -> Unit,
    onBack: () -> Unit
) {
    var termsAccepted by remember { mutableStateOf(true) }
    var movementAccepted by remember { mutableStateOf(true) }

    SevaScaffold(
        title = "Privacy & Consent",
        onBack = onBack,
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                text = "Your Privacy & Data Protection",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SevaSetu is designed to protect your identity. No Aadhaar or real health records are shared.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            InfoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Terms of Service (Required)",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Allows SevaSetu to generate your OPD slip and manage your queue place locally.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Switch(
                        checked = termsAccepted,
                        onCheckedChange = { termsAccepted = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            InfoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Indoor Navigation Tracking (Optional)",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Uses anonymous wall sticker scans to guide you to your room and display hospital crowd maps. No name or ID is stored.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Switch(
                        checked = movementAccepted,
                        onCheckedChange = { movementAccepted = it }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            BigButton(
                text = "Continue",
                enabled = termsAccepted,
                onClick = {
                    onContinue(ConsentDto(terms = termsAccepted, movement = movementAccepted))
                }
            )
        }
    }
}

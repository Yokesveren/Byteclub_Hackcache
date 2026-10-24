package app.sevasetu.patient.ui.slip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.SevaScaffold
import kotlinx.coroutines.launch

@Composable
fun IHaveMySlipScreen(
    onSlipLinked: (MyTurnDto) -> Unit,
    onOpenScanner: () -> Unit,
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val app = SevaApplication.instance
    val scope = rememberCoroutineScope()

    var tokenInput by remember { mutableStateOf("M-017") }
    var errorMessage by remember { mutableStateOf("") }
    var isLinking by remember { mutableStateOf(false) }

    SevaScaffold(
        title = "I Have My Slip",
        onBack = onBack,
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "Link Existing OPD Slip", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter your token number or scan the QR code on your paper slip.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            InfoCard {
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it; errorMessage = "" },
                    label = { Text("Token Number (e.g. M-017)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                BigButton(
                    text = if (isLinking) "Searching..." else "Link OPD Token",
                    onClick = {
                        isLinking = true
                        scope.launch {
                            val result = app.backendGateway.linkSlip("H01", tokenInput.trim())
                            isLinking = false
                            if (result != null) {
                                onSlipLinked(result)
                            } else {
                                errorMessage = "We could not find this token today. Please check the number or visit the help desk."
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            BigButton(
                text = "Scan Slip QR Code",
                onClick = onOpenScanner,
                type = ButtonType.SECONDARY
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Seeded Demo Tokens
            InfoCard {
                Text(text = "Demo Seeded Tokens for Testing", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                listOf("M-017", "M-011", "P-012", "A-013", "O-014").forEach { seed ->
                    OutlinedButton(
                        onClick = { tokenInput = seed },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(text = "Use Seeded Token: $seed", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

package app.sevasetu.patient.ui.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ButtonType
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.SevaScaffold

@Composable
fun HelpCardsScreen(
    onBack: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    SevaScaffold(
        title = "Help & FAQs",
        onBack = onBack,
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "Frequently Asked Questions", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))

            InfoCard {
                Text(text = "What documents should I bring?", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Please bring any previous hospital prescriptions, test reports, and an ID proof if available. ABHA number is optional.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            InfoCard {
                Text(text = "How does the OPD token work?", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Your token shows your place in the doctor's queue. Priority (yellow) patients are placed ahead of Routine patients.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            InfoCard {
                Text(text = "Where is the pharmacy & lab located?", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pharmacy and Laboratory are on the Ground Floor along the main corridor. Follow the indoor map in 'Find a place'.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            BigButton(
                text = "Have Chest Pain or Severe Symptoms? Press Emergency",
                onClick = onEmergencyClick,
                type = ButtonType.DANGER
            )
        }
    }
}

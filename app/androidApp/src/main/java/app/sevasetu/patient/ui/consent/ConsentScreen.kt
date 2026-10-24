package app.sevasetu.patient.ui.consent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.model.ConsentDto
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.InfoCard
import app.sevasetu.patient.ui.components.ScreenBand
import app.sevasetu.patient.ui.components.SevaScaffold

/** Consent (PRD 8.1, S9): plain words, the required part and the optional part kept apart. Saved on the phone. */
@Composable
fun ConsentScreen(
    onContinue: (ConsentDto) -> Unit,
    onEmergencyClick: () -> Unit,
    onBack: () -> Unit
) {
    val app = SevaApplication.instance
    var terms by rememberSaveable { mutableStateOf(false) }
    var movement by rememberSaveable { mutableStateOf(app.prefs.current.movementConsent) }

    SevaScaffold(
        title = "Your privacy",
        onBack = onBack,
        onListen = {
            app.speaker.speak(
                "SevaSetu keeps your details on this phone and shares only what the hospital needs for your token. " +
                    "Sharing which stickers you scan is optional and anonymous.",
                "सेवासेतु आपकी जानकारी इसी फ़ोन पर रखता है और अस्पताल को सिर्फ़ टोकन के लिए ज़रूरी जानकारी देता है। " +
                    "स्कैन किए गए स्टिकर साझा करना वैकल्पिक और गुमनाम है।"
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
            ScreenBand(
                title = "Your details stay yours",
                tone = "ward",
                lead = "No Aadhaar. First name only. Your slip shows your token, first name and department, nothing about your health."
            )
            Spacer(modifier = Modifier.height(16.dp))

            ConsentRow(
                title = "Use my details for my token",
                required = true,
                text = "The hospital gets your first name, age group, the help you need and a short note for the doctor. Needed to register.",
                checked = terms,
                onChecked = { terms = it }
            )
            Spacer(modifier = Modifier.height(12.dp))
            ConsentRow(
                title = "Share the stickers I scan",
                required = false,
                text = "Helps the hospital see crowded corridors. Sent under a random code, never your name or token. Directions work either way.",
                checked = movement,
                onChecked = { movement = it }
            )

            Spacer(modifier = Modifier.height(20.dp))
            BigButton(
                text = if (terms) "Agree and continue" else "Turn on the first switch to continue",
                enabled = terms,
                icon = if (terms) Icons.AutoMirrored.Filled.ArrowForward else null,
                onClick = {
                    app.prefs.update { it.copy(movementConsent = movement, onboarded = true) }
                    onContinue(ConsentDto(terms = true, movement = movement))
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "You can change the second choice any time in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConsentRow(title: String, required: Boolean, text: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (required) "REQUIRED" else "OPTIONAL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChecked)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

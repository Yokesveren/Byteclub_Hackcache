package app.sevasetu.patient.ui.language

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.sevasetu.patient.SevaApplication
import app.sevasetu.patient.data.prefs.LANGUAGES
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.ScreenBand
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.theme.Accent
import app.sevasetu.patient.ui.theme.Ink
import app.sevasetu.patient.ui.theme.LocalExtraColors
import app.sevasetu.patient.ui.theme.Mint

private val GREETING = mapOf(
    "ks" to Triple("Welcome to SevaSetu.", null, "سیواسیتو میں خوش آمدید۔"),
    "ur" to Triple("Welcome to SevaSetu.", null, "سیواسیتو میں خوش آمدید۔"),
    "doi" to Triple("Welcome to SevaSetu.", "सेवासेतु में आपका स्वागत है।", null),
    "hi" to Triple("Welcome to SevaSetu.", "सेवासेतु में आपका स्वागत है।", null),
    "en" to Triple("Welcome to SevaSetu.", null, null),
)

/** First launch (PRD 8.1): the five languages of Jammu and Kashmir, each in its own script, with spoken help. */
@Composable
fun LanguageScreen(
    onContinue: (selectedLang: String) -> Unit,
    onEmergencyClick: () -> Unit
) {
    val app = SevaApplication.instance
    var selected by rememberSaveable { mutableStateOf(app.prefs.current.language) }

    SevaScaffold(
        title = "SevaSetu",
        onListen = { app.speaker.speak("Choose your language.", "अपनी भाषा चुनें।", "اپنی زبان منتخب کریں۔") },
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ScreenBand(
                title = "Choose your language",
                tone = "mint",
                lead = "अपनी भाषा चुनें · اپنی زبان منتخب کریں"
            )
            Spacer(modifier = Modifier.height(16.dp))

            LANGUAGES.forEach { lang ->
                val on = selected == lang.code
                val x = LocalExtraColors.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) x.frame else x.field)
                        .then(if (x.isHighContrast) Modifier.border(2.dp, Color.Black, RoundedCornerShape(18.dp)) else Modifier)
                        .clickable(onClickLabel = lang.english) {
                            selected = lang.code
                            app.prefs.update { it.copy(language = lang.code) }
                            GREETING[lang.code]?.let { (en, hi, ur) -> app.speaker.speak(en, hi, ur) }
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lang.native,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (on) Color.White else Ink
                        )
                        if (lang.native != lang.english) {
                            Text(
                                text = lang.english,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (on) Mint else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (on) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = Mint, modifier = Modifier.size(30.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Screens are in English with Hindi help for now. Listen reads them aloud in your language where a voice is installed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            BigButton(
                text = "Continue",
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                onClick = { onContinue(selected) }
            )
        }
    }
}

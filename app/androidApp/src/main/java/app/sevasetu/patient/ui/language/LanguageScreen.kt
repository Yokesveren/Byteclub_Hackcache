package app.sevasetu.patient.ui.language

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sevasetu.patient.ui.components.BigButton
import app.sevasetu.patient.ui.components.SevaScaffold
import java.util.Locale

fun backendLanguage(appLanguage: String): String {
    return when (appLanguage.lowercase()) {
        "hi", "hindi" -> "hi"
        "ta", "tamil" -> "en"
        else -> "en"
    }
}

@Composable
fun LanguageScreen(
    onContinue: (selectedLang: String) -> Unit,
    onEmergencyClick: () -> Unit
) {
    val context = LocalContext.current
    var selectedLang by remember { mutableStateOf("hi") }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    DisposableEffect(context) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.language = Locale("hi", "IN")
            }
        }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    fun speakGreeting(lang: String) {
        val text = when (lang) {
            "hi" -> "नमस्ते, सेवासेतु में आपका स्वागत है।"
            "ta" -> "வணக்கம், சேவாசேதுவிற்கு வரவேற்கிறோம்."
            else -> "Welcome to SevaSetu."
        }
        val locale = when (lang) {
            "hi" -> Locale("hi", "IN")
            "ta" -> Locale("ta", "IN")
            else -> Locale.ENGLISH
        }
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "greeting")
    }

    SevaScaffold(
        title = "SevaSetu",
        onEmergencyClick = onEmergencyClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Choose Language",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Text(
                            text = "भाषा चुनें / மொழியைத் தேர்ந்தெடுக்கவும்",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                LanguageCard(
                    title = "हिन्दी",
                    subtitle = "Hindi",
                    isSelected = selectedLang == "hi",
                    onClick = {
                        selectedLang = "hi"
                        speakGreeting("hi")
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                LanguageCard(
                    title = "தமிழ்",
                    subtitle = "Tamil",
                    isSelected = selectedLang == "ta",
                    onClick = {
                        selectedLang = "ta"
                        speakGreeting("ta")
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                LanguageCard(
                    title = "English",
                    subtitle = "English",
                    isSelected = selectedLang == "en",
                    onClick = {
                        selectedLang = "en"
                        speakGreeting("en")
                    }
                )
            }

            BigButton(
                text = "Continue / आगे बढ़ें",
                onClick = { onContinue(selectedLang) }
            )
        }
    }
}

@Composable
private fun LanguageCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

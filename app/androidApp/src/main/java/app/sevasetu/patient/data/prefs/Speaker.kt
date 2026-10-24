package app.sevasetu.patient.data.prefs

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * The Listen button (PRD 8.8): one text-to-speech engine for the whole app.
 * Screens pass what to say in English and, where written, in Hindi or Urdu; the patient's language picks the line
 * and the voice. Kashmiri falls back to Urdu and Dogri to Hindi, the nearest voices on most phones.
 */
class Speaker(context: Context, private val prefs: Prefs) {

    private var ready = false
    private val tts = TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }

    fun speak(en: String, hi: String? = null, ur: String? = null) {
        if (!ready) return
        val (text, locale) = when (prefs.current.language) {
            "hi", "doi" -> if (hi != null) hi to Locale("hi", "IN") else en to Locale("en", "IN")
            "ur", "ks" -> when {
                ur != null -> ur to Locale("ur", "IN")
                hi != null -> hi to Locale("hi", "IN")
                else -> en to Locale("en", "IN")
            }
            else -> en to Locale("en", "IN")
        }
        val ok = tts.setLanguage(locale)
        if (ok == TextToSpeech.LANG_MISSING_DATA || ok == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale("en", "IN"))
            tts.speak(en, TextToSpeech.QUEUE_FLUSH, null, "seva")
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "seva")
        }
    }

    fun stop() {
        if (ready) tts.stop()
    }
}

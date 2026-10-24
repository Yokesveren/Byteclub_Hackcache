package app.sevasetu.patient.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** What the patient chose on this phone. Read synchronously at start so the app opens on the right screen. */
data class Settings(
    val language: String = "en",          // ks, ur, doi, hi, en (contract enum)
    val onboarded: Boolean = false,       // language and consent done
    val movementConsent: Boolean = false, // S9: scan events only with this
    val textScale: Float = 1.0f,
    val highContrast: Boolean = false,
    val avoidStairs: Boolean = false,
    val alertSound: Boolean = true,
    val alertVibration: Boolean = true,
    val flashOnCall: Boolean = false,
    val hospitalId: String = "H01",
    val patientId: String = "",           // reused for the same person, so the Health Journey builds up
)

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("seva_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    private fun read(): Settings {
        val d = Settings()
        val patientId = sp.getString("patientId", null) ?: UUID.randomUUID().toString().also {
            sp.edit().putString("patientId", it).apply()
        }
        return Settings(
            language = sp.getString("language", d.language)!!,
            onboarded = sp.getBoolean("onboarded", d.onboarded),
            movementConsent = sp.getBoolean("movementConsent", d.movementConsent),
            textScale = sp.getFloat("textScale", d.textScale),
            highContrast = sp.getBoolean("highContrast", d.highContrast),
            avoidStairs = sp.getBoolean("avoidStairs", d.avoidStairs),
            alertSound = sp.getBoolean("alertSound", d.alertSound),
            alertVibration = sp.getBoolean("alertVibration", d.alertVibration),
            flashOnCall = sp.getBoolean("flashOnCall", d.flashOnCall),
            hospitalId = sp.getString("hospitalId", d.hospitalId)!!,
            patientId = patientId,
        )
    }

    fun update(change: (Settings) -> Settings) {
        val s = change(_settings.value)
        sp.edit()
            .putString("language", s.language)
            .putBoolean("onboarded", s.onboarded)
            .putBoolean("movementConsent", s.movementConsent)
            .putFloat("textScale", s.textScale)
            .putBoolean("highContrast", s.highContrast)
            .putBoolean("avoidStairs", s.avoidStairs)
            .putBoolean("alertSound", s.alertSound)
            .putBoolean("alertVibration", s.alertVibration)
            .putBoolean("flashOnCall", s.flashOnCall)
            .putString("hospitalId", s.hospitalId)
            .putString("patientId", s.patientId)
            .apply()
        _settings.value = s
    }
}

/** The five languages of the PRD (contract enum). Kashmiri and Dogri speech fall back to the nearest installed voice. */
data class Language(val code: String, val native: String, val english: String, val rtl: Boolean = false)

val LANGUAGES = listOf(
    Language("ks", "کٲشُر", "Kashmiri", rtl = true),
    Language("ur", "اردو", "Urdu", rtl = true),
    Language("doi", "डोगरी", "Dogri"),
    Language("hi", "हिन्दी", "Hindi"),
    Language("en", "English", "English"),
)

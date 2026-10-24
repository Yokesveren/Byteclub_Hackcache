package app.sevasetu.patient.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** The patient's language code (ks, ur, doi, hi, en), provided at the top of the app. */
val LocalLang = staticCompositionLocalOf { "en" }

/**
 * Screen text in the patient's language. Keys are the English text itself, so a missing translation shows English,
 * never a blank. `{name}` placeholders are filled from [args]. Kashmiri is shown in Urdu and Dogri in Hindi until
 * native-speaker translations exist (see app/TRANSLATION_REVIEW.md).
 */
object I18n {
    /** The script the text is shown in for a language. */
    fun base(lang: String): String = when (lang) {
        "ur", "ks" -> "ur"
        "hi", "doi" -> "hi"
        else -> "en"
    }

    fun isRtl(lang: String) = base(lang) == "ur"

    fun has(lang: String, en: String): Boolean = base(lang) == "en" || STRINGS[en]?.get(base(lang)) != null

    fun t(lang: String, en: String, vararg args: Pair<String, Any?>): String {
        val b = base(lang)
        var s = if (b == "en") en else STRINGS[en]?.get(b) ?: en
        args.forEach { (k, v) -> s = s.replace("{$k}", v?.toString() ?: "") }
        return s
    }
}

/** Translate [en] (a key of [STRINGS], or any text) into the current language. */
@Composable
@ReadOnlyComposable
fun tr(en: String, vararg args: Pair<String, Any?>): String = I18n.t(LocalLang.current, en, *args)

/** For text made outside composition (click handlers, notifications): the language saved in settings. */
fun trNow(en: String, vararg args: Pair<String, Any?>): String =
    I18n.t(app.sevasetu.patient.SevaApplication.instance.prefs.current.language, en, *args)

/** "ground floor" / "floor 1" in the current language. */
@Composable
@ReadOnlyComposable
fun floorLabel(floor: Int): String = if (floor == 0) tr("ground floor") else tr("floor {n}", "n" to floor)

/** A department's name in the current language, from the hospital's own translations. */
@Composable
@ReadOnlyComposable
fun deptName(d: app.sevasetu.patient.data.model.DepartmentDto): String = d.nameI18n[I18n.base(LocalLang.current)] ?: d.name

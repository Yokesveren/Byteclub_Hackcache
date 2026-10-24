package app.sevasetu.patient

import app.sevasetu.patient.ui.i18n.I18n
import app.sevasetu.patient.ui.i18n.STRINGS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class I18nTest {

    @Test
    fun englishPassesThroughAndFillsPlaceholders() {
        assertEquals("3 ahead of you", I18n.t("en", "{n} ahead of you", "n" to 3))
    }

    @Test
    fun hindiAndUrduAreUsedAndDogriKashmiriFallBack() {
        assertEquals("आपसे पहले 3", I18n.t("hi", "{n} ahead of you", "n" to 3))
        assertEquals(I18n.t("hi", "Register"), I18n.t("doi", "Register"))
        assertEquals(I18n.t("ur", "Register"), I18n.t("ks", "Register"))
        assertTrue(I18n.isRtl("ks") && I18n.isRtl("ur") && !I18n.isRtl("doi"))
    }

    @Test
    fun missingTranslationShowsEnglishNeverBlank() {
        assertEquals("Something nobody translated", I18n.t("hi", "Something nobody translated"))
    }

    @Test
    fun everyTranslationKeepsItsPlaceholders() {
        val ph = Regex("""\{\w+\}""")
        STRINGS.forEach { (en, tr) ->
            val want = ph.findAll(en).map { it.value }.sorted().toList()
            tr.forEach { (lang, text) ->
                assertEquals("$lang: $en", want, ph.findAll(text).map { it.value }.sorted().toList())
                assertTrue("$lang blank: $en", text.isNotBlank())
            }
        }
    }
}

package app.ominira.core

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinesTest {

    private val letGo = mapOf(
        Lang.En to "let go",
        Lang.Pcm to "leave am",
        Lang.Yo to "fi sílẹ̀",
        Lang.Ha to "ku sake",
        Lang.Ig to "hapụ",
    )

    @Test
    fun holdingSaysLetGoNotTap() {
        for (lang in Lang.entries) {
            val hold = Lines.practiceResult(lang, 7, hold = true)
            val tap = Lines.practiceResult(lang, 7, hold = false)
            assertNotEquals(lang.code, tap, hold)
            assertTrue("$lang: $hold", hold.contains(letGo.getValue(lang)))
        }
    }

    @Test
    fun holdingSaysToWaitForZeroFirst() {
        val zero = mapOf(Lang.En to "zero", Lang.Pcm to "zero", Lang.Yo to "òdo", Lang.Ha to "sifili", Lang.Ig to "efu")
        for (lang in Lang.entries) {
            val short = Lines.pulseShort(lang, hold = true)
            val word = zero.getValue(lang)
            assertTrue("$lang: $short", short.indexOf(word) in 0 until short.indexOf(letGo.getValue(lang)))
            assertTrue(lang.code, Lines.holdTooSoon(lang).isNotBlank())
        }
    }
}

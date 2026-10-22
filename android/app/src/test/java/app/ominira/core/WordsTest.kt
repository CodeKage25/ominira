package app.ominira.core

import app.ominira.bank.FakeOminiraApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WordsTest {

    // Every line with no arguments, by name.
    private fun lines(lang: Lang): Map<String, String> {
        val w = Words.of(lang)
        return Words::class.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.declaringClass == Words::class.java }
            .mapNotNull { m ->
                when (val v = m.invoke(w)) {
                    is String -> m.name to v
                    is List<*> -> m.name to v.joinToString(" ")
                    else -> null
                }
            }
            .toMap()
    }

    @Test
    fun everyLineIsFilledInEveryLanguage() {
        for (lang in Lang.entries) {
            val blank = lines(lang).filterValues { it.isBlank() }.keys
            assertTrue("$lang has blank lines: $blank", blank.isEmpty())
        }
    }

    @Test
    fun yorubaHausaAndIgboAreNotLeftInEnglish() {
        val english = lines(Lang.En)
        for (lang in listOf(Lang.Yo, Lang.Ha, Lang.Ig)) {
            val same = lines(lang).filter { (name, text) -> text == english[name] }.keys
            // Brand words only: Pulse PIN, BVN and the like stay as they are.
            assertTrue("$lang still English: $same", same.size <= 3)
        }
    }

    @Test
    fun theBankInsideTheAppAnswersInTheCustomersLanguage() = runTest {
        val bank = FakeOminiraApi(latency = 0, lang = Lang.Yo)
        val draft = (bank.draftTransfer("py_tolu", 2_000_000) as Drafted.Made).draft
        assertEquals(listOf("₦20,000.", "Sí Adebayo Tolulope.", "Zenith Bank, tí ó parí sí 4 4 7 1.", "Ẹ pulse nọ́ńbà àṣírí yín láti fi ránṣẹ́."), draft.readback.spoken)

        val sent = bank.confirm(draft.id, draft.readbackHash, PinBlock.seal(listOf(2, 0, 7, 5)), callActive = false)
        assertEquals("Ó ti lọ. Tolu ti gbà á.", sent.say)

        bank.savePreferences(PreferencePatch(lang = Lang.Ha))
        assertEquals("Kuna da ₦822,300.", bank.balance("ac_1").say)
    }

    @Test
    fun theWordsThePromptsAskForAreUnderstood() {
        assertTrue(Answers.isStart("bẹ̀rẹ̀"))
        assertTrue(Answers.isStart("fara"))
        assertTrue(Answers.isStop("dúró"))
        assertTrue(Answers.isStop("kwụsị"))
        assertTrue(Answers.isCarryOn("tẹ̀síwájú"))
        assertTrue(Answers.isCarryOn("ci gaba"))
        assertTrue(Answers.isCarryOn("gaa n’ihu"))
        assertTrue(Answers.isAgain("sake"))
    }

    @Test
    fun daysAreNamedInTheLanguage() {
        val today = LocalDate.of(2026, 10, 21)
        val noon = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toString()
        assertEquals("Òní", Days.label(noon, ZoneOffset.UTC, today, Lang.Yo))
        assertEquals("Jiya", Days.label(today.minusDays(1).atTime(12, 0).toInstant(ZoneOffset.UTC).toString(), ZoneOffset.UTC, today, Lang.Ha))
        assertEquals("Today", Days.label(noon, ZoneOffset.UTC, today))
    }

    @Test
    fun pinIsSaidInEachLanguagesOwnWords() {
        assertEquals("Ẹ pulse nọ́ńbà àṣírí yín.", PinTerm.local(Lang.Yo, "Ẹ pulse PIN yín."))
        assertEquals("Na gaba, lambar sirrinku ta Pulse.", PinTerm.local(Lang.Ha, "Na gaba, Pulse PIN ɗinku."))
        assertEquals("Ku duba lambar sirrina.", PinTerm.local(Lang.Ha, "Ku duba PIN ɗina."))
        assertEquals("Nke ọzọ, nọmba nzuzo Pulse gị.", PinTerm.local(Lang.Ig, "Nke ọzọ, Pulse PIN gị."))
        assertEquals("Your Pulse PIN.", PinTerm.local(Lang.Pcm, "Your Pulse PIN."))
        // Every Yorùbá, Hausa and Igbo line has lost the English word.
        for (lang in listOf(Lang.Yo, Lang.Ha, Lang.Ig)) {
            val left = lines(lang).filterValues { Regex("\\bPIN\\b").containsMatchIn(it) }.keys
            assertTrue("$lang still says PIN: $left", left.isEmpty())
        }
    }

    @Test
    fun aVoiceSpellsWhatTheScreenShows() {
        assertEquals("N.I.N. ending 5521. Your B.V.N. matches.", VoiceText.of("NIN ending 5521. Your BVN matches."))
        assertEquals("Pinned", VoiceText.of("Pinned"))
    }
}

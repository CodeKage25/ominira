package app.ominira.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswersTest {

    @Test
    fun yesInEveryLanguage() {
        for (yes in listOf("Yes", "yes please", "Okay", "Na so", "Ehen", "Oya", "Bẹ́ẹ̀ni", "beeni", "Eh", "Toh", "Ee", "That's right")) {
            assertTrue(yes, Answers.isYes(yes))
        }
    }

    @Test
    fun noInEveryLanguage() {
        for (no in listOf("No", "Nope", "Rárá", "Bẹ́ẹ̀kọ́", "A'a", "A’a", "Mba", "No, I'd rather read it myself", "Read and tap")) {
            assertTrue(no, Answers.isNo(no))
            assertFalse(no, Answers.isYes(no))
        }
    }

    @Test
    fun ordinaryWordsAreNotYes() {
        assertFalse(Answers.isYes("I want to read it"))
        assertFalse(Answers.isYes("Send money to Tolu"))
    }

    @Test
    fun startAndAgain() {
        assertTrue(Answers.isStart("Start"))
        assertTrue(Answers.isStart("I'm ready"))
        assertTrue(Answers.isStart("yes"))
        assertFalse(Answers.isStart("No, not now"))
        assertTrue(Answers.isAgain("Say it again"))
        assertTrue(Answers.isAgain("Repeat"))
        assertFalse(Answers.isAgain("Yes"))
    }

    @Test
    fun scamPauseAnswers() {
        assertTrue(Answers.isStop("Stop"))
        assertTrue(Answers.isStop("Yes, someone told me to"))
        assertTrue(Answers.isStop("cancel it"))
        assertFalse(Answers.isCarryOn("Stop"))
        assertTrue(Answers.isCarryOn("Carry on"))
        assertTrue(Answers.isCarryOn("No, I chose this myself"))
        assertTrue(Answers.isCarryOn("No"))
        assertFalse(Answers.isStop("Carry on"))
    }

    @Test
    fun languageNamesInAnyLanguageAndSpelling() {
        val cases = mapOf(
            "English" to Lang.En,
            "english please" to Lang.En,
            "Turanci" to Lang.En,
            "Bekee" to Lang.En,
            "Gẹ̀ẹ́sì" to Lang.En,
            "geesi" to Lang.En,
            "Pidgin" to Lang.Pcm,
            "pijin" to Lang.Pcm,
            "broken English" to Lang.Pcm,
            "Yorùbá" to Lang.Yo,
            "yoruba" to Lang.Yo,
            "Èdè Yorùbá" to Lang.Yo,
            "Hausa" to Lang.Ha,
            "Igbo" to Lang.Ig,
            "Ìgbò" to Lang.Ig,
            "ibo" to Lang.Ig,
        )
        for ((said, lang) in cases) assertEquals(said, lang, Answers.language(said))
        assertNull(Answers.language("I don't know"))
        assertNull(Answers.language(""))
    }

    @Test
    fun digitsHoweverTheyAreSaid() {
        assertEquals("2234", Answers.digits("2 2 3 4"))
        assertEquals("2234", Answers.digits("two two three four"))
        assertEquals("2234", Answers.digits("double two three four"))
        assertEquals("2234", Answers.digits("22-34"))
        assertEquals("22212345678", Answers.digits("two two two one two three four five six seven eight"))
        assertEquals("1110", Answers.digits("triple one oh"))
        assertEquals("", Answers.digits("hello there"))
    }

    @Test
    fun aSpokenPinIsNoticed() {
        assertTrue(Answers.soundsLikePin("my PIN is 2075"))
        assertTrue(Answers.soundsLikePin("my pin is two zero seven five"))
        assertFalse(Answers.soundsLikePin("send 2075 to Tolu"))
    }
}

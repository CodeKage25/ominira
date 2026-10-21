package app.ominira.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingTest {

    @Test
    fun oneDirectionAtATimeMostImportantFirst() {
        assertEquals(Aim.Dark, Framing.face(Seen(0.1f, 0.1f, 0.1f), luma = 20))
        assertEquals(Aim.Search, Framing.face(null, luma = 120))
        // Too small and off to the left: distance first.
        assertEquals(Aim.Closer, Framing.face(Seen(0.2f, 0.5f, 0.15f), luma = 120))
        assertEquals(Aim.Left, Framing.face(Seen(0.2f, 0.5f, 0.4f), luma = 120))
        assertEquals(Aim.Down, Framing.face(Seen(0.5f, 0.8f, 0.4f), luma = 120))
        assertEquals(Aim.Straight, Framing.face(Seen(0.5f, 0.5f, 0.4f, turnDeg = 30f), luma = 120))
        assertEquals(Aim.Hold, Framing.face(Seen(0.52f, 0.47f, 0.4f, turnDeg = 5f), luma = 120))
        assertEquals(Aim.Farther, Framing.card(Seen(0.5f, 0.5f, 0.99f), luma = 120))
    }

    @Test
    fun closenessRisesAsTheFaceSettles() {
        val far = Framing.closeness(Seen(0.15f, 0.2f, 0.1f), 0.28f, 0.7f)
        val near = Framing.closeness(Seen(0.45f, 0.5f, 0.35f), 0.28f, 0.7f)
        assertTrue(near > far)
        assertEquals(1f, Framing.closeness(Seen(0.5f, 0.5f, 0.4f), 0.28f, 0.7f))
        assertEquals(0f, Framing.closeness(null, 0.28f, 0.7f))
    }

    @Test
    fun speaksOnlyOnceADirectionHolds() {
        val steady = Steady(sayAfterMs = 900, lockAfterMs = 1_200)
        assertNull(steady.feed(Aim.Left, 0))
        assertNull(steady.feed(Aim.Left, 500))
        assertEquals(Aim.Left, steady.feed(Aim.Left, 950))
        // Said once, not again while it stays the same, until it has been quiet a while.
        assertNull(steady.feed(Aim.Left, 2_000))
        assertEquals(Aim.Left, steady.feed(Aim.Left, 7_000))
        assertNull(steady.feed(Aim.Hold, 7_050))
        assertFalse(steady.locked(7_500))
        assertEquals(Aim.Hold, steady.feed(Aim.Hold, 8_000))
        assertTrue(steady.locked(8_300))
    }

    @Test
    fun readsANinSlipKeepingOnlyTheLastFourDigits() {
        val slip = """
            NATIONAL IDENTITY MANAGEMENT COMMISSION
            Surname: ADEYEMI
            First Name: RONKE
            NIN: 1234 5678 551
            Date of Birth: 14 MAR 1961
        """.trimIndent()
        val id = IdCardText.read(slip)!!
        assertEquals("Ronke Adeyemi", id.fullName)
        assertEquals("8551", id.ninLast4)
        assertEquals("1961-03-14", id.bornOn)
    }

    @Test
    fun readsACardWithValuesOnTheNextLine() {
        val card = """
            SURNAME/NOM
            IBRAHIM
            GIVEN NAMES/PRENOMS
            MUSA BELLO
            14/03/1961
            NIN 12345678901
        """.trimIndent()
        val id = IdCardText.read(card)!!
        assertEquals("Musa Bello Ibrahim", id.fullName)
        assertEquals("8901", id.ninLast4)
        assertEquals("1961-03-14", id.bornOn)
    }

    @Test
    fun halfACardIsNotReadBack() {
        assertNull(IdCardText.read("Surname: ADEYEMI\nFirst Name: RONKE"))
        assertNull(IdCardText.ninLast4("call 0803 123 45"))
    }
}

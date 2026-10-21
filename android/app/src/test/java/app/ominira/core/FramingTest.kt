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

    // Lines stacked down one column, 30 px apart, the way a plain slip reads.
    private fun column(vararg texts: String, left: Int = 100): List<CardLine> =
        texts.mapIndexed { i, t -> CardLine(t, left, 100 + i * 30, left + 400, 120 + i * 30) }

    @Test
    fun readsANinSlipKeepingOnlyTheLastFourDigits() {
        val slip = column(
            "NATIONAL IDENTITY MANAGEMENT COMMISSION",
            "Surname: ADEYEMI",
            "First Name: RONKE",
            "NIN: 1234 5678 551",
            "Date of Birth: 14 MAR 1961",
        )
        val id = IdCardText.read(slip)!!
        assertEquals("Ronke Adeyemi", id.fullName)
        assertEquals("8551", id.ninLast4)
        assertEquals("1961-03-14", id.bornOn)
    }

    // The digital NIN slip: labels with their French halves, values under them, the issue date beside.
    @Test
    fun readsTheDigitalSlipByPositionNotByOrder() {
        val card = listOf(
            CardLine("SURNAME/NOM", 200, 100, 330, 115),
            CardLine("GIVEN NAMES/PRENOMS", 200, 150, 400, 165),
            // Comes next in the list, as the camera's blocks often do, but sits off to the right.
            CardLine("SONGA", 620, 158, 700, 175),
            CardLine("PROUD, NIGERIAN", 200, 170, 380, 190),
            CardLine("CITIZEN", 200, 120, 300, 140),
            CardLine("DATE OF BIRTH SEX/SEXE", 200, 200, 420, 215),
            CardLine("01 OCT 1960 M", 200, 220, 340, 240),
            CardLine("ISSUE DATE", 600, 220, 700, 235),
            CardLine("20 JUL 2021", 600, 240, 700, 258),
            CardLine("1234 567 8910", 80, 300, 600, 340),
        )
        val id = IdCardText.read(card)!!
        assertEquals("Proud Nigerian Citizen", id.fullName)
        assertEquals("8910", id.ninLast4)
        // The earliest date is the birth date, not the issue date.
        assertEquals("1960-10-01", id.bornOn)
    }

    @Test
    fun halfACardIsNotReadBack() {
        assertNull(IdCardText.read(column("Surname: ADEYEMI", "First Name: RONKE")))
        assertNull(IdCardText.ninLast4("call 0803 123 45"))
    }
}

package app.ominira.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NairaAndAmountTest {

    @Test
    fun formatsKoboAsWholeNaira() {
        assertEquals("₦842,300", Naira.format(84_230_000))
        assertEquals("₦20,000", Naira.format(2_000_000))
        assertEquals("₦150,000", Naira.format(15_000_000))
        assertEquals("₦1,000,000", Naira.format(100_000_000))
        assertEquals("₦0", Naira.format(0))
        assertEquals("₦5", Naira.format(550))
    }

    @Test
    fun saysNumbersTheWayBanksReadThem() {
        assertEquals("zero", Naira.words(0))
        assertEquals("twenty-one", Naira.words(21))
        assertEquals("one hundred", Naira.words(100))
        assertEquals("twenty thousand", Naira.words(20_000))
        assertEquals("one thousand and fifty", Naira.words(1_050))
        assertEquals("one hundred and fifty thousand", Naira.words(150_000))
        assertEquals("eight hundred and forty-two thousand, three hundred", Naira.words(842_300))
        assertEquals("one million, two hundred thousand", Naira.words(1_200_000))
        assertEquals("one hundred and eighty-five thousand", Naira.words(185_000))
    }

    @Test
    fun spokenAmountsAndDigits() {
        assertEquals("twenty thousand naira", Naira.spoken(2_000_000))
        assertEquals("Twenty thousand naira", Naira.spokenSentence(2_000_000))
        assertEquals("four, four, seven, one", Naira.digitWords("4471"))
        assertEquals("eight, eight, three, zero", Naira.digitWords("8830"))
    }

    @Test
    fun readsAmountsFromSpeechAndTyping() {
        assertEquals(2_000_000L, SpokenAmount.parseKobo("Send twenty thousand to Tolu"))
        assertEquals(2_000_000L, SpokenAmount.parseKobo("twenty thousand"))
        assertEquals(2_000_000L, SpokenAmount.parseKobo("20k"))
        assertEquals(2_000_000L, SpokenAmount.parseKobo("20 thousand"))
        assertEquals(2_000_000L, SpokenAmount.parseKobo("₦20,000"))
        assertEquals(2_000_000L, SpokenAmount.parseKobo("N20,000"))
        assertEquals(15_000_000L, SpokenAmount.parseKobo("Send 150,000 to Musa"))
        assertEquals(15_000_000L, SpokenAmount.parseKobo("150000"))
        assertEquals(100_000_000L, SpokenAmount.parseKobo("1,000,000"))
        assertEquals(150_000_000L, SpokenAmount.parseKobo("1.5m"))
        assertEquals(120_000_000L, SpokenAmount.parseKobo("one million two hundred thousand"))
        assertEquals(2_050_000L, SpokenAmount.parseKobo("twenty thousand five hundred naira"))
        assertEquals(25_000_000L, SpokenAmount.parseKobo("two hundred and fifty thousand"))
        assertEquals(2_500_000L, SpokenAmount.parseKobo("twenty-five thousand"))
        assertEquals(100_000L, SpokenAmount.parseKobo("a thousand"))
    }

    @Test
    fun stopsAtTheFirstOrdinaryWordAndIgnoresStrayLetters() {
        assertEquals(2_000_000L, SpokenAmount.parseKobo("I'm sending 20k to Tolu 2"))
        assertNull(SpokenAmount.parseKobo("Send money to Tolu"))
        assertNull(SpokenAmount.parseKobo("I'm here"))
        assertNull(SpokenAmount.parseKobo("zero"))
        assertNull(SpokenAmount.parseKobo(""))
    }
}

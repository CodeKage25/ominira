package app.ominira.core

import app.ominira.bank.FakeOminiraApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRulesTest {

    private fun pin(s: String) = s.map { it - '0' }

    @Test
    fun easyPinsAreRefused() {
        for (p in listOf("1111", "1234", "9876", "1212", "0123")) {
            assertEquals(p, PinRules.Weak.Easy, PinRules.check(pin(p), null))
        }
        assertNull(PinRules.check(pin("2075"), null))
    }

    @Test
    fun theBirthdayOnTheCardIsRefused() {
        val born = "1961-03-14"
        assertEquals(PinRules.Weak.Birthday, PinRules.check(pin("1961"), born))
        assertEquals(PinRules.Weak.Birthday, PinRules.check(pin("1403"), born))
        assertEquals(PinRules.Weak.Birthday, PinRules.check(pin("0314"), born))
        assertNull(PinRules.check(pin("4826"), born))
    }

    @Test
    fun theBankInsideTheAppSignsInWithTheNewPin() = runTest {
        val bank = FakeOminiraApi(latency = 0)
        val id = bank.startOnboarding(Lang.En).id
        assertTrue(bank.confirmBvn(id).match)
        assertFalse(bank.setPin(id, "not a block"))
        assertTrue(bank.setPin(id, PinBlock.seal(pin("4826"))))
        assertTrue(bank.unlock(PinBlock.seal(pin("4826"))) is Unlock.Open)
        assertTrue(bank.unlock(PinBlock.seal(pin("2075"))) is Unlock.Wrong)
    }
}

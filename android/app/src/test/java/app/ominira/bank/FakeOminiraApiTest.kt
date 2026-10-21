package app.ominira.bank

import app.ominira.core.DraftKind
import app.ominira.core.DraftStatus
import app.ominira.core.Drafted
import app.ominira.core.Lang
import app.ominira.core.Lines
import app.ominira.core.Outcome
import app.ominira.core.PinBlock
import app.ominira.core.Unlock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeOminiraApiTest {

    private val rightPin = PinBlock.seal(listOf(2, 0, 7, 5))
    private val wrongPin = PinBlock.seal(listOf(1, 1, 1, 1))

    private fun bank() = FakeOminiraApi(latency = 0)

    private suspend fun FakeOminiraApi.transfer(payeeId: String, kobo: Long) =
        (draftTransfer(payeeId, kobo) as Drafted.Made).draft

    @Test
    fun script1_sendTwentyThousandToTolu() = runTest {
        val bank = bank()
        val turn = bank.turn("Send twenty thousand to Tolu", Lang.En)
        val draft = turn.draft!!
        assertEquals(DraftKind.Transfer, draft.kind)
        assertEquals(2_000_000L, draft.readback.amountKobo)
        assertEquals("Adebayo Tolulope", draft.readback.name)
        assertEquals(
            listOf("Twenty thousand naira.", "To Adebayo Tolulope.", "Zenith Bank, ending four, four, seven, one.", "Pulse your PIN to send."),
            draft.readback.spoken,
        )
        assertEquals("sha256:e15360d1707c35ca3b32ed4f36edd993fccb733198f020ca23aa612babc574dd", draft.readbackHash)

        val outcome = bank.confirm(draft.id, draft.readbackHash, rightPin, callActive = false)
        assertTrue(outcome is Outcome.Sent)
        assertEquals("Sent. Tolu has it.", outcome.say)
        assertEquals(84_230_000L - 2_000_000L, bank.accounts().first().balanceKobo)
        assertEquals(DraftStatus.Executed, bank.draft(draft.id)?.status)

        // Confirming again never pays twice.
        val again = bank.confirm(draft.id, draft.readbackHash, rightPin, callActive = false)
        assertTrue(again is Outcome.Sent)
        assertEquals(84_230_000L - 2_000_000L, bank.accounts().first().balanceKobo)
    }

    @Test
    fun script2_twoTurnsMakeTheSameDraft() = runTest {
        val bank = bank()
        val first = bank.turn("Send money to Tolu", Lang.En)
        assertNull(first.draft)
        assertEquals("How much should I send to Tolu?", first.say)
        val second = bank.turn("twenty thousand", Lang.En)
        val draft = second.draft!!
        assertEquals(2_000_000L, draft.readback.amountKobo)
        assertEquals("Adebayo Tolulope", draft.readback.name)
    }

    @Test
    fun script3_scamPauseThenCoApprover() = runTest {
        val bank = bank()
        val draft = bank.transfer("py_musa", 15_000_000)

        val paused = bank.confirm(draft.id, draft.readbackHash, rightPin, callActive = true)
        assertTrue(paused is Outcome.Paused)
        assertEquals(DraftStatus.Paused, bank.draft(draft.id)?.status)
        assertEquals(84_230_000L, bank.accounts().first().balanceKobo)

        assertTrue(bank.answerPause(draft.id, stop = false) is Outcome.Resumed)

        // A fresh PIN block; the pause is not asked twice.
        val waiting = bank.confirm(draft.id, draft.readbackHash, rightPin, callActive = true)
        assertTrue(waiting is Outcome.NeedsApproval)
        assertEquals("Funmi", (waiting as Outcome.NeedsApproval).approver)
        assertEquals(84_230_000L, bank.accounts().first().balanceKobo)

        // Offline, Funmi approves after about six seconds (virtual time here).
        val decided = bank.awaitDecision(draft.id)
        assertTrue(decided is Outcome.Sent)
        assertTrue(decided.say.startsWith("Funmi approved."))
        assertEquals(84_230_000L - 15_000_000L, bank.accounts().first().balanceKobo)
    }

    @Test
    fun noPauseWithoutACall() = runTest {
        val bank = bank()
        val draft = bank.transfer("py_musa", 15_000_000)
        assertTrue(bank.confirm(draft.id, draft.readbackHash, rightPin, callActive = false) is Outcome.NeedsApproval)
    }

    @Test
    fun noPauseForSmallAmountsOrSavedPeople() = runTest {
        val bank = bank()
        val small = bank.transfer("py_musa", 1_000_000)
        assertTrue(bank.confirm(small.id, small.readbackHash, rightPin, callActive = true) is Outcome.Sent)
        val mama = bank.transfer("py_mama", 4_000_000)
        assertTrue(bank.confirm(mama.id, mama.readbackHash, rightPin, callActive = true) is Outcome.Sent)
    }

    @Test
    fun stoppingAtThePauseCancels() = runTest {
        val bank = bank()
        val draft = bank.transfer("py_musa", 15_000_000)
        bank.confirm(draft.id, draft.readbackHash, rightPin, callActive = true)
        assertTrue(bank.answerPause(draft.id, stop = true) is Outcome.Stopped)
        assertEquals(DraftStatus.Cancelled, bank.draft(draft.id)?.status)
        assertTrue(bank.confirm(draft.id, draft.readbackHash, rightPin, callActive = true) is Outcome.Stopped)
        assertEquals(84_230_000L, bank.accounts().first().balanceKobo)
    }

    @Test
    fun cancellingWhileTheApproverDecidesSendsNothing() = runTest {
        val bank = bank()
        val draft = bank.transfer("py_tolu", 10_000_000)
        assertTrue(bank.confirm(draft.id, draft.readbackHash, rightPin, callActive = false) is Outcome.NeedsApproval)
        assertTrue(bank.cancel(draft.id) is Outcome.Stopped)
        assertTrue(bank.awaitDecision(draft.id) is Outcome.Stopped)
        assertEquals(84_230_000L, bank.accounts().first().balanceKobo)
    }

    @Test
    fun script5_wrongPinThreeTimesLocks() = runTest {
        val bank = bank()
        val tries = (1..3).map { (bank.unlock(wrongPin) as Unlock.Wrong).triesLeft }
        assertEquals(listOf(2, 1, 0), tries)
        assertTrue(bank.unlock(rightPin) is Unlock.Wrong)
        val draft = bank.transfer("py_tolu", 2_000_000)
        val outcome = bank.confirm(draft.id, draft.readbackHash, rightPin, callActive = false)
        assertEquals(0, (outcome as Outcome.WrongPin).triesLeft)
        assertEquals(84_230_000L, bank.accounts().first().balanceKobo)
    }

    @Test
    fun wrongPinAtConfirmCountsDownThenRightPinResets() = runTest {
        val bank = bank()
        val draft = bank.transfer("py_tolu", 2_000_000)
        assertEquals(2, (bank.confirm(draft.id, draft.readbackHash, wrongPin, false) as Outcome.WrongPin).triesLeft)
        assertEquals(1, (bank.confirm(draft.id, draft.readbackHash, wrongPin, false) as Outcome.WrongPin).triesLeft)
        assertTrue(bank.confirm(draft.id, draft.readbackHash, rightPin, false) is Outcome.Sent)
        assertTrue(bank.unlock(rightPin) is Unlock.Open)
    }

    @Test
    fun aChangedReadbackMustBeHeardAgain() = runTest {
        val bank = bank()
        val draft = bank.transfer("py_tolu", 2_000_000)
        val outcome = bank.confirm(draft.id, "sha256:something-else", rightPin, false)
        assertTrue(outcome is Outcome.HearAgain)
        assertEquals(draft.readbackHash, (outcome as Outcome.HearAgain).readbackHash)
        assertEquals(84_230_000L, bank.accounts().first().balanceKobo)
    }

    @Test
    fun limitsAndFundsAreCheckedBeforeThePin() = runTest {
        val bank = bank()
        val tooBig = bank.transfer("py_tolu", 200_000_000)
        val outcome = bank.confirm(tooBig.id, tooBig.readbackHash, wrongPin, false)
        assertEquals("limit_exceeded", (outcome as Outcome.NotSent).code)
        val more = bank.transfer("py_tolu", 90_000_000)
        assertEquals("insufficient_funds", (bank.confirm(more.id, more.readbackHash, rightPin, false) as Outcome.NotSent).code)
        // Neither used up a PIN try.
        assertTrue(bank.unlock(rightPin) is Unlock.Open)
    }

    @Test
    fun script4_blockMyCard() = runTest {
        val bank = bank()
        val turn = bank.turn("Block my card", Lang.En)
        val draft = turn.draft!!
        assertEquals(DraftKind.CardBlock, draft.kind)
        assertNull(draft.readback.amountKobo)
        assertEquals("Card ending 8830", draft.readback.name)
        val outcome = bank.confirm(draft.id, draft.readbackHash, rightPin, callActive = false)
        assertEquals("Card blocked. A new one is on its way.", outcome.say)
        assertTrue(bank.cards().first().blocked)
        assertTrue(bank.draftCardBlock("cd_1") is Drafted.Refused)
    }

    @Test
    fun aSpokenPinMakesNoDraft() = runTest {
        val bank = bank()
        val turn = bank.turn("Send twenty thousand to Tolu, my PIN is 2075", Lang.En)
        assertNull(turn.draft)
        assertEquals(Lines.PIN_SPOKEN, turn.say)
    }

    @Test
    fun onboardingNeedsALiveSelfieAndAMatchingBvn() = runTest {
        val bank = bank()
        val session = bank.startOnboarding(Lang.Yo)
        assertEquals(4, session.livenessDigits.size)
        assertFalse(bank.sendSelfie(session.id, session.livenessDigits.map { (it + 1) % 10 }))
        assertTrue(bank.sendSelfie(session.id, session.livenessDigits))
        bank.readIdDocument(session.id)
        assertFalse(bank.checkBvn(session.id, "12345678901").match)
        assertFalse(bank.completeOnboarding(session.id))
        assertTrue(bank.checkBvn(session.id, "22212345678").match)
        assertTrue(bank.completeOnboarding(session.id))
    }

    @Test
    fun cameInIsWorkedOutFromTheTransactions() = runTest {
        val bank = bank()
        assertEquals("One hundred and eighty-five thousand naira came in this week, from two people.", bank.incomeSummary("ac_1"))
        assertEquals("You have eight hundred and forty-two thousand, three hundred naira.", bank.balance("ac_1").say)
    }
}

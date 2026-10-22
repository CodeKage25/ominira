package app.ominira.bank

import app.ominira.core.Account
import app.ominira.core.Balance
import app.ominira.core.BvnCheck
import app.ominira.core.Card
import app.ominira.core.Draft
import app.ominira.core.Drafted
import app.ominira.core.IdDetails
import app.ominira.core.Lang
import app.ominira.core.OnboardingSession
import app.ominira.core.Outcome
import app.ominira.core.Payee
import app.ominira.core.PreferencePatch
import app.ominira.core.Turn
import app.ominira.core.Txn
import app.ominira.core.Unlock

// Only confirm() moves money, and only with a Pulse PIN block.
interface OminiraApi {

    suspend fun startOnboarding(lang: Lang): OnboardingSession
    suspend fun sendSelfie(sessionId: String, spokenDigits: List<Int>?): Boolean
    // read: what the phone's camera read off the card; it names the new account.
    suspend fun readIdDocument(sessionId: String, read: IdDetails? = null): IdDetails
    suspend fun checkBvn(sessionId: String, bvn: String): BvnCheck
    // With the customer's consent, Zenith finds the BVN with NIBSS: nothing to say aloud.
    suspend fun confirmBvn(sessionId: String): BvnCheck
    suspend fun completeOnboarding(sessionId: String): Boolean
    // After completeOnboarding: the new account's own PIN, sealed. False when the bank refuses it.
    suspend fun setPin(sessionId: String, pinBlock: String): Boolean

    suspend fun unlock(pinBlock: String): Unlock

    suspend fun accounts(): List<Account>

    // The name the app greets the customer by; null when the bank cannot be reached.
    suspend fun firstName(): String?
    suspend fun balance(accountId: String): Balance
    // Newest first; direction is "in", "out" or null for both.
    suspend fun transactions(accountId: String, limit: Int, direction: String?): List<Txn>
    suspend fun incomeSummary(accountId: String): String
    suspend fun payees(): List<Payee>
    // Someone new: the bank looks up the name behind the number and reads it back in the draft.
    suspend fun draftTransferToAccount(bankCode: String, accountNumber: String, amountKobo: Long): Drafted
    suspend fun cards(): List<Card>

    suspend fun savePreferences(patch: PreferencePatch)

    suspend fun turn(text: String, lang: Lang): Turn

    suspend fun draftTransfer(payeeId: String, amountKobo: Long): Drafted
    suspend fun draftCardBlock(cardId: String): Drafted
    suspend fun draft(draftId: String): Draft?
    suspend fun confirm(draftId: String, readbackHash: String, pinBlock: String, callActive: Boolean): Outcome
    suspend fun answerPause(draftId: String, stop: Boolean): Outcome
    suspend fun cancel(draftId: String): Outcome

    suspend fun awaitDecision(draftId: String): Outcome
}

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

/**
 * The bank as the app sees it: the customer's side of docs/api.md. Two implementations:
 * [FakeOminiraApi] (offline, same demo world and gate order) and [GatewayOminiraApi] (HTTPS).
 *
 * The one rule holds on both: the assistant drafts, plain code moves money. Only [confirm] can
 * move money, and only with a Pulse PIN block.
 */
interface OminiraApi {

    // Onboarding (no auth on the wire). [lang] is the language picked on the first screen.
    suspend fun startOnboarding(lang: Lang): OnboardingSession
    /** False when [spokenDigits] do not match the liveness digits; complete then refuses. */
    suspend fun sendSelfie(sessionId: String, spokenDigits: List<Int>?): Boolean
    suspend fun readIdDocument(sessionId: String): IdDetails
    suspend fun checkBvn(sessionId: String, bvn: String): BvnCheck
    suspend fun completeOnboarding(sessionId: String): Boolean

    // Sign in with the Pulse PIN block
    suspend fun unlock(pinBlock: String): Unlock

    // Reading money
    suspend fun accounts(): List<Account>
    suspend fun balance(accountId: String): Balance
    /** Newest first. [direction] is "in", "out" or null for both. */
    suspend fun transactions(accountId: String, limit: Int, direction: String?): List<Txn>
    suspend fun incomeSummary(accountId: String): String
    suspend fun payees(): List<Payee>
    suspend fun cards(): List<Card>

    // Preferences, best effort
    suspend fun savePreferences(patch: PreferencePatch)

    // The assistant: drafts only, never moves money
    suspend fun turn(text: String, lang: Lang): Turn

    // Drafts and the confirm gate
    suspend fun draftTransfer(payeeId: String, amountKobo: Long): Drafted
    suspend fun draftCardBlock(cardId: String): Drafted
    suspend fun draft(draftId: String): Draft?
    suspend fun confirm(draftId: String, readbackHash: String, pinBlock: String, callActive: Boolean): Outcome
    suspend fun answerPause(draftId: String, stop: Boolean): Outcome
    suspend fun cancel(draftId: String): Outcome

    /**
     * Waits while the draft is with the co-approver, then says how it ended. The Gateway client
     * polls GET /v1/drafts/{id} every 2 s. Cancel the coroutine to stop waiting.
     */
    suspend fun awaitDecision(draftId: String): Outcome
}

package app.ominira.bank

import app.ominira.core.Account
import app.ominira.core.Answers
import app.ominira.core.Balance
import app.ominira.core.BvnCheck
import app.ominira.core.Card
import app.ominira.core.Draft
import app.ominira.core.DraftKind
import app.ominira.core.DraftStatus
import app.ominira.core.Drafted
import app.ominira.core.IdDetails
import app.ominira.core.Lang
import app.ominira.core.Lines
import app.ominira.core.Naira
import app.ominira.core.OnboardingSession
import app.ominira.core.Outcome
import app.ominira.core.Payee
import app.ominira.core.PinBlock
import app.ominira.core.PreferencePatch
import app.ominira.core.Readback
import app.ominira.core.ReadbackHash
import app.ominira.core.SpokenAmount
import app.ominira.core.Turn
import app.ominira.core.Txn
import app.ominira.core.Unlock
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/**
 * The bank inside the app, for demos with no network. Same demo world as docs/api.md (Ronke,
 * ₦842,300, Tolu, Mama and new payee Musa, card ending 8830, Funmi approving above ₦50,000,
 * Pulse PIN 2 0 7 5) and the same confirm gate, checked in the same order:
 * real draft, heard it, limits, scam pause, Pulse PIN, co-approver, execute.
 *
 * Offline, Funmi approves by herself [approvalDelayMs] after she is asked.
 *
 * [latency] scales the small pauses that make the demo feel like a network; tests pass 0.
 */
class FakeOminiraApi(
    private val now: () -> Instant = { Instant.now() },
    private val approvalDelayMs: Long = 6_000,
    private val latency: Long = 1,
) : OminiraApi {

    private val pin = listOf(2, 0, 7, 5)
    private var triesLeft = 3
    private var balanceKobo = 84_230_000L
    private var sentTodayKobo = 0L
    private var lang = Lang.En

    private val approver = "Funmi"
    private val pauseThresholdKobo = 5_000_000L // ₦50,000, to a new payee during a call
    private val approverThresholdKobo = 5_000_000L // above ₦50,000, Funmi decides
    private val perTransferLimitKobo = 100_000_000L // ₦1,000,000
    private val dailyLimitKobo = 500_000_000L // ₦5,000,000

    private val account = Account("ac_1", "Current account", "4567", 0)

    private val people = listOf(
        Payee("py_tolu", "Tolu", "Adebayo Tolulope", "Zenith Bank", "4471", isNew = false, flagged = false),
        Payee("py_mama", "Mama", "Adeyemi Folake", "GTBank", "0213", isNew = false, flagged = false),
        Payee("py_musa", "Musa", "Musa Ibrahim", "OPay", "7710", isNew = true, flagged = false),
    )

    private val cardList = mutableListOf(Card("cd_1", "8830", "Ronke Adeyemi", blocked = false))

    private val txns: MutableList<Txn> = run {
        val t = now()
        mutableListOf(
            Txn("tx_3", "Mama", 1_500_000, incoming = true, at = t.minus(Duration.ofDays(1)).toString(), reference = "ZEN51830291"),
            Txn("tx_2", "Salary, Adewale Stores", 17_000_000, incoming = true, at = t.minus(Duration.ofDays(3)).toString(), reference = "ZEN51722940"),
            Txn("tx_1", "Shoprite, Ikeja", 845_000, incoming = false, at = t.minus(Duration.ofDays(5)).toString(), reference = "ZEN51600173"),
        )
    }

    private class Record(
        var draft: Draft,
        val payee: Payee?,
        val card: Card?,
        val createdAt: Instant,
        var pauseAnswered: Boolean = false,
    )

    private val records = LinkedHashMap<String, Record>()

    /** "Send money to Tolu", then "twenty thousand": who the unfinished request was for. */
    private var waitingFor: Payee? = null

    private class Signup(
        val digits: List<Int> = emptyList(),
        var selfie: Boolean = true,
        var idRead: Boolean = false,
        var bvnOk: Boolean = false,
    )
    private val signups = HashMap<String, Signup>()

    private suspend fun pause(ms: Long) {
        if (latency > 0) delay(ms * latency)
    }

    // ---- Onboarding ----------------------------------------------------------------------

    override suspend fun startOnboarding(lang: Lang): OnboardingSession {
        pause(300)
        this.lang = lang
        val id = "ob_" + token(6)
        val digits = List(4) { Random.nextInt(10) }
        signups[id] = Signup(digits)
        return OnboardingSession(id, digits)
    }

    /** As on the Gateway: live unless digits were sent and they do not match. */
    override suspend fun sendSelfie(sessionId: String, spokenDigits: List<Int>?): Boolean {
        pause(500)
        val s = signups.getOrPut(sessionId) { Signup() }
        s.selfie = spokenDigits == null || s.digits.isEmpty() || spokenDigits == s.digits
        return s.selfie
    }

    override suspend fun readIdDocument(sessionId: String): IdDetails {
        pause(500)
        signups.getOrPut(sessionId) { Signup() }.idRead = true
        return IdDetails("Ronke Adeyemi", "5521", "1961-03-14")
    }

    override suspend fun checkBvn(sessionId: String, bvn: String): BvnCheck {
        pause(700)
        val match = bvn.length == 11 && bvn.all { it in '0'..'9' } && bvn != "12345678901"
        signups.getOrPut(sessionId) { Signup() }.bvnOk = match
        return if (match) BvnCheck(true, "Your BVN matches your card and your face.")
        else BvnCheck(false, "That BVN does not match your card. Please check it and try again.")
    }

    override suspend fun completeOnboarding(sessionId: String): Boolean {
        pause(200)
        val s = signups[sessionId] ?: return false
        return s.selfie && s.idRead && s.bvnOk
    }

    // ---- Sign in ---------------------------------------------------------------------------

    override suspend fun unlock(pinBlock: String): Unlock {
        pause(250)
        return when (val check = checkPin(pinBlock)) {
            null -> Unlock.Open("Welcome back.")
            else -> Unlock.Wrong(check.triesLeft, check.say)
        }
    }

    /** Null when the PIN is right. Shared by sign in and confirm: one PIN, one count of tries. */
    private fun checkPin(pinBlock: String): Outcome.WrongPin? {
        if (triesLeft == 0) return Outcome.WrongPin(0, LOCKED)
        if (PinBlock.open(pinBlock) == pin) {
            triesLeft = 3
            return null
        }
        triesLeft -= 1
        if (triesLeft == 0) return Outcome.WrongPin(0, LOCKED)
        val tries = if (triesLeft == 1) "one try" else Naira.words(triesLeft.toLong()) + " tries"
        return Outcome.WrongPin(triesLeft, "That PIN did not match. Feel the count again. You have $tries left.")
    }

    // ---- Reading money -------------------------------------------------------------------

    override suspend fun accounts(): List<Account> {
        pause(150)
        return listOf(account.copy(balanceKobo = balanceKobo))
    }

    override suspend fun balance(accountId: String): Balance {
        pause(150)
        return Balance(balanceKobo, "You have ${Naira.spoken(balanceKobo)}.")
    }

    override suspend fun transactions(accountId: String, limit: Int, direction: String?): List<Txn> {
        pause(150)
        return txns.filter {
            when (direction) {
                "in" -> it.incoming
                "out" -> !it.incoming
                else -> true
            }
        }.take(limit)
    }

    override suspend fun incomeSummary(accountId: String): String {
        pause(150)
        return cameIn()
    }

    /** Worked out from the transactions of the last seven days, so it stays true after a transfer. */
    private fun cameIn(): String {
        val weekAgo = now().minus(Duration.ofDays(7))
        val incoming = txns.filter { it.incoming && (Instant.parse(it.at).isAfter(weekAgo)) }
        if (incoming.isEmpty()) return "Nothing came in this week."
        val total = incoming.sumOf { it.amountKobo }
        val people = incoming.map { it.who }.distinct().size
        val from = if (people == 1) "one person" else Naira.words(people.toLong()) + " people"
        return "${Naira.spokenSentence(total)} came in this week, from $from."
    }

    override suspend fun payees(): List<Payee> {
        pause(100)
        return people
    }

    override suspend fun cards(): List<Card> {
        pause(100)
        return cardList.toList()
    }

    override suspend fun savePreferences(patch: PreferencePatch) {
        patch.lang?.let { lang = it }
    }

    // ---- The assistant ---------------------------------------------------------------------

    override suspend fun turn(text: String, lang: Lang): Turn {
        pause(600)
        this.lang = lang
        val heard = mask(text)
        fun answer(line: String, draft: Draft? = null) = Turn(heard, line, line, "rules", draft)

        // A spoken PIN is caught before anything else, and never kept.
        if (Answers.soundsLikePin(text)) {
            waitingFor = null
            return answer(Lines.PIN_SPOKEN)
        }

        val t = text.lowercase()
        val said = Answers.normalise(text).split(' ')
        val named = people.firstOrNull { p ->
            said.any { it.startsWith(p.nickname.lowercase()) } ||
                p.name.lowercase().split(' ').any { part -> part.length > 3 && part in said }
        }
        val amount = SpokenAmount.parseKobo(text)
        val payee = named ?: waitingFor?.takeIf { amount != null }
        val sending = listOf("send", "transfer", "pay", "give").any { w -> said.any { it.startsWith(w) } } ||
            (named == null && payee != null)

        return when {
            "block" in t && ("card" in t || "atm" in t) -> {
                waitingFor = null
                val card = cardList.first()
                when (val made = draftCardBlock(card.id)) {
                    is Drafted.Made -> answer("I can block your card ending ${Naira.digitWords(card.last4)}.", made.draft)
                    is Drafted.Refused -> answer(made.say)
                }
            }
            sending && payee == null -> answer("Who are you sending to? Say a name, like Tolu or Mama.")
            sending && payee != null && amount == null -> {
                waitingFor = payee
                answer("How much should I send to ${payee.nickname}?")
            }
            sending && payee != null && amount != null -> {
                waitingFor = null
                when (val made = draftTransfer(payee.id, amount)) {
                    is Drafted.Made -> answer("I found ${payee.name} at ${payee.bank}. Listen, then pulse your PIN to send.", made.draft)
                    is Drafted.Refused -> answer(made.say)
                }
            }
            listOf("came in", "this week", "received", "income", "salary").any { it in t } -> answer(cameIn())
            listOf("balance", "how much", "my money", "account").any { it in t } ->
                answer("You have ${Naira.spoken(balanceKobo)} in your current account.")
            else -> answer("I can send money, read your balance, tell you what came in, or block your card. What would you like?")
        }
    }

    /** Account numbers and BVNs are never repeated in full. */
    private fun mask(text: String): String =
        text.replace(Regex("""\d{10,}""")) { m -> "•".repeat(m.value.length - 4) + m.value.takeLast(4) }

    // ---- Drafts --------------------------------------------------------------------------

    override suspend fun draftTransfer(payeeId: String, amountKobo: Long): Drafted {
        pause(200)
        val p = people.firstOrNull { it.id == payeeId }
            ?: return Drafted.Refused("payee_not_found", "I could not find that person. Nothing was sent.")
        if (amountKobo <= 0) return Drafted.Refused("invalid_amount", "That amount is not right. Nothing was sent.")
        val readback = Readback(
            kind = DraftKind.Transfer,
            name = p.name,
            bank = p.bank,
            last4 = p.last4,
            amountKobo = amountKobo,
            amountText = Naira.format(amountKobo),
            lang = lang.code,
            spoken = listOf(
                Naira.spokenSentence(amountKobo) + ".",
                "To ${p.name}.",
                "${p.bank}, ending ${Naira.digitWords(p.last4)}.",
                "Pulse your PIN to send.",
            ),
        )
        return Drafted.Made(open(readback, p, null))
    }

    override suspend fun draftCardBlock(cardId: String): Drafted {
        pause(200)
        val c = cardList.firstOrNull { it.id == cardId }
            ?: return Drafted.Refused("card_not_found", "I could not find that card. Nothing has changed.")
        if (c.blocked) return Drafted.Refused("already_blocked", "That card is already blocked. A new one is on its way.")
        val readback = Readback(
            kind = DraftKind.CardBlock,
            name = "Card ending ${c.last4}",
            bank = "Zenith Bank",
            last4 = c.last4,
            amountKobo = null,
            amountText = null,
            lang = lang.code,
            spoken = listOf(
                "Block your card ending ${Naira.digitWords(c.last4)}.",
                "It stops working at once. A new card reaches you in about five working days.",
                "Pulse your PIN to block it.",
            ),
        )
        return Drafted.Made(open(readback, null, c))
    }

    private fun open(readback: Readback, payee: Payee?, card: Card?): Draft {
        val created = now()
        val draft = Draft(
            id = "dr_" + token(6),
            kind = readback.kind,
            status = DraftStatus.AwaitingConfirm,
            source = "app",
            readback = readback,
            readbackHash = ReadbackHash.of(readback),
            expiresAt = created.plus(Duration.ofMinutes(10)).toString(),
            reference = null,
            say = null,
        )
        records[draft.id] = Record(draft, payee, card, created)
        return draft
    }

    override suspend fun draft(draftId: String): Draft? {
        val r = records[draftId] ?: return null
        expireIfStale(r)
        return r.draft
    }

    private fun expireIfStale(r: Record) {
        if (r.draft.status.open && Duration.between(r.createdAt, now()) > Duration.ofMinutes(10)) {
            set(r, DraftStatus.Expired, EXPIRED)
        }
    }

    private fun set(r: Record, status: DraftStatus, say: String?, reference: String? = r.draft.reference) {
        r.draft = r.draft.copy(status = status, say = say, reference = reference)
    }

    // ---- The confirm gate ----------------------------------------------------------------

    override suspend fun confirm(draftId: String, readbackHash: String, pinBlock: String, callActive: Boolean): Outcome {
        pause(800)

        // 1. Real draft: this customer's, open, not expired.
        val r = records[draftId] ?: return Outcome.NotSent("not_found", false, "I could not find that request. Nothing was sent.")
        expireIfStale(r)
        when (r.draft.status) {
            DraftStatus.Executed -> return Outcome.Sent(r.draft.reference.orEmpty(), "That was already done. Nothing more was sent.")
            DraftStatus.Cancelled, DraftStatus.Failed -> return Outcome.Stopped("That request is closed. Nothing was sent.")
            DraftStatus.Expired -> return Outcome.Expired(EXPIRED)
            DraftStatus.AwaitingApproval -> return Outcome.NeedsApproval(approver, approvalLine())
            DraftStatus.Processing -> return Outcome.Unsure(ON_ITS_WAY)
            DraftStatus.AwaitingConfirm, DraftStatus.Paused -> Unit
        }

        // 2. Heard it: the readback the customer heard must be the one we would send.
        val fresh = ReadbackHash.of(r.draft.readback)
        if (readbackHash != fresh) {
            return Outcome.HearAgain(r.draft.readback, fresh, "Something changed since I read it to you. Please listen again.")
        }

        val amount = r.draft.readback.amountKobo ?: 0L
        if (r.draft.kind == DraftKind.Transfer) {
            // 3. Limits, then what is in the account.
            if (amount > perTransferLimitKobo) {
                return Outcome.NotSent("limit_exceeded", false, "That is more than one million naira, the most you can send at once. Nothing was sent.")
            }
            if (sentTodayKobo + amount > dailyLimitKobo) {
                val left = (dailyLimitKobo - sentTodayKobo).coerceAtLeast(0)
                return Outcome.NotSent("limit_exceeded", false, "You can send ${Naira.spoken(left)} more today. Nothing was sent.")
            }
            if (amount > balanceKobo) {
                return Outcome.NotSent("insufficient_funds", false, "That is more than you have. Nothing was sent.")
            }

            // 4. Scam pause: a new payee, a large amount and a call, or a flagged payee.
            val p = r.payee
            val risky = p != null && ((p.isNew && amount >= pauseThresholdKobo && callActive) || p.flagged)
            if (risky && !r.pauseAnswered) {
                set(r, DraftStatus.Paused, PAUSE)
                return Outcome.Paused(PAUSE)
            }
        }

        // 5. Pulse PIN.
        checkPin(pinBlock)?.let { return it }

        // 6. Co-approver. The PIN is already checked, so approval executes without a second PIN.
        if (r.draft.kind == DraftKind.Transfer && amount > approverThresholdKobo) {
            set(r, DraftStatus.AwaitingApproval, approvalLine())
            return Outcome.NeedsApproval(approver, approvalLine())
        }

        // 7. Execute.
        return execute(r)
    }

    private fun approvalLine() =
        "$approver checks transfers above fifty thousand naira. I have asked $approver. Nothing moves until $approver says yes."

    private fun execute(r: Record): Outcome {
        return when (r.draft.kind) {
            DraftKind.Transfer -> {
                val amount = r.draft.readback.amountKobo ?: 0L
                if (amount > balanceKobo) {
                    set(r, DraftStatus.Failed, "That is more than you have. Nothing was sent.")
                    return Outcome.NotSent("insufficient_funds", false, "That is more than you have. Nothing was sent.")
                }
                val p = r.payee
                val reference = "ZEN" + Random.nextLong(10_000_000L, 99_999_999L)
                balanceKobo -= amount
                sentTodayKobo += amount
                txns.add(0, Txn(r.draft.id, r.draft.readback.name, amount, incoming = false, at = now().toString(), reference = reference))
                val say = "Sent. ${p?.nickname ?: r.draft.readback.name} has it."
                set(r, DraftStatus.Executed, say, reference)
                Outcome.Sent(reference, say)
            }
            DraftKind.CardBlock -> {
                val i = cardList.indexOfFirst { it.last4 == r.draft.readback.last4 }
                if (i >= 0) cardList[i] = cardList[i].copy(blocked = true)
                val reference = "CRD" + Random.nextLong(100_000L, 999_999L)
                val say = "Card blocked. A new one is on its way."
                set(r, DraftStatus.Executed, say, reference)
                Outcome.Sent(reference, say)
            }
        }
    }

    override suspend fun answerPause(draftId: String, stop: Boolean): Outcome {
        pause(300)
        val r = records[draftId] ?: return Outcome.NotSent("not_found", false, "I could not find that request. Nothing was sent.")
        if (r.draft.status != DraftStatus.Paused) {
            return Outcome.NotSent("not_paused", false, "That request is not waiting on a pause.")
        }
        r.pauseAnswered = true
        return if (stop) {
            val say = "Stopped. Your money is safe. Nothing was sent."
            set(r, DraftStatus.Cancelled, say)
            Outcome.Stopped(say)
        } else {
            val say = "Okay. Pulse your PIN again to send it."
            set(r, DraftStatus.AwaitingConfirm, say)
            Outcome.Resumed(say)
        }
    }

    override suspend fun cancel(draftId: String): Outcome {
        pause(200)
        val r = records[draftId] ?: return Outcome.Stopped("Cancelled. Nothing was sent.")
        return when (r.draft.status) {
            DraftStatus.Executed -> Outcome.Sent(r.draft.reference.orEmpty(), "That was already sent.")
            DraftStatus.Processing -> Outcome.Unsure(ON_ITS_WAY)
            else -> {
                val say = "Cancelled. Nothing was sent."
                if (r.draft.status.open || r.draft.status == DraftStatus.AwaitingApproval) set(r, DraftStatus.Cancelled, say)
                Outcome.Stopped(say)
            }
        }
    }

    override suspend fun awaitDecision(draftId: String): Outcome {
        val r = records[draftId] ?: return Outcome.NotSent("not_found", false, "I could not find that request. Nothing was sent.")
        if (r.draft.status == DraftStatus.AwaitingApproval) delay(approvalDelayMs)
        return when (r.draft.status) {
            DraftStatus.AwaitingApproval -> when (val done = execute(r)) {
                is Outcome.Sent -> done.copy(say = "$approver approved. " + done.say)
                else -> done
            }
            DraftStatus.Executed -> Outcome.Sent(r.draft.reference.orEmpty(), r.draft.say ?: "Sent.")
            DraftStatus.Cancelled -> Outcome.Stopped(r.draft.say ?: "Cancelled. Nothing was sent.")
            DraftStatus.Expired -> Outcome.Expired(r.draft.say ?: EXPIRED)
            else -> Outcome.NotSent("not_waiting", true, "That request is not waiting for $approver.")
        }
    }

    private fun token(n: Int): String {
        val chars = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..n).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private companion object {
        const val LOCKED = "Your Pulse PIN is locked after three wrong tries. Nothing was sent. Zenith will call you to unlock it."
        const val PAUSE = "Is someone on the phone telling you to send this? Nothing has been sent."
        const val EXPIRED = "That request is more than ten minutes old, so I closed it. Nothing was sent."
        const val ON_ITS_WAY = "Your transfer is still on its way. Do not send it again. Check your balance in a few minutes."
    }
}

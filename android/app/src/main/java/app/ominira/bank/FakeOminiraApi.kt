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
import app.ominira.core.Naira
import app.ominira.core.OnboardingSession
import app.ominira.core.Outcome
import app.ominira.core.Payee
import app.ominira.core.PinBlock
import app.ominira.core.PinRules
import app.ominira.core.PreferencePatch
import app.ominira.core.Readback
import app.ominira.core.ReadbackHash
import app.ominira.core.SpokenAmount
import app.ominira.core.Turn
import app.ominira.core.Txn
import app.ominira.core.Unlock
import app.ominira.core.Words
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class FakeOminiraApi(
    private val now: () -> Instant = { Instant.now() },
    private val approvalDelayMs: Long = 6_000,
    private val latency: Long = 1,
    lang: Lang = Lang.En,
) : OminiraApi {

    // The demo PIN until the customer sets their own.
    private var pin = listOf(2, 0, 7, 5)
    private var triesLeft = 3
    private var balanceKobo = 84_230_000L
    private var sentTodayKobo = 0L
    // Every line it says follows the customer's language, like the Gateway's say.
    private var lang = lang
    private val w: Words get() = Words.of(lang)

    private val approver = "Funmi"
    private val pauseThresholdKobo = 5_000_000L
    private val approverThresholdKobo = 5_000_000L
    private val perTransferLimitKobo = 100_000_000L
    private val dailyLimitKobo = 500_000_000L

    private val account = Account("ac_1", "Current account", "4567", 0, "2081234567")

    // Full numbers of the saved people, so "someone new" by number finds them offline too.
    private val numbers = mapOf("2044714471" to "py_tolu", "0123450213" to "py_mama", "8031447710" to "py_musa")

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

    override suspend fun startOnboarding(lang: Lang): OnboardingSession {
        pause(300)
        this.lang = lang
        val id = "ob_" + token(6)
        val digits = List(4) { Random.nextInt(10) }
        signups[id] = Signup(digits)
        return OnboardingSession(id, digits)
    }

    override suspend fun sendSelfie(sessionId: String, spokenDigits: List<Int>?): Boolean {
        pause(500)
        val s = signups.getOrPut(sessionId) { Signup() }
        s.selfie = spokenDigits == null || s.digits.isEmpty() || spokenDigits == s.digits
        return s.selfie
    }

    override suspend fun readIdDocument(sessionId: String, read: IdDetails?): IdDetails {
        pause(500)
        signups.getOrPut(sessionId) { Signup() }.idRead = true
        return read ?: IdDetails("Ronke Adeyemi", "5521", "1961-03-14")
    }

    override suspend fun checkBvn(sessionId: String, bvn: String): BvnCheck {
        pause(700)
        val match = bvn.length == 11 && bvn.all { it in '0'..'9' } && bvn != "12345678901"
        signups.getOrPut(sessionId) { Signup() }.bvnOk = match
        return if (match) BvnCheck(true, w.bvnMatches) else BvnCheck(false, w.bvnNoMatch)
    }

    override suspend fun confirmBvn(sessionId: String): BvnCheck {
        pause(700)
        signups.getOrPut(sessionId) { Signup() }.bvnOk = true
        return BvnCheck(true, w.bvnFound)
    }

    override suspend fun setPin(sessionId: String, pinBlock: String): Boolean {
        pause(300)
        val digits = PinBlock.open(pinBlock) ?: return false
        if (PinRules.check(digits, null) != null) return false
        pin = digits
        triesLeft = 3
        return true
    }

    override suspend fun completeOnboarding(sessionId: String): Boolean {
        pause(200)
        val s = signups[sessionId] ?: return false
        return s.selfie && s.idRead && s.bvnOk
    }

    override suspend fun unlock(pinBlock: String): Unlock {
        pause(250)
        return when (val check = checkPin(pinBlock)) {
            null -> Unlock.Open(w.welcomeBack)
            else -> Unlock.Wrong(check.triesLeft, check.say)
        }
    }

    // Null when right. Shared by sign in and confirm: one count of tries.
    private fun checkPin(pinBlock: String): Outcome.WrongPin? {
        if (triesLeft == 0) return Outcome.WrongPin(0, w.pinLockedFull)
        if (PinBlock.open(pinBlock) == pin) {
            triesLeft = 3
            return null
        }
        triesLeft -= 1
        if (triesLeft == 0) return Outcome.WrongPin(0, w.pinLockedFull)
        return Outcome.WrongPin(triesLeft, w.pinWrong(triesLeft))
    }

    override suspend fun firstName(): String = "Ronke"

    override suspend fun accounts(): List<Account> {
        pause(150)
        return listOf(account.copy(balanceKobo = balanceKobo))
    }

    override suspend fun balance(accountId: String): Balance {
        pause(150)
        return Balance(balanceKobo, w.youHave(balanceKobo))
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

    private fun cameIn(): String {
        val weekAgo = now().minus(Duration.ofDays(7))
        val incoming = txns.filter { it.incoming && (Instant.parse(it.at).isAfter(weekAgo)) }
        if (incoming.isEmpty()) return w.nothingCameIn
        return w.cameIn(incoming.sumOf { it.amountKobo }, incoming.map { it.who }.distinct().size)
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

    override suspend fun turn(text: String, lang: Lang): Turn {
        pause(600)
        this.lang = lang
        val heard = mask(text)
        fun answer(line: String, draft: Draft? = null) = Turn(heard, line, line, "rules", draft)

        // The spoken PIN check comes first; the PIN is never kept.
        if (Answers.soundsLikePin(text)) {
            waitingFor = null
            return answer(w.pinSpoken)
        }

        val t = text.lowercase()
        val said = Answers.normalise(text).split(' ')
        val named = people.firstOrNull { p ->
            said.any { it.startsWith(p.nickname.lowercase()) } ||
                p.name.lowercase().split(' ').any { part -> part.length > 3 && part in said }
        }
        val amount = SpokenAmount.parseKobo(text)
        val payee = named ?: waitingFor?.takeIf { amount != null }
        // English, then yo, ha and ig words as the en-NG recogniser spells them.
        val sending = listOf("send", "transfer", "pay", "give", "ranse", "aika", "tura", "ziga").any { v -> said.any { it.startsWith(v) } } ||
            (named == null && payee != null)

        return when {
            listOf("block", "toshe", "gbochi").any { it in t } && listOf("card", "atm", "kaadi", "kati").any { it in t } -> {
                waitingFor = null
                val card = cardList.first()
                when (val made = draftCardBlock(card.id)) {
                    is Drafted.Made -> answer(w.canBlockCard(card.last4), made.draft)
                    is Drafted.Refused -> answer(made.say)
                }
            }
            sending && payee == null -> answer(w.whoSendingTo)
            sending && payee != null && amount == null -> {
                waitingFor = payee
                answer(w.howMuchSend(payee.nickname))
            }
            sending && payee != null && amount != null -> {
                waitingFor = null
                when (val made = draftTransfer(payee.id, amount)) {
                    is Drafted.Made -> answer(w.foundPayee(payee.name, payee.bank), made.draft)
                    is Drafted.Refused -> answer(made.say)
                }
            }
            listOf("came in", "this week", "received", "income", "salary", "wole", "shigo", "batara").any { it in t } -> answer(cameIn())
            listOf("balance", "how much", "my money", "account", "elo", "nawa", "ego ole").any { it in t } -> answer(w.balanceInAccount(balanceKobo))
            else -> answer(w.help)
        }
    }

    private fun mask(text: String): String =
        text.replace(Regex("""\d{10,}""")) { m -> "•".repeat(m.value.length - 4) + m.value.takeLast(4) }

    override suspend fun draftTransfer(payeeId: String, amountKobo: Long): Drafted {
        pause(200)
        val p = people.firstOrNull { it.id == payeeId }
            ?: return Drafted.Refused("payee_not_found", w.payeeNotFound)
        if (amountKobo <= 0) return Drafted.Refused("invalid_amount", w.amountNotRight)
        val readback = Readback(
            kind = DraftKind.Transfer,
            name = p.name,
            bank = p.bank,
            last4 = p.last4,
            amountKobo = amountKobo,
            amountText = Naira.format(amountKobo),
            lang = lang.code,
            spoken = listOf(w.moneySentence(amountKobo) + ".", w.rbTo(p.name), w.rbBank(p.bank, p.last4), w.rbPulseSend),
        )
        return Drafted.Made(open(readback, p, null))
    }

    override suspend fun draftTransferToAccount(bankCode: String, accountNumber: String, amountKobo: Long): Drafted {
        val payeeId = numbers[accountNumber.trim()]
            ?: return Drafted.Refused("invalid_account", w.accountNotFound)
        return draftTransfer(payeeId, amountKobo)
    }

    override suspend fun draftCardBlock(cardId: String): Drafted {
        pause(200)
        val c = cardList.firstOrNull { it.id == cardId }
            ?: return Drafted.Refused("card_not_found", w.cardNotFound)
        if (c.blocked) return Drafted.Refused("already_blocked", w.alreadyBlocked)
        val readback = Readback(
            kind = DraftKind.CardBlock,
            name = w.cardName(c.last4),
            bank = "Zenith Bank",
            last4 = c.last4,
            amountKobo = null,
            amountText = null,
            lang = lang.code,
            spoken = listOf(w.rbBlock(c.last4), w.rbBlockEffect, w.rbPulseBlock),
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
            set(r, DraftStatus.Expired, w.expired)
        }
    }

    private fun set(r: Record, status: DraftStatus, say: String?, reference: String? = r.draft.reference) {
        r.draft = r.draft.copy(status = status, say = say, reference = reference)
    }

    override suspend fun confirm(draftId: String, readbackHash: String, pinBlock: String, callActive: Boolean): Outcome {
        pause(800)

        // Same gate order as the Gateway; do not reorder these checks.
        val r = records[draftId] ?: return Outcome.NotSent("not_found", false, w.requestNotFound)
        expireIfStale(r)
        when (r.draft.status) {
            DraftStatus.Executed -> return Outcome.Sent(r.draft.reference.orEmpty(), w.alreadyDone)
            DraftStatus.Cancelled, DraftStatus.Failed -> return Outcome.Stopped(w.requestClosed)
            DraftStatus.Expired -> return Outcome.Expired(w.expired)
            DraftStatus.AwaitingApproval -> return Outcome.NeedsApproval(approver, w.approvalWait(approver))
            DraftStatus.Processing -> return Outcome.Unsure(w.onItsWay)
            DraftStatus.AwaitingConfirm, DraftStatus.Paused -> Unit
        }

        val fresh = ReadbackHash.of(r.draft.readback)
        if (readbackHash != fresh) {
            return Outcome.HearAgain(r.draft.readback, fresh, w.readbackChanged)
        }

        val amount = r.draft.readback.amountKobo ?: 0L
        if (r.draft.kind == DraftKind.Transfer) {
            if (amount > perTransferLimitKobo) {
                return Outcome.NotSent("limit_exceeded", false, w.limitSingle)
            }
            if (sentTodayKobo + amount > dailyLimitKobo) {
                val left = (dailyLimitKobo - sentTodayKobo).coerceAtLeast(0)
                return Outcome.NotSent("limit_exceeded", false, w.limitDaily(left))
            }
            if (amount > balanceKobo) {
                return Outcome.NotSent("insufficient_funds", false, w.moreThanYouHave)
            }

            val p = r.payee
            val risky = p != null && ((p.isNew && amount >= pauseThresholdKobo && callActive) || p.flagged)
            if (risky && !r.pauseAnswered) {
                set(r, DraftStatus.Paused, w.pauseFull)
                return Outcome.Paused(w.pauseFull)
            }
        }

        checkPin(pinBlock)?.let { return it }

        if (r.draft.kind == DraftKind.Transfer && amount > approverThresholdKobo) {
            set(r, DraftStatus.AwaitingApproval, w.approvalWait(approver))
            return Outcome.NeedsApproval(approver, w.approvalWait(approver))
        }

        return execute(r)
    }

    private fun execute(r: Record): Outcome {
        return when (r.draft.kind) {
            DraftKind.Transfer -> {
                val amount = r.draft.readback.amountKobo ?: 0L
                if (amount > balanceKobo) {
                    set(r, DraftStatus.Failed, w.moreThanYouHave)
                    return Outcome.NotSent("insufficient_funds", false, w.moreThanYouHave)
                }
                val p = r.payee
                val reference = "ZEN" + Random.nextLong(10_000_000L, 99_999_999L)
                balanceKobo -= amount
                sentTodayKobo += amount
                txns.add(0, Txn(r.draft.id, r.draft.readback.name, amount, incoming = false, at = now().toString(), reference = reference))
                val say = w.sent(p?.nickname ?: r.draft.readback.name)
                set(r, DraftStatus.Executed, say, reference)
                Outcome.Sent(reference, say)
            }
            DraftKind.CardBlock -> {
                val i = cardList.indexOfFirst { it.last4 == r.draft.readback.last4 }
                if (i >= 0) cardList[i] = cardList[i].copy(blocked = true)
                val reference = "CRD" + Random.nextLong(100_000L, 999_999L)
                val say = w.cardBlockedNew
                set(r, DraftStatus.Executed, say, reference)
                Outcome.Sent(reference, say)
            }
        }
    }

    override suspend fun answerPause(draftId: String, stop: Boolean): Outcome {
        pause(300)
        val r = records[draftId] ?: return Outcome.NotSent("not_found", false, w.requestNotFound)
        if (r.draft.status != DraftStatus.Paused) {
            return Outcome.NotSent("not_paused", false, w.notPaused)
        }
        r.pauseAnswered = true
        return if (stop) {
            val say = w.stoppedSafeNothingSent
            set(r, DraftStatus.Cancelled, say)
            Outcome.Stopped(say)
        } else {
            val say = w.pulseAgain
            set(r, DraftStatus.AwaitingConfirm, say)
            Outcome.Resumed(say)
        }
    }

    override suspend fun cancel(draftId: String): Outcome {
        pause(200)
        val r = records[draftId] ?: return Outcome.Stopped(w.cancelled)
        return when (r.draft.status) {
            DraftStatus.Executed -> Outcome.Sent(r.draft.reference.orEmpty(), w.alreadySent)
            DraftStatus.Processing -> Outcome.Unsure(w.onItsWay)
            else -> {
                val say = w.cancelled
                if (r.draft.status.open || r.draft.status == DraftStatus.AwaitingApproval) set(r, DraftStatus.Cancelled, say)
                Outcome.Stopped(say)
            }
        }
    }

    override suspend fun awaitDecision(draftId: String): Outcome {
        val r = records[draftId] ?: return Outcome.NotSent("not_found", false, w.requestNotFound)
        if (r.draft.status == DraftStatus.AwaitingApproval) delay(approvalDelayMs)
        return when (r.draft.status) {
            DraftStatus.AwaitingApproval -> when (val done = execute(r)) {
                is Outcome.Sent -> done.copy(say = w.approved(approver) + " " + done.say)
                else -> done
            }
            DraftStatus.Executed -> Outcome.Sent(r.draft.reference.orEmpty(), r.draft.say ?: w.sentShort)
            DraftStatus.Cancelled -> Outcome.Stopped(r.draft.say ?: w.cancelled)
            DraftStatus.Expired -> Outcome.Expired(r.draft.say ?: w.expired)
            else -> Outcome.NotSent("not_waiting", true, w.notWaitingFor(approver))
        }
    }

    private fun token(n: Int): String {
        val chars = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..n).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}

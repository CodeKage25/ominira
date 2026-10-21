package app.ominira.core

/*
 * The app's picture of the bank, shaped after docs/api.md. Pure Kotlin: no android.* imports,
 * so the fake bank and the parsers run in JVM unit tests.
 */

/** The five languages every `say` line can come in. [tag] is what speech engines are asked for. */
enum class Lang(val code: String, val nativeName: String, val tag: String) {
    En("en", "English", "en-NG"),
    Pcm("pcm", "Pidgin", "en-NG"),
    Yo("yo", "Yorùbá", "yo-NG"),
    Ha("ha", "Hausa", "ha-NG"),
    Ig("ig", "Igbo", "ig-NG");

    companion object {
        fun fromCode(code: String?): Lang? = entries.firstOrNull { it.code == code }
    }
}

/** Ways in. Several can be on at once. [wire] is the value in PUT /v1/me/preferences. */
enum class Mode(val wire: String) { Voice("voice"), Sign("sign"), Switch("switch"), Plain("plain") }

data class Account(val id: String, val name: String, val last4: String, val balanceKobo: Long)

data class Balance(val balanceKobo: Long, val say: String)

data class Payee(
    val id: String,
    val nickname: String,
    val name: String,
    val bank: String,
    val last4: String,
    val isNew: Boolean,
    val flagged: Boolean,
)

/** One movement of money. [at] is ISO 8601 UTC, as on the wire. */
data class Txn(
    val id: String,
    val who: String,
    val amountKobo: Long,
    val incoming: Boolean,
    val at: String,
    val reference: String?,
)

data class Card(val id: String, val last4: String, val holder: String, val blocked: Boolean)

enum class DraftKind(val wire: String) {
    Transfer("transfer"),
    CardBlock("card_block");

    companion object {
        fun fromWire(value: String?): DraftKind = if (value == CardBlock.wire) CardBlock else Transfer
    }
}

/** The draft status machine in docs/api.md. */
enum class DraftStatus(val wire: String) {
    AwaitingConfirm("awaiting_confirm"),
    Paused("paused"),
    AwaitingApproval("awaiting_approval"),
    Processing("processing"),
    Executed("executed"),
    Cancelled("cancelled"),
    Expired("expired"),
    Failed("failed");

    val open: Boolean get() = this == AwaitingConfirm || this == Paused

    companion object {
        fun fromWire(value: String?): DraftStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** What the customer hears before anything moves. [spoken] is read line by line, with pauses. */
data class Readback(
    val kind: DraftKind,
    val name: String,
    val bank: String,
    val last4: String,
    val amountKobo: Long?,
    val amountText: String?,
    val lang: String,
    val spoken: List<String>,
)

data class Draft(
    val id: String,
    val kind: DraftKind,
    val status: DraftStatus,
    val source: String,
    val readback: Readback,
    val readbackHash: String,
    val expiresAt: String?,
    val reference: String?,
    val say: String?,
)

/** POST /v1/drafts: a draft, or the Gateway's reason for not making one. */
sealed interface Drafted {
    data class Made(val draft: Draft) : Drafted
    data class Refused(val code: String, val say: String) : Drafted
}

/**
 * Every answer the confirm gate (and the calls around it) can give, already sorted into what the
 * customer needs to know. Each carries the line they hear.
 */
sealed interface Outcome {
    val say: String

    /** Money moved, or the card is blocked. */
    data class Sent(val reference: String, override val say: String) : Outcome

    /** Scam pause: answer with stop or chose myself. Nothing has moved. */
    data class Paused(override val say: String) : Outcome

    /** The PIN was right; the co-approver now decides. Nothing has moved yet. */
    data class NeedsApproval(val approver: String, override val say: String) : Outcome

    /** Pulse PIN did not match. 0 tries left means locked. */
    data class WrongPin(val triesLeft: Int, override val say: String) : Outcome

    /** readback_mismatch: read [readback] to the customer, then confirm with [readbackHash]. */
    data class HearAgain(val readback: Readback, val readbackHash: String, override val say: String) : Outcome

    /** The pause was answered "chose myself": confirm again with a fresh PIN. */
    data class Resumed(override val say: String) : Outcome

    /** Cancelled, stopped at the pause, declined by the approver, or already closed. Nothing moved. */
    data class Stopped(override val say: String) : Outcome

    /** Older than ten minutes, or the approver did not answer in time. Nothing moved. */
    data class Expired(override val say: String) : Outcome

    /** A definite "not sent". With [canRetry] the draft is still open and can be confirmed again. */
    data class NotSent(val code: String, val canRetry: Boolean, override val say: String) : Outcome

    /** Nobody can tell yet whether money moved. The customer must not send it again. */
    data class Unsure(override val say: String) : Outcome
}

data class OnboardingSession(val id: String, val livenessDigits: List<Int>)

data class IdDetails(val fullName: String, val ninLast4: String, val bornOn: String)

data class BvnCheck(val match: Boolean, val say: String)

sealed interface Unlock {
    data class Open(val say: String) : Unlock
    data class Wrong(val triesLeft: Int, val say: String) : Unlock
}

/** One assistant turn. [say] is the line to speak; [draft] is set when money might move. */
data class Turn(val heard: String, val reply: String, val say: String, val engine: String, val draft: Draft?)

/** A partial PUT /v1/me/preferences. Null fields are left out. */
data class PreferencePatch(
    val lang: Lang? = null,
    val modes: Set<Mode>? = null,
    val theme: String? = null,
    val speechRate: Float? = null,
    val pulseGapMs: Long? = null,
)

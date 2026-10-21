package app.ominira.core

// Pure Kotlin, no android.* imports: core/ runs in JVM unit tests.

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

sealed interface Drafted {
    data class Made(val draft: Draft) : Drafted
    data class Refused(val code: String, val say: String) : Drafted
}

sealed interface Outcome {
    val say: String

    data class Sent(val reference: String, override val say: String) : Outcome

    data class Paused(override val say: String) : Outcome

    data class NeedsApproval(val approver: String, override val say: String) : Outcome

    data class WrongPin(val triesLeft: Int, override val say: String) : Outcome

    data class HearAgain(val readback: Readback, val readbackHash: String, override val say: String) : Outcome

    data class Resumed(override val say: String) : Outcome

    data class Stopped(override val say: String) : Outcome

    data class Expired(override val say: String) : Outcome

    data class NotSent(val code: String, val canRetry: Boolean, override val say: String) : Outcome

    // Money may have moved: never tell the customer nothing was sent.
    data class Unsure(override val say: String) : Outcome
}

data class OnboardingSession(val id: String, val livenessDigits: List<Int>)

data class IdDetails(val fullName: String, val ninLast4: String, val bornOn: String)

data class BvnCheck(val match: Boolean, val say: String)

sealed interface Unlock {
    data class Open(val say: String) : Unlock
    data class Wrong(val triesLeft: Int, val say: String) : Unlock
}

data class Turn(val heard: String, val reply: String, val say: String, val engine: String, val draft: Draft?)

data class PreferencePatch(
    val lang: Lang? = null,
    val modes: Set<Mode>? = null,
    val theme: String? = null,
    val speechRate: Float? = null,
    val pulseGapMs: Long? = null,
)

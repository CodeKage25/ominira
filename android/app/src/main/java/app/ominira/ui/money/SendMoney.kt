package app.ominira.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Answers
import app.ominira.core.Draft
import app.ominira.core.Drafted
import app.ominira.core.Naira
import app.ominira.core.Outcome
import app.ominira.core.Payee
import app.ominira.core.PinBlock
import app.ominira.core.Readback
import app.ominira.ui.kit.ActionButton
import app.ominira.ui.kit.Badge
import app.ominira.ui.kit.BlockingSheet
import app.ominira.ui.kit.ChatLine
import app.ominira.ui.kit.ChoiceRow
import app.ominira.ui.kit.DigitField
import app.ominira.ui.kit.Emphasis
import app.ominira.ui.kit.Grouping
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.Hint
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.Kicker
import app.ominira.ui.kit.Milestone
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.Progress
import app.ominira.ui.kit.ReadbackPanel
import app.ominira.ui.kit.Timeline
import app.ominira.ui.kit.Tone
import app.ominira.ui.pulse.PulseStep
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.Tones
import app.ominira.voice.rememberPrompt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where the customer is in sending money. */
private enum class Step { Ask, Readback, Pulse, Paused, Approval, Done, Stopped, Unsure }

private const val PAUSE_QUESTION = "Is someone on the phone telling you to send this?"
private const val NEVER_CALLS = "Zenith will never call you to move money. If anyone on this call told you to do this, hang up now."

/**
 * Send money: ask, hear the readback, say yes, pulse the PIN, hear the outcome. The confirm gate
 * may answer with a scam pause or a co-approver wait; both stay on this screen so the customer
 * never loses their place.
 *
 * In voice mode every step is asked and answered out loud. Without voice, the saved people and an
 * amount field do the same job by touch.
 */
@Composable
fun SendMoneyScreen(session: Session, onHome: () -> Unit, onBalance: () -> Unit, onSign: () -> Unit) {
    val scope = rememberCoroutineScope()
    val handed = remember { session.pendingTurn }
    LaunchedEffect(Unit) { session.pendingTurn = null }

    var step by remember { mutableStateOf(if (handed?.draft != null) Step.Readback else Step.Ask) }
    var draft by remember { mutableStateOf(handed?.draft) }
    var heard by remember { mutableStateOf(handed?.heard) }
    var reply by remember { mutableStateOf(handed?.say) }
    var replyCount by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }
    var ending by remember { mutableStateOf("") }
    var askLead by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var approver by remember { mutableStateOf("your approver") }
    var waiting by remember { mutableStateOf<Job?>(null) }
    var people by remember { mutableStateOf<List<Payee>>(emptyList()) }
    var picked by remember { mutableStateOf<Payee?>(null) }
    var amountText by remember { mutableStateOf("") }
    var misses by remember { mutableIntStateOf(0) }
    val voice = session.talksFirst
    val confirmLine = "Say yes to go on to your Pulse PIN, or no to stop."

    LaunchedEffect(Unit) { people = runCatching { session.api.payees() }.getOrDefault(emptyList()) }

    fun startOver(lead: String?) {
        waiting?.cancel()
        draft = null
        heard = null
        reply = null
        picked = null
        amountText = ""
        problem = null
        askLead = lead
        step = Step.Ask
    }

    fun handle(outcome: Outcome) {
        busy = false
        when (outcome) {
            is Outcome.Sent -> {
                ending = outcome.say
                problem = null
                step = Step.Done
                session.haptics.done()
                scope.launch { Tones.done() }
            }
            is Outcome.Paused -> {
                step = Step.Paused
                session.haptics.stop()
            }
            is Outcome.NeedsApproval -> {
                approver = outcome.approver
                step = Step.Approval
                session.say(outcome.say)
                val d = draft ?: return
                waiting?.cancel()
                waiting = scope.launch { handle(session.api.awaitDecision(d.id)) }
            }
            is Outcome.WrongPin -> {
                problem = outcome.say
                locked = outcome.triesLeft == 0
                attempt += 1
                step = Step.Pulse
                session.haptics.stop()
                session.say(outcome.say)
            }
            is Outcome.HearAgain -> {
                draft = draft?.copy(readback = outcome.readback, readbackHash = outcome.readbackHash)
                problem = outcome.say
                step = Step.Readback
            }
            is Outcome.Resumed -> {
                problem = null
                attempt += 1
                step = Step.Pulse
                session.say(outcome.say)
            }
            is Outcome.Stopped -> {
                ending = outcome.say
                step = Step.Stopped
            }
            is Outcome.Expired -> {
                ending = outcome.say
                step = Step.Stopped
            }
            is Outcome.NotSent -> {
                if (outcome.canRetry && draft != null) {
                    problem = outcome.say
                    step = Step.Readback
                } else {
                    ending = outcome.say
                    step = Step.Stopped
                }
            }
            is Outcome.Unsure -> {
                ending = outcome.say
                step = Step.Unsure
                session.haptics.stop()
            }
        }
    }

    /** A spoken or typed request goes to the assistant, which may make a draft. */
    fun request(text: String) {
        heard = text
        busy = true
        problem = null
        scope.launch {
            val turn = runCatching { session.api.turn(text, session.lang) }.getOrNull()
            busy = false
            if (turn == null) {
                reply = "Sorry, I could not do that just now. Please try again."
                replyCount += 1
                return@launch
            }
            reply = turn.say
            val made = turn.draft
            if (made != null) {
                draft = made
                step = Step.Readback
            } else {
                replyCount += 1
            }
        }
    }

    /** Without voice: the picked person and the typed amount become a draft directly. */
    fun checkTyped(who: Payee) {
        val kobo = amountText.toLongOrNull()?.times(100) ?: return
        busy = true
        problem = null
        heard = "Send ${Naira.format(kobo)} to ${who.nickname}"
        scope.launch {
            val made = runCatching { session.api.draftTransfer(who.id, kobo) }.getOrNull()
            busy = false
            when (made) {
                is Drafted.Made -> {
                    draft = made.draft
                    step = Step.Readback
                }
                is Drafted.Refused -> {
                    problem = made.say
                    session.say(made.say)
                }
                null -> {
                    val line = "I could not prepare that. Nothing was sent. Please try again."
                    problem = line
                    session.say(line)
                }
            }
        }
    }

    fun cancelDraft() {
        val d = draft
        if (d != null) scope.launch { runCatching { session.api.cancel(d.id) } }
        startOver("Okay, I have stopped. Nothing was sent.")
        if (!voice) session.say("Okay, I have stopped. Nothing was sent.")
    }

    fun stopAtPause() {
        val d = draft ?: return
        if (busy) return
        busy = true
        scope.launch {
            val answer = runCatching { session.api.answerPause(d.id, stop = true) }.getOrNull()
            if (answer is Outcome.NotSent || answer == null) {
                busy = false
                problem = answer?.say ?: "I cannot reach the bank right now. Nothing was sent."
                session.say(problem.orEmpty())
            } else {
                handle(answer)
                if (!voice) session.say(answer.say)
            }
        }
    }

    fun carryOn() {
        val d = draft ?: return
        if (busy) return
        busy = true
        scope.launch {
            val answer = runCatching { session.api.answerPause(d.id, stop = false) }.getOrNull()
            if (answer !is Outcome.Resumed) {
                busy = false
                if (answer != null && answer !is Outcome.NotSent) {
                    handle(answer)
                } else {
                    problem = answer?.say ?: "I cannot reach the bank right now. Nothing was sent."
                    session.say(problem.orEmpty())
                }
                return@launch
            }
            if (session.callActive()) {
                // The most important line in the flow: it finishes before anything else is said.
                session.say(NEVER_CALLS)
                session.speaker.awaitQuiet()
            }
            // After "chose myself" the gate wants a fresh PIN block, so the customer pulses again.
            handle(answer)
        }
    }

    fun cancelWaiting() {
        val d = draft ?: return
        waiting?.cancel()
        scope.launch {
            val answer = runCatching { session.api.cancel(d.id) }.getOrNull()
            if (answer == null || answer is Outcome.NotSent) {
                // Not cancelled: it is still with the approver, so keep listening for the answer.
                session.say(answer?.say ?: "I could not reach the bank to cancel it. It is still waiting. Try again.")
                waiting = scope.launch { handle(session.api.awaitDecision(d.id)) }
            } else {
                handle(answer)
            }
        }
    }

    val prompt = rememberPrompt(session) { said ->
        if (said == null) return@rememberPrompt
        when (step) {
            Step.Ask -> request(said)
            Step.Readback -> when {
                Answers.isNo(said) || Answers.mentions(said, "wrong", "stop", "cancel") -> cancelDraft()
                Answers.isAgain(said) -> draft?.let { ask(*it.readback.spoken.toTypedArray(), confirmLine) }
                Answers.isYes(said) || Answers.isStart(said) -> {
                    problem = null
                    step = Step.Pulse
                }
                misses < 2 -> {
                    misses += 1
                    ask(confirmLine)
                }
                else -> session.say("You can also use the buttons on the screen.")
            }
            Step.Paused -> when {
                Answers.isCarryOn(said) -> carryOn()
                Answers.isStop(said) -> stopAtPause()
                misses < 2 -> {
                    misses += 1
                    ask("Say stop, or say carry on.")
                }
                else -> session.say("You can also use the buttons on the screen.")
            }
            Step.Done, Step.Stopped, Step.Unsure -> when {
                Answers.mentions(said, "send", "another", "more") -> startOver(null)
                Answers.mentions(said, "balance") -> onBalance()
                else -> onHome()
            }
            Step.Pulse, Step.Approval -> Unit
        }
    }

    // One voice at a time: each step is said once, then (in voice mode) the microphone opens.
    LaunchedEffect(step, draft?.readbackHash) {
        misses = 0
        val d = draft
        when (step) {
            Step.Ask -> if (voice) prompt.ask(*listOfNotNull(askLead, "Who would you like to send money to, and how much?").toTypedArray())
            Step.Readback -> if (d != null) {
                val lines = listOfNotNull(problem) + d.readback.spoken
                if (voice) prompt.ask(*(lines + confirmLine).toTypedArray()) else session.say(*lines.toTypedArray())
            }
            Step.Paused -> {
                val lines = arrayOf(PAUSE_QUESTION, "This is a new person and a large amount. Nothing has been sent.", "Say stop, or say carry on.")
                if (voice) prompt.ask(*lines) else session.say(*lines)
            }
            Step.Done, Step.Stopped, Step.Unsure -> {
                if (voice) prompt.ask(ending, "Say home to go back, or say send to send more.") else session.say(ending)
            }
            Step.Pulse, Step.Approval -> Unit
        }
    }
    LaunchedEffect(replyCount) {
        val r = reply
        if (replyCount > 0 && r != null && step == Step.Ask) {
            if (voice) prompt.ask(r) else session.say(r)
        }
    }

    if (step == Step.Pulse) {
        val d = draft
        PulseStep(
            session = session,
            title = d?.readback?.let { amountOf(it) }?.let { "Pulse your PIN to send $it" } ?: "Pulse your PIN",
            attempt = attempt,
            problem = problem,
            busy = busy,
            locked = locked,
            onBack = { step = Step.Readback },
            backLabel = "Back to the readback",
            doneLine = if (locked) "Your Pulse PIN is locked. Nothing was sent." else null,
            doneAction = "Back to home",
            onDoneAction = onHome,
            onDigits = { digits ->
                val current = draft ?: return@PulseStep
                // Sealed at once and sent; the digits are not kept.
                val block = PinBlock.seal(digits)
                busy = true
                problem = null
                scope.launch {
                    handle(session.api.confirm(current.id, current.readbackHash, block, session.callActive()))
                }
            },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        Page(
            // While the scam pause is up, only the pause sheet exists for TalkBack.
            modifier = if (step == Step.Paused) Modifier.clearAndSetSemantics { } else Modifier,
            top = {
                IconAction(Glyphs.Back, "Back to home", {
                    prompt.cancel()
                    waiting?.cancel()
                    onHome()
                })
                Heading("Send money", Modifier.weight(1f))
                if (session.callActive()) Badge(Tone.Caution, "On a call", icon = Glyphs.Phone)
            },
            bottom = {
                when (step) {
                    Step.Readback -> {
                        val d = draft
                        if (d != null) {
                            ActionButton(
                                "Pulse PIN to send " + amountOf(d.readback).orEmpty(),
                                onClick = {
                                    prompt.cancel()
                                    problem = null
                                    step = Step.Pulse
                                },
                                icon = Glyphs.Pulse,
                            )
                            ActionButton("Something is wrong", onClick = {
                                prompt.cancel()
                                cancelDraft()
                            }, style = Emphasis.Secondary)
                        }
                    }
                    Step.Done, Step.Stopped, Step.Unsure -> ActionButton("Back to home", onClick = {
                        prompt.cancel()
                        onHome()
                    }, icon = Glyphs.Home)
                    Step.Approval -> ActionButton("Cancel this transfer", onClick = { cancelWaiting() }, style = Emphasis.Secondary)
                    Step.Ask -> ActionButton("Say it", onClick = { prompt.listenNow() }, style = Emphasis.Sun, icon = Glyphs.Mic)
                    Step.Pulse, Step.Paused -> Unit
                }
            },
        ) {
            when (step) {
                Step.Ask -> {
                    ChatLine(false, askLead?.let { "$it Who would you like to send money to, and how much?" } ?: "Who would you like to send money to, and how much?")
                    heard?.let { ChatLine(true, it) }
                    reply?.let { ChatLine(false, it) }
                    problem?.let { Notice(Tone.Danger, it) }
                    if (busy) Hint("Working it out…")
                    Kicker("Or choose a person")
                    for (p in people) {
                        ChoiceRow(
                            title = p.nickname,
                            sub = p.name + ", " + p.bank + if (p.isNew) ". New to you." else "",
                            selected = picked?.id == p.id,
                            minHeight = 64.dp,
                            onClick = {
                                prompt.cancel()
                                picked = p
                            },
                        )
                    }
                    val who = picked
                    if (who != null) {
                        DigitField(
                            label = "How much to ${who.nickname}, in naira?",
                            value = amountText,
                            onValueChange = { amountText = it },
                            maxDigits = 9,
                            grouping = Grouping.Thousands,
                        )
                        ActionButton(
                            "Check it before sending",
                            onClick = { checkTyped(who) },
                            enabled = (amountText.toLongOrNull() ?: 0L) > 0L,
                            busy = busy,
                        )
                    }
                }
                Step.Readback, Step.Paused -> {
                    heard?.let { ChatLine(true, it) }
                    problem?.let { Notice(Tone.Caution, it) }
                    draft?.let { ReadbackPanel(it.readback) }
                }
                Step.Approval -> {
                    val d = draft
                    val who = approver.replaceFirstChar { it.uppercaseChar() }
                    Heading("Waiting for $approver")
                    Hint("$who approves transfers above ₦50,000, and sees who and how much, never your PIN.")
                    Timeline(
                        listOf(
                            Milestone(
                                "You confirmed ${d?.readback?.amountText ?: ""} to ${d?.readback?.name ?: ""}",
                                "Just now, with your Pulse PIN",
                                Progress.Done,
                            ),
                            Milestone("$who is checking", "Sent to $approver's phone", Progress.Now),
                            Milestone("Money is sent", "As soon as $approver approves", Progress.Next),
                        ),
                    )
                }
                Step.Done -> DoneBlock(ending, draft)
                Step.Stopped -> {
                    Notice(Tone.Ok, ending.ifBlank { "Stopped. Your money is safe." }, body = "Nobody from Zenith will ever ask you to move money to keep it safe.")
                    ActionButton("Talk to Zenith now", onClick = onSign, style = Emphasis.Secondary, icon = Glyphs.Phone)
                }
                Step.Unsure -> {
                    Notice(Tone.Caution, ending, body = "Do not send it again. Your balance will show what happened.")
                    ActionButton("Check my balance", onClick = onBalance, style = Emphasis.Secondary, icon = Glyphs.Wallet)
                }
                Step.Pulse -> Unit
            }
        }

        if (step == Step.Paused) {
            BlockingSheet(caution = true) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Om.color.raised)
                        .border(3.dp, Om.color.caution, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Glyphs.Pause, contentDescription = null, tint = Om.color.caution, modifier = Modifier.size(34.dp)) }
                Text(
                    PAUSE_QUESTION,
                    style = Om.type.title,
                    color = Om.color.text,
                    modifier = Modifier.semantics {
                        heading()
                        paneTitle = "Scam check"
                    },
                )
                Text("This is a new person and a large amount. Take your time. Nothing has been sent.", style = Om.type.body, color = Om.color.text)
                problem?.let { Notice(Tone.Danger, it) }
                ActionButton("Yes, stop this payment", onClick = {
                    prompt.cancel()
                    stopAtPause()
                }, style = Emphasis.Caution, icon = Glyphs.Pause, busy = busy)
                ActionButton("No, I chose this myself", onClick = {
                    prompt.cancel()
                    carryOn()
                }, style = Emphasis.Secondary, busy = busy)
            }
        }
    }
}

/** "₦20,000", as the Gateway wrote it, or worked out from the kobo. Null for a card block. */
private fun amountOf(rb: Readback): String? = rb.amountText ?: rb.amountKobo?.let { Naira.format(it) }

/** Money sent: a large tick, the outcome line as a heading, and what went where. */
@Composable
private fun DoneBlock(line: String, draft: Draft?) {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(Om.color.okSoft)
                .border(3.dp, Om.color.ok, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Glyphs.Check, contentDescription = null, tint = Om.color.ok, modifier = Modifier.size(56.dp)) }
        Text(line, style = Om.type.title, color = Om.color.text, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        draft?.readback?.let { rb ->
            val what = amountOf(rb)?.let { "$it to " }.orEmpty() + rb.name
            Text(what + "\n" + rb.bank, style = Om.type.body, color = Om.color.muted, textAlign = TextAlign.Center)
        }
    }
}

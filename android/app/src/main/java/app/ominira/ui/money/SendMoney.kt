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
import app.ominira.core.Command
import app.ominira.core.Commands
import app.ominira.core.Draft
import app.ominira.core.Drafted
import app.ominira.core.Naira
import app.ominira.core.Outcome
import app.ominira.core.Payee
import app.ominira.core.PinBlock
import app.ominira.core.Readback
import app.ominira.core.Words
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
import app.ominira.ui.kit.VoiceDock
import app.ominira.ui.kit.touchAnswer
import app.ominira.ui.pulse.PulseStep
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.Tones
import app.ominira.voice.LocalVoice
import app.ominira.voice.rememberVoice
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class Step { Ask, Readback, Pulse, Paused, Approval, Done, Stopped, Unsure }

// Someone new is at Zenith: person to person inside the bank.
private const val ZENITH = "057"


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
    // A Words pick, so the lead shows in the customer's language and is spoken in the voice's.
    var askLead by remember { mutableStateOf<(Words.() -> String)?>(null) }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var approver by remember { mutableStateOf("") }
    var waiting by remember { mutableStateOf<Job?>(null) }
    var people by remember { mutableStateOf<List<Payee>>(emptyList()) }
    var picked by remember { mutableStateOf<Payee?>(null) }
    // Someone new, by their Zenith account number.
    var someoneNew by remember { mutableStateOf(false) }
    var numberText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var misses by remember { mutableIntStateOf(0) }
    val host = LocalVoice.current
    // Spoken to get here: this screen talks back even outside voice mode.
    val byVoice = remember { host.cameByVoice() }
    val voice = session.talksFirst || byVoice
    val w = session.words
    val s = session.spoken
    val touch = session.answersByTouch
    val confirmLine = if (touch) s.touchToPin + " " + s.holdToStop else s.confirmSend

    LaunchedEffect(Unit) { people = runCatching { session.api.payees() }.getOrDefault(emptyList()) }

    fun startOver(lead: (Words.() -> String)?) {
        waiting?.cancel()
        draft = null
        heard = null
        reply = null
        picked = null
        someoneNew = false
        numberText = ""
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

    fun request(text: String) {
        heard = text
        busy = true
        problem = null
        scope.launch {
            val turn = runCatching { session.api.turn(text, session.lang) }.getOrNull()
            busy = false
            if (turn == null) {
                reply = w.couldNotDo
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

    fun drafted(make: suspend () -> Drafted) {
        busy = true
        problem = null
        scope.launch {
            val made = runCatching { make() }.getOrNull()
            busy = false
            when (made) {
                is Drafted.Made -> {
                    draft = made.draft
                    step = Step.Readback
                }
                is Drafted.Refused -> if (made.code == Drafted.UNREACHABLE) {
                    problem = w.cannotReachBank
                    session.say(s.cannotReachBank)
                } else {
                    problem = made.say
                    session.say(made.say)
                }
                null -> {
                    problem = w.couldNotPrepare
                    session.say(s.couldNotPrepare)
                }
            }
        }
    }

    fun checkTyped(who: Payee) {
        val kobo = amountText.toLongOrNull()?.times(100) ?: return
        heard = w.sendTo(Naira.format(kobo), who.nickname)
        drafted { session.api.draftTransfer(who.id, kobo) }
    }

    fun checkNew() {
        val kobo = amountText.toLongOrNull()?.times(100) ?: return
        if (numberText.length != 10) return
        heard = w.sendTo(Naira.format(kobo), numberText)
        drafted { session.api.draftTransferToAccount(ZENITH, numberText, kobo) }
    }

    fun cancelDraft() {
        val d = draft
        if (d != null) scope.launch { runCatching { session.api.cancel(d.id) } }
        startOver { stoppedNothingSent }
        if (!voice) session.say(s.stoppedNothingSent)
    }

    fun stopAtPause() {
        val d = draft ?: return
        if (busy) return
        busy = true
        scope.launch {
            val answer = runCatching { session.api.answerPause(d.id, stop = true) }.getOrNull()
            if (answer is Outcome.NotSent || answer == null) {
                busy = false
                problem = answer?.say ?: w.cannotReachBank
                session.say(answer?.say ?: s.cannotReachBank)
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
                    problem = answer?.say ?: w.cannotReachBank
                    session.say(answer?.say ?: s.cannotReachBank)
                }
                return@launch
            }
            if (session.callActive()) {
                // Most important line: it finishes before anything else is said.
                session.say(s.neverCalls)
                session.speaker.awaitQuiet()
            }
            handle(answer)
        }
    }

    fun cancelWaiting() {
        val d = draft ?: return
        waiting?.cancel()
        scope.launch {
            val answer = runCatching { session.api.cancel(d.id) }.getOrNull()
            if (answer == null || answer is Outcome.NotSent) {
                // Cancel failed: it is still with the approver, so keep waiting.
                session.say(answer?.say ?: s.cancelFailed)
                waiting = scope.launch { handle(session.api.awaitDecision(d.id)) }
            } else {
                handle(answer)
            }
        }
    }

    // Words a yes-or-no question did not take: ask again twice, then point at the buttons.
    val missed: (String) -> Unit = {
        when {
            misses >= 2 -> session.say(s.useButtons)
            step == Step.Readback -> {
                misses += 1
                host.ask(confirmLine, listen = voice && session.understands)
            }
            else -> {
                misses += 1
                host.ask(s.sayStopOrCarryOn, listen = voice && session.understands)
            }
        }
    }
    rememberVoice(
        onHeard = { said ->
            when (step) {
                Step.Ask -> when (Commands.parse(said)) {
                    null -> {
                        request(said)
                        true
                    }
                    Command.Send -> {
                        host.ask(s.whoAndHowMuch, listen = voice)
                        true
                    }
                    else -> false
                }
                Step.Readback -> when {
                    Answers.isNo(said) || Answers.mentions(said, "wrong", "stop", "cancel") -> {
                        cancelDraft()
                        true
                    }
                    Answers.isAgain(said) -> {
                        draft?.let { host.ask(*it.readback.spoken.toTypedArray(), confirmLine, listen = voice && session.understands) }
                        true
                    }
                    Answers.isYes(said) || Answers.isStart(said) -> {
                        problem = null
                        step = Step.Pulse
                        true
                    }
                    else -> false
                }
                Step.Paused -> when {
                    Answers.isCarryOn(said) -> {
                        carryOn()
                        true
                    }
                    Answers.isStop(said) -> {
                        stopAtPause()
                        true
                    }
                    else -> false
                }
                Step.Done, Step.Stopped, Step.Unsure -> when {
                    Answers.mentions(said, "send", "another", "more", "ranse", "aika", "ziga") -> {
                        startOver(null)
                        true
                    }
                    Answers.mentions(said, "balance", "owo", "kudi", "ego") -> {
                        onBalance()
                        true
                    }
                    // "Say that again" and the like are still commands here.
                    Commands.parse(said) != null -> false
                    else -> {
                        onHome()
                        true
                    }
                }
                Step.Pulse, Step.Approval -> false
            }
        },
        onMissed = if (step == Step.Readback || step == Step.Paused) missed else null,
    )

    LaunchedEffect(step, draft?.readbackHash) {
        misses = 0
        val d = draft
        when (step) {
            Step.Ask -> if (voice) {
                val lead = askLead?.invoke(s) ?: s.placeSend
                host.ask(lead, s.whoAndHowMuch, listen = voice)
            }
            Step.Readback -> if (d != null) {
                val lines = listOfNotNull(problem) + d.readback.spoken
                if (voice) host.ask(*(lines + confirmLine).toTypedArray(), listen = session.understands) else session.say(*lines.toTypedArray())
            }
            Step.Paused -> {
                val lines = arrayOf(s.pauseQuestion, s.newAndLarge, if (touch) s.touchPause else s.sayStopOrCarryOn)
                if (voice) host.ask(*lines, listen = session.understands) else session.say(*lines)
            }
            Step.Done, Step.Stopped, Step.Unsure -> {
                if (voice) host.ask(ending, if (touch) s.touchHome else s.homeOrSendMore, listen = session.understands) else session.say(ending)
            }
            Step.Pulse, Step.Approval -> Unit
        }
    }
    LaunchedEffect(replyCount) {
        val r = reply
        if (replyCount > 0 && r != null && step == Step.Ask) {
            if (voice) host.ask(r, listen = voice) else session.say(r)
        }
    }

    if (step == Step.Pulse) {
        val d = draft
        PulseStep(
            session = session,
            title = d?.readback?.let { amountOf(it) }?.let { w.pulseToSend(it) } ?: w.pulseYourPin,
            attempt = attempt,
            problem = problem,
            busy = busy,
            locked = locked,
            onBack = { step = Step.Readback },
            backLabel = w.backToReadback,
            doneLine = if (locked) w.lockedNothingSent else null,
            doneAction = w.backToHome,
            onDoneAction = onHome,
            onDigits = { digits ->
                val current = draft ?: return@PulseStep
                // Seal at once; the digits are never kept.
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

    Box(
        Modifier
            .fillMaxSize()
            .touchAnswer(
                touch && step in setOf(Step.Readback, Step.Paused, Step.Done, Step.Stopped, Step.Unsure),
                onTap = {
                    host.cancel()
                    when (step) {
                        Step.Readback -> {
                            problem = null
                            step = Step.Pulse
                        }
                        Step.Paused -> stopAtPause()
                        else -> onHome()
                    }
                },
                onHold = {
                    host.cancel()
                    when (step) {
                        Step.Readback -> cancelDraft()
                        Step.Paused -> carryOn()
                        else -> startOver(null)
                    }
                },
            ),
    ) {
        Page(
            // While the scam pause is up, only the pause sheet exists for TalkBack.
            modifier = if (step == Step.Paused) Modifier.clearAndSetSemantics { } else Modifier,
            top = {
                IconAction(Glyphs.Back, w.backToHome, {
                    host.cancel()
                    waiting?.cancel()
                    onHome()
                })
                Heading(w.sendMoney, Modifier.weight(1f))
                if (session.callActive()) Badge(Tone.Caution, w.onACall, icon = Glyphs.Phone)
            },
            bottom = {
                when (step) {
                    Step.Readback -> {
                        val d = draft
                        if (d != null) {
                            ActionButton(
                                w.pulseButtonSend(amountOf(d.readback).orEmpty()),
                                onClick = {
                                    host.cancel()
                                    problem = null
                                    step = Step.Pulse
                                },
                                icon = Glyphs.Pulse,
                            )
                            ActionButton(w.somethingWrong, onClick = {
                                host.cancel()
                                cancelDraft()
                            }, style = Emphasis.Secondary)
                        }
                    }
                    Step.Done, Step.Stopped, Step.Unsure -> ActionButton(w.backToHome, onClick = {
                        host.cancel()
                        onHome()
                    }, icon = Glyphs.Home)
                    Step.Approval -> ActionButton(w.cancelTransfer, onClick = { cancelWaiting() }, style = Emphasis.Secondary)
                    Step.Ask, Step.Pulse, Step.Paused -> Unit
                }
            },
            dock = { VoiceDock(session) },
        ) {
            when (step) {
                Step.Ask -> {
                    ChatLine(false, listOfNotNull(askLead?.invoke(w), w.whoAndHowMuch).joinToString(" "))
                    heard?.let { ChatLine(true, it) }
                    reply?.let { ChatLine(false, it) }
                    problem?.let { Notice(Tone.Danger, it) }
                    if (busy) Hint(w.workingOut)
                    Kicker(w.orChoosePerson)
                    for (p in people) {
                        ChoiceRow(
                            title = p.nickname,
                            sub = p.name + ", " + p.bank + if (p.isNew) ". " + w.newToYou else "",
                            selected = picked?.id == p.id,
                            minHeight = 64.dp,
                            onClick = {
                                host.cancel()
                                picked = p
                                someoneNew = false
                            },
                        )
                    }
                    ChoiceRow(
                        title = w.someoneNew,
                        selected = someoneNew,
                        minHeight = 64.dp,
                        onClick = {
                            host.cancel()
                            picked = null
                            someoneNew = true
                        },
                    )
                    if (someoneNew) {
                        DigitField(
                            label = w.theirAccountNumber,
                            value = numberText,
                            onValueChange = { numberText = it },
                            maxDigits = 10,
                            grouping = Grouping.None,
                        )
                        DigitField(
                            label = w.howMuchNaira,
                            value = amountText,
                            onValueChange = { amountText = it },
                            maxDigits = 9,
                            grouping = Grouping.Thousands,
                        )
                        ActionButton(
                            w.checkBeforeSending,
                            onClick = { checkNew() },
                            enabled = numberText.length == 10 && (amountText.toLongOrNull() ?: 0L) > 0L,
                            busy = busy,
                        )
                    }
                    val who = picked
                    if (who != null) {
                        DigitField(
                            label = w.howMuchTo(who.nickname),
                            value = amountText,
                            onValueChange = { amountText = it },
                            maxDigits = 9,
                            grouping = Grouping.Thousands,
                        )
                        ActionButton(
                            w.checkBeforeSending,
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
                    val name = approver.ifBlank { w.yourApprover }
                    val who = name.replaceFirstChar { it.uppercaseChar() }
                    Heading(w.waitingFor(name))
                    Hint(w.approverRole(who))
                    Timeline(
                        listOf(
                            Milestone(
                                w.youConfirmed(d?.readback?.amountText ?: "", d?.readback?.name ?: ""),
                                w.justNowWithPin,
                                Progress.Done,
                            ),
                            Milestone(w.isChecking(who), w.sentToPhone(name), Progress.Now),
                            Milestone(w.moneyIsSent, w.assoonAsApproves(name), Progress.Next),
                        ),
                    )
                }
                Step.Done -> DoneBlock(ending, draft)
                Step.Stopped -> {
                    Notice(Tone.Ok, ending.ifBlank { w.stoppedSafe }, body = w.nobodyAsksToMove)
                    ActionButton(w.talkToZenith, onClick = onSign, style = Emphasis.Secondary, icon = Glyphs.Phone)
                }
                Step.Unsure -> {
                    Notice(Tone.Caution, ending, body = w.dontSendAgain)
                    ActionButton(w.checkMyBalance, onClick = onBalance, style = Emphasis.Secondary, icon = Glyphs.Wallet)
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
                    w.pauseQuestion,
                    style = Om.type.title,
                    color = Om.color.text,
                    modifier = Modifier.semantics {
                        heading()
                        paneTitle = w.scamCheck
                    },
                )
                Text(w.newAndLargeTakeTime, style = Om.type.body, color = Om.color.text)
                problem?.let { Notice(Tone.Danger, it) }
                ActionButton(w.yesStopPayment, onClick = {
                    host.cancel()
                    stopAtPause()
                }, style = Emphasis.Caution, icon = Glyphs.Pause, busy = busy)
                ActionButton(w.noChoseMyself, onClick = {
                    host.cancel()
                    carryOn()
                }, style = Emphasis.Secondary, busy = busy)
            }
        }
    }
}

private fun amountOf(rb: Readback): String? = rb.amountText ?: rb.amountKobo?.let { Naira.format(it) }

@Composable
private fun DoneBlock(line: String, draft: Draft?) {
    val w = Om.words
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
            val what = amountOf(rb)?.let { w.amountTo(it, rb.name) } ?: rb.name
            Text(what + "\n" + rb.bank, style = Om.type.body, color = Om.color.muted, textAlign = TextAlign.Center)
        }
    }
}

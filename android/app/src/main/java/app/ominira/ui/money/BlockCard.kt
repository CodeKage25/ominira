package app.ominira.ui.money

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.ominira.Session
import app.ominira.core.Answers
import app.ominira.core.Card
import app.ominira.core.Draft
import app.ominira.core.Drafted
import app.ominira.core.Outcome
import app.ominira.core.PinBlock
import app.ominira.ui.kit.ActionButton
import app.ominira.ui.kit.BankCardView
import app.ominira.ui.kit.ChoiceRow
import app.ominira.ui.kit.Confirmation
import app.ominira.ui.kit.Emphasis
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.ReadbackPanel
import app.ominira.ui.kit.Tone
import app.ominira.ui.kit.touchAnswer
import app.ominira.ui.pulse.PulseStep
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import kotlinx.coroutines.launch

@Composable
fun BlockCardScreen(session: Session, onHome: () -> Unit) {
    val scope = rememberCoroutineScope()
    val handed = remember { session.pendingTurn }
    var card by remember { mutableStateOf<Card?>(null) }
    var draft by remember { mutableStateOf<Draft?>(handed?.draft) }
    var pulse by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var blocked by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var ship by remember { mutableStateOf("home") }
    var attempt by remember { mutableIntStateOf(0) }
    var misses by remember { mutableIntStateOf(0) }
    var leaving by remember { mutableStateOf(false) }
    var readAsked by remember { mutableIntStateOf(0) }
    val voice = session.talksFirst
    val w = session.words
    val s = session.spoken
    val touch = session.answersByTouch
    val confirmLine = if (touch) s.touchToPin + " " + s.holdToKeepCard else s.confirmBlock
    val shipLine = s.whereSendCard + " " + if (touch) s.touchShip else s.sayHomeOrBranch

    fun finish(where: String) {
        if (leaving) return
        leaving = true
        ship = where
        scope.launch {
            session.say(if (where == "home") s.cardToHome else s.cardToBranch)
            session.speaker.awaitQuiet()
            onHome()
        }
    }

    fun leave() {
        if (leaving) return
        leaving = true
        onHome()
    }

    val prompt = app.ominira.voice.rememberPrompt(session) { said ->
        if (said == null) return@rememberPrompt
        when {
            blocked && Answers.mentions(said, "home", "house", "ile", "gida", "ulo") -> finish("home")
            blocked && Answers.mentions(said, "branch", "bank", "office", "eka", "reshe", "alaka") -> finish("branch")
            blocked && misses < 2 -> {
                misses += 1
                ask(shipLine)
            }
            blocked -> session.say(s.tapWhereThenDone)
            Answers.isNo(said) -> leave()
            Answers.isAgain(said) -> draft?.let { ask(*it.readback.spoken.toTypedArray(), confirmLine) }
            Answers.isYes(said) || Answers.isStart(said) -> if (draft != null) {
                problem = null
                pulse = true
            }
            misses < 2 -> {
                misses += 1
                ask(confirmLine)
            }
            else -> session.say(s.useButtons)
        }
    }

    LaunchedEffect(Unit) {
        session.pendingTurn = null
        val cards = runCatching { session.api.cards() }.getOrDefault(emptyList())
        card = cards.firstOrNull { !it.blocked } ?: cards.firstOrNull()
        val c = card
        if (draft == null && c != null) {
            when (val made = runCatching { session.api.draftCardBlock(c.id) }.getOrNull()) {
                is Drafted.Made -> draft = made.draft
                is Drafted.Refused -> {
                    problem = made.say
                    session.say(made.say)
                }
                null -> {
                    problem = w.bankUnreachableNoChange
                    session.say(s.bankUnreachableNoChange)
                }
            }
        }
        readAsked += 1
    }

    LaunchedEffect(readAsked) {
        val d = draft
        if (readAsked == 0 || d == null || blocked) return@LaunchedEffect
        val lines = d.readback.spoken.toTypedArray()
        if (voice) prompt.ask(*lines, confirmLine) else session.say(*lines)
    }

    fun confirmWith(digits: List<Int>) {
        val d = draft ?: return
        val block = PinBlock.seal(digits)
        busy = true
        problem = null
        scope.launch {
            val outcome = session.api.confirm(d.id, d.readbackHash, block, session.callActive())
            busy = false
            when (outcome) {
                is Outcome.Sent -> {
                    blocked = true
                    pulse = false
                    card = card?.copy(blocked = true)
                    session.haptics.stop()
                    if (voice) prompt.ask(outcome.say, shipLine) else session.say(outcome.say, s.whereSendCard)
                }
                is Outcome.WrongPin -> {
                    problem = outcome.say
                    locked = outcome.triesLeft == 0
                    attempt += 1
                    session.haptics.stop()
                    session.say(outcome.say)
                }
                is Outcome.HearAgain -> {
                    draft = d.copy(readback = outcome.readback, readbackHash = outcome.readbackHash)
                    problem = outcome.say
                    pulse = false
                    readAsked += 1
                }
                is Outcome.NotSent -> {
                    problem = outcome.say
                    pulse = false
                    if (outcome.canRetry) readAsked += 1 else session.say(outcome.say)
                }
                else -> {
                    problem = outcome.say
                    pulse = false
                    session.say(outcome.say)
                }
            }
        }
    }

    if (pulse && !blocked) {
        PulseStep(
            session = session,
            title = w.pulseToBlockCard,
            attempt = attempt,
            problem = problem,
            busy = busy,
            locked = locked,
            onBack = {
                pulse = false
                readAsked += 1
            },
            backLabel = w.backToReadback,
            doneLine = if (locked) w.lockedCardSame else null,
            doneAction = w.backToHome,
            onDoneAction = { leave() },
            onDigits = { digits -> confirmWith(digits) },
        )
        return
    }

    Page(
        modifier = Modifier.touchAnswer(
            touch && (blocked || draft != null),
            onTap = {
                prompt.cancel()
                if (blocked) {
                    finish("home")
                } else {
                    problem = null
                    pulse = true
                }
            },
            onHold = {
                prompt.cancel()
                if (blocked) finish("branch") else leave()
            },
        ),
        top = {
            IconAction(Glyphs.Back, w.backToHome, {
                prompt.cancel()
                leave()
            })
            Heading(w.myCard, Modifier.weight(1f))
        },
        bottom = {
            if (blocked) {
                ActionButton(w.done, onClick = {
                    prompt.cancel()
                    finish(ship)
                }, icon = Glyphs.Home)
            } else {
                ActionButton(
                    w.pulseToBlock,
                    onClick = {
                        prompt.cancel()
                        problem = null
                        pulse = true
                    },
                    style = Emphasis.Danger,
                    icon = Glyphs.Pulse,
                    enabled = draft != null,
                )
                ActionButton(w.notNow, onClick = {
                    prompt.cancel()
                    leave()
                }, style = Emphasis.Quiet)
            }
        },
    ) {
        card?.let { BankCardView(it) }
        problem?.let { Notice(if (blocked) Tone.Info else Tone.Caution, it) }
        if (blocked) {
            Confirmation(w.cardBlockedNew)
            Text(w.whereCardGo, style = Om.type.label, color = Om.color.text, modifier = Modifier.semantics { heading() })
            ChoiceRow(w.myHomeAddress, onClick = {
                prompt.cancel()
                ship = "home"
            }, selected = ship == "home", sub = "14 Bode Thomas Street, Surulere")
            ChoiceRow(w.zenithBranch, onClick = {
                prompt.cancel()
                ship = "branch"
            }, selected = ship == "branch", sub = w.pickUpMyself)
        } else {
            draft?.let { ReadbackPanel(it.readback, heading = w.checkBeforeBlock, accent = Om.color.danger) }
        }
    }
}

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
import app.ominira.ui.kit.VoiceDock
import app.ominira.ui.kit.touchAnswer
import app.ominira.ui.pulse.PulseStep
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.LocalVoice
import app.ominira.voice.rememberVoice
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
    val host = LocalVoice.current
    // Spoken to get here: this screen talks back even outside voice mode.
    val byVoice = remember { host.cameByVoice() }
    val voice = session.talksFirst || byVoice
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

    // Words the question did not take: ask again twice, then point at the screen.
    val missed: (String) -> Unit = {
        when {
            misses >= 2 -> session.say(if (blocked) s.tapWhereThenDone else s.useButtons)
            else -> {
                misses += 1
                host.ask(if (blocked) shipLine else confirmLine, listen = voice && session.understands)
            }
        }
    }

    rememberVoice(
        onHeard = { said ->
            when {
                blocked && Answers.mentions(said, "home", "house", "ile", "gida", "ulo") -> {
                    finish("home")
                    true
                }
                blocked && Answers.mentions(said, "branch", "bank", "office", "eka", "reshe", "alaka") -> {
                    finish("branch")
                    true
                }
                blocked || draft == null -> false
                Answers.isNo(said) -> {
                    leave()
                    true
                }
                Answers.isAgain(said) -> {
                    draft?.let { host.ask(*it.readback.spoken.toTypedArray(), confirmLine, listen = voice && session.understands) }
                    true
                }
                Answers.isYes(said) || Answers.isStart(said) -> {
                    problem = null
                    pulse = true
                    true
                }
                else -> false
            }
        },
        onMissed = if (blocked || draft != null) missed else null,
    )

    LaunchedEffect(Unit) {
        session.pendingTurn = null
        val cards = runCatching { session.api.cards() }.getOrDefault(emptyList())
        card = cards.firstOrNull { !it.blocked } ?: cards.firstOrNull()
        val c = card
        if (draft == null && c != null) {
            when (val made = runCatching { session.api.draftCardBlock(c.id) }.getOrNull()) {
                is Drafted.Made -> draft = made.draft
                is Drafted.Refused -> if (made.code == Drafted.UNREACHABLE) {
                    problem = w.bankUnreachableNoChange
                    session.say(s.bankUnreachableNoChange)
                } else {
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
        // The first time, say where this is.
        val lines = (listOfNotNull(s.placeCard.takeIf { readAsked == 1 }) + d.readback.spoken).toTypedArray()
        if (voice) host.ask(*lines, confirmLine, listen = session.understands) else session.say(*lines)
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
                    if (voice) host.ask(outcome.say, shipLine, listen = session.understands) else session.say(outcome.say, s.whereSendCard)
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
                host.cancel()
                if (blocked) {
                    finish("home")
                } else {
                    problem = null
                    pulse = true
                }
            },
            onHold = {
                host.cancel()
                if (blocked) finish("branch") else leave()
            },
        ),
        top = {
            IconAction(Glyphs.Back, w.backToHome, {
                host.cancel()
                leave()
            })
            Heading(w.myCard, Modifier.weight(1f))
        },
        bottom = {
            if (blocked) {
                ActionButton(w.done, onClick = {
                    host.cancel()
                    finish(ship)
                }, icon = Glyphs.Home)
            } else {
                ActionButton(
                    w.pulseToBlock,
                    onClick = {
                        host.cancel()
                        problem = null
                        pulse = true
                    },
                    style = Emphasis.Danger,
                    icon = Glyphs.Pulse,
                    enabled = draft != null,
                )
                ActionButton(w.notNow, onClick = {
                    host.cancel()
                    leave()
                }, style = Emphasis.Quiet)
            }
        },
        dock = { VoiceDock(session) },
    ) {
        card?.let { BankCardView(it) }
        problem?.let { Notice(if (blocked) Tone.Info else Tone.Caution, it) }
        if (blocked) {
            Confirmation(w.cardBlockedNew)
            Text(w.whereCardGo, style = Om.type.label, color = Om.color.text, modifier = Modifier.semantics { heading() })
            ChoiceRow(w.myHomeAddress, onClick = {
                host.cancel()
                ship = "home"
            }, selected = ship == "home", sub = "14 Bode Thomas Street, Surulere")
            ChoiceRow(w.zenithBranch, onClick = {
                host.cancel()
                ship = "branch"
            }, selected = ship == "branch", sub = w.pickUpMyself)
        } else {
            draft?.let { ReadbackPanel(it.readback, heading = w.checkBeforeBlock, accent = Om.color.danger) }
        }
    }
}

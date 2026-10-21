package app.ominira.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Answers
import app.ominira.ui.kit.ActionButton
import app.ominira.ui.kit.Badge
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.Hint
import app.ominira.ui.kit.Kicker
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.Tone
import app.ominira.ui.kit.touchAnswer
import app.ominira.ui.theme.Glyphs
import app.ominira.voice.rememberPrompt
import kotlinx.coroutines.launch

// The customer allows Zenith to find their BVN with NIBSS; they never say the 11 digits.
@Composable
fun BvnScreen(session: Session, onBack: () -> Unit, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    val leave = remember { Once() }
    var checking by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var misses by remember { mutableIntStateOf(0) }
    val w = session.words
    val s = session.spoken
    val touch = session.answersByTouch
    val askLine = if (touch) s.allowTap else s.allowYesOrTap

    fun next() = leave.run(onNext)

    fun allow() {
        if (checking || open) return
        checking = true
        problem = null
        scope.launch {
            val result = runCatching {
                val id = session.onboardingSession()
                val answer = session.api.confirmBvn(id)
                if (answer.match && session.api.completeOnboarding(id)) session.syncAll()
                answer
            }.getOrNull()
            checking = false
            when {
                result == null -> {
                    problem = w.bvnFailed
                    session.say(s.bvnFailed)
                }
                result.match -> {
                    open = true
                    session.haptics.done()
                    session.say(result.say, s.accountOpen, s.nextPulse)
                    if (session.talksFirst) {
                        session.speaker.awaitQuiet()
                        next()
                    }
                }
                else -> {
                    problem = result.say
                    session.say(result.say)
                }
            }
        }
    }

    fun notNow() {
        problem = w.bvnDeclined
        session.say(s.bvnDeclined)
    }

    val prompt = rememberPrompt(session) { heard ->
        when {
            heard == null -> session.say(askLine)
            Answers.isNo(heard) -> notNow()
            Answers.isYes(heard) || Answers.isStart(heard) -> allow()
            misses < 2 -> {
                misses += 1
                ask(askLine)
            }
            else -> session.say(s.useButtons)
        }
    }

    LaunchedEffect(Unit) {
        if (session.talksFirst) prompt.ask(s.bvnConsent, askLine)
    }

    val waiting = !open && !checking
    RemindWhileWaiting(session, waiting) { session.say(askLine) }

    Page(
        modifier = if (touch) {
            Modifier.touchAnswer(
                waiting,
                onTap = {
                    prompt.cancel()
                    allow()
                },
                onHold = {
                    prompt.cancel()
                    notNow()
                },
            )
        } else {
            Modifier.tapAnywhere(waiting, w.allowCheck) {
                prompt.cancel()
                allow()
            }
        },
        top = { StepBar(4, onBack) },
        bottom = {
            if (open) {
                ActionButton(w.nextPulseButton, onClick = {
                    session.speaker.stop()
                    next()
                }, icon = Glyphs.Pulse)
            } else {
                ActionButton(w.allowCheck, busy = checking, onClick = {
                    prompt.cancel()
                    allow()
                })
            }
        },
    ) {
        Kicker(w.lastCheck)
        Heading(w.bvnConsentHeading)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(Tone.Ok, w.selfie)
            Badge(Tone.Ok, w.idCard)
            Badge(if (open) Tone.Ok else Tone.Caution, "BVN")
        }
        Hint(w.bvnConsent)
        if (checking) Hint(w.checkingBvn)
        problem?.let { Notice(Tone.Caution, it) }
        if (open) Notice(Tone.Ok, w.accountOpen, body = w.bvnFound)
    }
}

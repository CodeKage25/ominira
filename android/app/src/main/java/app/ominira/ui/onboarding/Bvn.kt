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
import app.ominira.ui.kit.DigitField
import app.ominira.ui.kit.Emphasis
import app.ominira.ui.kit.Grouping
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.Kicker
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.Tone
import app.ominira.ui.kit.touchAnswer
import app.ominira.ui.theme.Glyphs
import app.ominira.voice.rememberPrompt
import kotlinx.coroutines.launch

@Composable
fun BvnScreen(session: Session, onBack: () -> Unit, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    val leave = remember { Once() }
    var bvn by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var refusal by remember { mutableStateOf<String?>(null) }
    var heardBvn by remember { mutableStateOf<String?>(null) }
    var misses by remember { mutableIntStateOf(0) }
    var checkAsked by remember { mutableStateOf(false) }
    val w = session.words
    val s = session.spoken
    val touch = session.answersByTouch

    val error = when {
        tried && bvn.length != 11 -> w.bvnLength(bvn.length)
        else -> refusal
    }

    fun next() = leave.run(onNext)

    fun check() {
        if (bvn.length != 11) {
            tried = true
            return
        }
        if (checking) return
        checking = true
        refusal = null
        scope.launch {
            val result = runCatching {
                val id = session.onboardingSession()
                val answer = session.api.checkBvn(id, bvn)
                if (answer.match && session.api.completeOnboarding(id)) session.syncAll()
                answer
            }.getOrNull()
            checking = false
            when {
                result == null -> {
                    refusal = w.bvnFailed
                    session.say(s.bvnFailed)
                }
                result.match -> {
                    open = true
                    session.haptics.done()
                    session.say(s.accountOpen, s.nextPulse)
                    if (session.talksFirst) {
                        session.speaker.awaitQuiet()
                        next()
                    }
                }
                else -> {
                    refusal = result.say
                    session.say(result.say)
                }
            }
        }
    }

    // Longer quiet, so a BVN said slowly is not cut off at the first gap.
    val prompt = rememberPrompt(session, quietMs = 2_000) { heard ->
        val pending = heardBvn
        when {
            heard == null -> session.say(s.canTypeBvn)
            pending != null && Answers.isYes(heard) -> {
                heardBvn = null
                bvn = pending
                tried = false
                checkAsked = true
            }
            pending != null && Answers.isNo(heard) -> {
                heardBvn = null
                ask(s.sayBvnAgain, listen = session.talksFirst)
            }
            else -> {
                val d = Answers.digits(heard)
                when {
                    d.length == 11 -> {
                        heardBvn = d
                        ask(s.iHeard(s.digits(d)), if (touch) s.touchYesNo else s.isThatRight)
                    }
                    pending != null -> ask(s.yesOrSayAgain)
                    misses >= 3 -> session.say(s.useKeypad)
                    d.isEmpty() -> {
                        misses += 1
                        ask(s.sayElevenDigits, listen = session.talksFirst)
                    }
                    else -> {
                        misses += 1
                        ask(s.heardDigitCount(d.length), listen = session.talksFirst)
                    }
                }
            }
        }
    }

    LaunchedEffect(checkAsked) {
        if (checkAsked) {
            checkAsked = false
            check()
        }
    }

    LaunchedEffect(Unit) {
        if (session.talksFirst) {
            prompt.ask(s.bvnIntro, s.bvnDial, listen = session.talksFirst)
        }
    }

    Page(
        // The digits are heard in en-NG; only the yes or no that follows is a touch.
        modifier = Modifier.touchAnswer(
            touch && heardBvn != null,
            onTap = {
                val pending = heardBvn ?: return@touchAnswer
                prompt.cancel()
                heardBvn = null
                bvn = pending
                tried = false
                checkAsked = true
            },
            onHold = {
                heardBvn = null
                prompt.ask(s.sayBvnAgain, listen = session.talksFirst)
            },
        ),
        top = { StepBar(4, onBack) },
        bottom = {
            if (open) {
                ActionButton(w.nextPulseButton, onClick = {
                    session.speaker.stop()
                    next()
                }, icon = Glyphs.Pulse)
            } else {
                ActionButton(w.checkBvn, busy = checking, onClick = {
                    prompt.cancel()
                    check()
                })
            }
        },
    ) {
        Kicker(w.lastCheck)
        Heading(w.whatIsBvn)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(Tone.Ok, w.selfie)
            Badge(Tone.Ok, w.idCard)
            Badge(if (open) Tone.Ok else Tone.Caution, "BVN")
        }
        DigitField(
            label = w.yourBvn,
            hint = w.bvnHint,
            value = bvn,
            onValueChange = {
                bvn = it
                tried = false
                refusal = null
                open = false
            },
            error = error,
            maxDigits = 11,
            grouping = Grouping.Fours,
        )
        ActionButton(
            w.sayItInstead,
            onClick = {
                heardBvn = null
                prompt.ask(session.spoken.sayBvnDigits, listen = true)
            },
            style = Emphasis.Quiet,
            icon = Glyphs.Mic,
            wide = false,
        )
        if (open) Notice(Tone.Ok, w.accountOpen, body = w.bvnMatches)
    }
}

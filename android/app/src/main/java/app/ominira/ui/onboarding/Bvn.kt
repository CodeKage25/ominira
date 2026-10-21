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
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Answers
import app.ominira.core.Naira
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

    val error = when {
        tried && bvn.length != 11 -> "That is ${bvn.length} digit${if (bvn.length == 1) "" else "s"}. A BVN has 11."
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
                    val line = "I could not check your BVN. Check the connection and try again."
                    refusal = line
                    session.say(line)
                }
                result.match -> {
                    open = true
                    session.haptics.done()
                    session.say("Your account is open.", "Next, your Pulse PIN.")
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
            heard == null -> session.say("You can also type your BVN, then tap Check my BVN.")
            pending != null && Answers.isYes(heard) -> {
                heardBvn = null
                bvn = pending
                tried = false
                checkAsked = true
            }
            pending != null && Answers.isNo(heard) -> {
                heardBvn = null
                ask("Okay. Please say your BVN again, one digit at a time.")
            }
            else -> {
                val d = Answers.digits(heard)
                when {
                    d.length == 11 -> {
                        heardBvn = d
                        ask("I heard: " + Naira.digitWords(d) + ".", "Is that right? Say yes or no.")
                    }
                    pending != null -> ask("Say yes if that is right, or no to say it again.")
                    misses >= 3 -> session.say("Let's use the keypad instead. Type your BVN, then tap Check my BVN.")
                    d.isEmpty() -> {
                        misses += 1
                        ask("Please say the eleven digits of your BVN.")
                    }
                    else -> {
                        misses += 1
                        ask("I heard ${d.length} digits. A BVN has eleven. Please say it again.")
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
            prompt.ask(
                "Last step. Please say your BVN, all eleven digits.",
                "If you do not know it, dial star five six five star zero hash from this phone.",
            )
        }
    }

    Page(
        top = { StepBar(4, onBack) },
        bottom = {
            if (open) {
                ActionButton("Next: your Pulse PIN", onClick = {
                    session.speaker.stop()
                    next()
                }, icon = Glyphs.Pulse)
            } else {
                ActionButton("Check my BVN", busy = checking, onClick = {
                    prompt.cancel()
                    check()
                })
            }
        },
    ) {
        Kicker("Last check")
        Heading("What is your BVN?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(Tone.Ok, "Selfie")
            Badge(Tone.Ok, "ID card")
            Badge(if (open) Tone.Ok else Tone.Caution, "BVN")
        }
        DigitField(
            label = "Your BVN",
            hint = "It is 11 digits. Dial *565*0# from your phone to hear it.",
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
            "Say it instead",
            onClick = {
                heardBvn = null
                prompt.ask("Say your BVN, one digit at a time.", listen = true)
            },
            style = Emphasis.Quiet,
            icon = Glyphs.Mic,
            wide = false,
        )
        if (open) Notice(Tone.Ok, "Your account is open.", body = "Your BVN matches your card and your face.")
    }
}

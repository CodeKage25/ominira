package app.ominira.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Answers
import app.ominira.core.Lang
import app.ominira.core.Mode
import app.ominira.core.OnboardingCopy
import app.ominira.ui.kit.ActionButton
import app.ominira.ui.kit.Brand
import app.ominira.ui.kit.Emphasis
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.Hint
import app.ominira.ui.kit.Kicker
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.StepDots
import app.ominira.ui.kit.Tone
import app.ominira.ui.kit.WayInGrid
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.rememberPrompt

/**
 * The first thing anyone meets. Òmìnira speaks before it asks for anything: say yes, or tap
 * anywhere (the whole screen is one button), and it talks the rest of the way. "Read and tap
 * instead", near the top, leads to the ways-in picker, where nothing is ticked, so a deaf
 * customer never lands in voice mode. With TalkBack on, the app stays quiet and TalkBack reads it.
 */
@Composable
fun WelcomeScreen(session: Session, onTalk: () -> Unit, onRead: () -> Unit) {
    val leave = remember { Once() }
    var tries by remember { mutableIntStateOf(0) }
    var dismissed by remember { mutableIntStateOf(0) }
    // Everything said here is also written here.
    var line by remember { mutableStateOf<String?>(null) }
    val readerOn = remember { session.screenReaderOn() }
    val copy = OnboardingCopy.of(session.lang)
    // Spoken in the chosen language when there is a voice for it; otherwise in English, which is
    // better than Yorùbá, Hausa or Igbo read out by an English voice. The screen stays in the language.
    val voiced = remember(session.lang) { session.speaker.hasVoiceFor(session.lang) }
    val spoken = if (voiced) copy else OnboardingCopy.of(Lang.En)
    val speakIn = if (voiced) session.lang else Lang.En

    fun tell(pick: (OnboardingCopy) -> String) {
        line = pick(copy)
        session.sayIn(speakIn, pick(spoken))
    }

    fun talk() = leave.run {
        session.chooseModes(setOf(Mode.Voice))
        onTalk()
    }

    fun read() = leave.run {
        session.speaker.stop()
        onRead()
    }

    // No permission box on this screen: until the microphone is allowed, the phone's own
    // listening box takes the answer.
    val prompt = rememberPrompt(session, askForMic = false) { heard ->
        when {
            heard == null -> {
                // The listening box was closed, nearly always by a tap on the screen.
                dismissed += 1
                tell { if (dismissed >= 2) it.stopListening else it.tapToBegin }
            }
            Answers.isNo(heard) -> read()
            Answers.isYes(heard) || Answers.isStart(heard) -> talk()
            tries == 0 -> {
                tries = 1
                line = copy.notCaught + " " + copy.sayYes
                session.sayIn(speakIn, spoken.notCaught, spoken.sayYes)
                ask(listen = true)
            }
            else -> tell { it.willWait }
        }
    }

    LaunchedEffect(Unit) {
        if (session.screenReaderOn()) return@LaunchedEffect
        session.sayIn(speakIn, spoken.welcome, spoken.canTalk, spoken.askTalk)
        prompt.ask(listen = true)
    }

    Page(
        modifier = Modifier.tapAnywhere(true, "talk me through it") {
            prompt.cancel()
            talk()
        },
        top = { Brand(Modifier.weight(1f)) },
    ) {
        Kicker(copy.kicker)
        Heading(copy.heading)
        Hint(if (readerOn) copy.hintReader else copy.hint)
        ActionButton(
            copy.readAndTap,
            onClick = {
                prompt.cancel()
                read()
            },
            style = Emphasis.Secondary,
            icon = Glyphs.Plain,
        )
        line?.let { Notice(Tone.Info, it) }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp)
                .clip(Corners.xl)
                .background(Om.color.sun)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Glyphs.Sonar, contentDescription = null, tint = Om.color.onSun, modifier = Modifier.size(72.dp))
            Text(
                copy.bigTap,
                style = Om.type.title,
                color = Om.color.onSun,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Ask how, never what: "How would you like to use Òmìnira?" Several ways can be on. Reached by
 * choosing to read and tap, so this screen stays quiet, and nothing is ticked for the customer.
 */
@Composable
fun WaysInScreen(session: Session, onNext: () -> Unit) {
    var chosen by remember { mutableStateOf(emptySet<Mode>()) }
    val leave = remember { Once() }
    val copy = OnboardingCopy.of(session.lang)
    val label = when (chosen.size) {
        0 -> copy.pickOne
        1 -> copy.continueWith(copy.modeName(chosen.first()))
        else -> copy.continueWith(chosen.size)
    }
    Page(
        top = {
            Brand(Modifier.weight(1f))
            StepDots(1, STEPS)
        },
        bottom = {
            ActionButton(
                label,
                onClick = {
                    leave.run {
                        session.chooseModes(chosen)
                        onNext()
                    }
                },
                icon = Glyphs.Send,
                enabled = chosen.isNotEmpty(),
            )
        },
    ) {
        Kicker(copy.kicker)
        Heading(copy.waysHeading)
        Hint(copy.waysHint)
        WayInGrid(chosen, copy) { mode, on -> chosen = if (on) chosen + mode else chosen - mode }
    }
}

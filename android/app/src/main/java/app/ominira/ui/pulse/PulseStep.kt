package app.ominira.ui.pulse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.ominira.ui.kit.Grouping
import app.ominira.ui.kit.DigitField
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Lines
import app.ominira.core.Mode
import app.ominira.ui.kit.ActionButton
import app.ominira.ui.kit.Confirmation
import app.ominira.ui.kit.Emphasis
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.Kicker
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.Tone
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om

@Composable
fun PulseCoach(session: Session, practising: Boolean, onPractising: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionButton(
            session.words.howDoesItWork,
            onClick = { session.say(Lines.pulseExplain(session.voiceLang, session.holdToPulse)) },
            modifier = Modifier.weight(1f),
            style = Emphasis.Secondary,
        )
        ActionButton(
            if (practising) session.words.donePractising else session.words.practiseFirst,
            onClick = {
                val next = !practising
                onPractising(next)
                if (next) session.say(Lines.practiceStart(session.voiceLang, session.holdToPulse)) else session.speaker.stop()
            },
            modifier = Modifier.weight(1f),
            style = Emphasis.Secondary,
        )
    }
}

@Composable
fun PulseStep(
    session: Session,
    title: String,
    onDigits: (List<Int>) -> Unit,
    attempt: Int,
    problem: String? = null,
    busy: Boolean = false,
    locked: Boolean = false,
    onBack: (() -> Unit)? = null,
    backLabel: String = Om.words.back,
    topStart: (@Composable RowScope.() -> Unit)? = null,
    doneLine: String? = null,
    doneAction: String? = null,
    onDoneAction: () -> Unit = {},
    // Said on arrival instead of the Pulse PIN explanation.
    arrival: List<String>? = null,
) {
    val c = Om.color
    val w = session.words
    var showCount by rememberSaveable { mutableStateOf(Mode.Voice !in session.modes) }
    var practising by rememberSaveable { mutableStateOf(false) }
    var introduced by rememberSaveable { mutableStateOf(false) }
    // Dev bypass, for moving fast in demos: the PIN typed instead of pulsed, silently. Not part of the product;
    // the call's keypad shortcut is the same bypass. The digits are never saved with the screen.
    var typing by rememberSaveable { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // Once a visit: turning the phone rebuilds the screen but is not a new visit.
        if (!introduced && session.talksFirst) session.sayOnArrival(*(arrival ?: listOf(session.pulseIntro())).toTypedArray())
        introduced = true
    }

    Page(
        background = c.forest,
        top = {
            if (onBack != null) IconAction(Glyphs.Back, backLabel, onBack)
            if (topStart != null) topStart() else Spacer(Modifier.weight(1f))
            if (doneLine == null && !locked) {
                ActionButton(
                    if (typing) w.pulseInstead else w.typePin,
                    onClick = {
                        session.speaker.stop()
                        typed = ""
                        typing = !typing
                    },
                    style = Emphasis.Secondary,
                    wide = false,
                )
            }
            ActionButton(
                if (showCount) w.hideCount else w.showCount,
                onClick = { showCount = !showCount },
                style = Emphasis.Secondary,
                wide = false,
            )
        },
        bottom = {
            if (doneLine != null && doneAction != null) {
                ActionButton(doneAction, onClick = onDoneAction, style = Emphasis.Sun, icon = Glyphs.Home)
            }
        },
    ) {
        Kicker("Pulse PIN", color = c.sun)
        Text(title, style = Om.type.title, color = c.bandText, modifier = Modifier.semantics { heading() })
        if (doneLine != null) {
            Confirmation(doneLine)
        } else {
            problem?.let { Notice(if (locked) Tone.Danger else Tone.Caution, it) }
            when {
                busy -> Text(w.checkingWithZenith, style = Om.type.bodyStrong, color = c.bandText)
                locked -> Unit
                typing -> DigitField(
                    w.typePinLabel,
                    typed,
                    { now ->
                        typed = now
                        if (now.length == 4) {
                            // Sealed by the caller at once, as a pulsed PIN is; nothing is kept here.
                            val digits = now.map { it - '0' }
                            typed = ""
                            onDigits(digits)
                        }
                    },
                    maxDigits = 4,
                    grouping = Grouping.None,
                    masked = true,
                )
                else -> {
                    PulseCoach(session, practising) { practising = it }
                    val hold = session.holdToPulse
                    key(attempt, practising, hold) {
                        PulsePad(
                            haptics = session.haptics,
                            gapMs = session.pulseGapMs,
                            showCount = showCount,
                            hold = hold,
                            onDigits = onDigits,
                            practice = practising,
                            zeroSound = session.zeroSound,
                            onPractice = { d -> session.say(Lines.practiceResult(session.voiceLang, d, hold)) },
                            onTooSoon = { session.say(Lines.holdTooSoon(session.voiceLang)) },
                        )
                    }
                }
            }
        }
        if (showCount) Text(w.demoPin, style = Om.type.small, color = c.bandMuted)
    }
}

package app.ominira.ui.pulse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Two buttons above every Pulse PIN pad: the spoken explanation, any time, and a practice round
 * where nothing is entered and zero also sounds, so the customer learns the strong double buzz.
 */
@Composable
fun PulseCoach(session: Session, practising: Boolean, onPractising: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionButton(
            "How does it work?",
            onClick = { session.say(Lines.pulseExplain(session.lang)) },
            modifier = Modifier.weight(1f),
            style = Emphasis.Secondary,
        )
        ActionButton(
            if (practising) "Done practising" else "Practise first",
            onClick = {
                val next = !practising
                onPractising(next)
                if (next) session.say(Lines.practiceStart(session.lang)) else session.speaker.stop()
            },
            modifier = Modifier.weight(1f),
            style = Emphasis.Secondary,
        )
    }
}

/**
 * A whole Pulse PIN screen on the forest band, so it reads the same in every look. In voice mode
 * the count is not drawn (the screen stays dark); "Show count" draws it for sighted viewers.
 *
 * Used for sign in and for every money confirm. [attempt] starts the pad afresh after a wrong PIN.
 */
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
    backLabel: String = "Back",
    topStart: (@Composable RowScope.() -> Unit)? = null,
    doneLine: String? = null,
    doneAction: String? = null,
    onDoneAction: () -> Unit = {},
) {
    val c = Om.color
    var showCount by rememberSaveable { mutableStateOf(Mode.Voice !in session.modes) }
    var practising by rememberSaveable { mutableStateOf(false) }
    var introduced by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Once a visit: turning the phone rebuilds the screen but is not a new visit.
        if (!introduced && session.talksFirst) session.sayOnArrival(session.pulseIntro())
        introduced = true
    }

    Page(
        background = c.forest,
        top = {
            if (onBack != null) IconAction(Glyphs.Back, backLabel, onBack)
            if (topStart != null) topStart() else Spacer(Modifier.weight(1f))
            ActionButton(
                if (showCount) "Hide count" else "Show count",
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
                busy -> Text("Checking with Zenith…", style = Om.type.bodyStrong, color = c.bandText)
                locked -> Unit
                else -> {
                    PulseCoach(session, practising) { practising = it }
                    key(attempt, practising) {
                        PulsePad(
                            haptics = session.haptics,
                            gapMs = session.pulseGapMs,
                            showCount = showCount,
                            onDigits = onDigits,
                            practice = practising,
                            zeroSound = session.zeroSound,
                            onPractice = { d -> session.say(Lines.practiceResult(session.lang, d)) },
                        )
                    }
                }
            }
        }
        if (showCount) Text("Demo PIN 2 0 7 5. On a real phone the screen stays dark and the count is felt, not seen.", style = Om.type.small, color = c.bandMuted)
    }
}

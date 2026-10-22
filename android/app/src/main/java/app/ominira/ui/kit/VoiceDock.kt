package app.ominira.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.LocalVoice

// The same place at the bottom of every screen after onboarding. showButton false where the
// screen offers Speak itself, as switch scanning does.
@Composable
fun VoiceDock(session: Session, showButton: Boolean = true) {
    val voice = LocalVoice.current
    val talking by session.speaker.talking.collectAsState()
    val w = session.words
    val heard = voice.heard
    val said = session.lastLine
    val captions = session.captions && (heard != null || said != null)
    if (!showButton && !captions) return
    val busy = voice.listening || talking
    Column(
        Modifier
            .fillMaxWidth()
            .background(Om.color.raised)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (captions) {
            heard?.let { CaptionBox(w.youSaid, it, maxLines = 2, live = false) }
            said?.let { CaptionBox(w.ominiraSaid, it, maxLines = 3, live = false) }
        }
        if (showButton) {
            ActionButton(
                text = when {
                    voice.thinking -> w.workingItOut
                    busy -> w.stop
                    else -> w.speak
                },
                onClick = { voice.press() },
                modifier = Modifier
                    .heightIn(min = 88.dp)
                    .semantics { if (voice.listening) stateDescription = w.listening },
                style = Emphasis.Sun,
                icon = if (busy) Glyphs.Close else Glyphs.Mic,
                busy = voice.thinking,
            )
        }
    }
}

package app.ominira.ui.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Lines
import app.ominira.ui.kit.ActionButton
import app.ominira.ui.kit.Brand
import app.ominira.ui.kit.CaptionBox
import app.ominira.ui.kit.ChoiceRow
import app.ominira.ui.kit.Emphasis
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.ModeChip
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.Tone
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.rememberEars
import kotlinx.coroutines.launch

@Composable
fun PlainHomeScreen(session: Session, actions: HomeActions) {
    val scope = rememberCoroutineScope()
    var reply by remember { mutableStateOf<String?>(null) }
    var calling by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    val ears = rememberEars(session) { text ->
        if (text.isNullOrBlank()) return@rememberEars
        working = true
        scope.launch {
            val turn = runCatching { session.api.turn(text, session.lang) }.getOrNull()
            working = false
            if (turn == null) {
                reply = "Sorry, I could not do that just now. Please try again."
            } else if (!routeTurn(session, turn, actions)) {
                reply = turn.say
                session.say(turn.say)
            }
        }
    }

    LaunchedEffect(Unit) { session.sayOnArrival(Lines.whatToDo(session.lang)) }

    Page(
        top = {
            Brand(Modifier.weight(1f)) { IconAction(Glyphs.Settings, "Settings", actions.settings) }
        },
        bottom = {
            ActionButton(
                "Or just say it",
                onClick = {
                    session.speaker.stop()
                    ears.listen()
                },
                style = Emphasis.Sun,
                icon = Glyphs.Mic,
                busy = working,
            )
        },
    ) {
        Text(session.greeting(), style = Om.type.lead, color = Om.color.muted)
        Text(Lines.whatToDo(session.lang), style = Om.type.displayM, color = Om.color.text, modifier = Modifier.semantics { heading() })
        reply?.let { CaptionBox("Òmìnira said", it) }
        BigChoice("Send money", Om.color.modeVoice, Glyphs.Send, actions.send)
        BigChoice("How much do I have?", Om.color.modeSwitch, Glyphs.Wallet, actions.balance)
        BigChoice("My card", Om.color.modeSign, Glyphs.Card, actions.card)
        BigChoice("Sign language help", Om.color.modeSign, Glyphs.Sign, actions.sign)
        BigChoice("Talk to a person", Om.color.modePlain, Glyphs.Phone) {
            calling = true
            session.say("Calling a Zenith person who speaks your language. Stay on the line.")
        }
        if (calling) Notice(Tone.Info, "Calling a Zenith person who speaks your language.", body = "Stay on the line.", icon = Glyphs.Phone)
    }
}

@Composable
private fun BigChoice(text: String, color: Color, icon: ImageVector, onClick: () -> Unit) {
    ChoiceRow(
        title = text,
        onClick = onClick,
        radio = false,
        minHeight = 88.dp,
        leading = { ModeChip(color, icon) },
    )
}

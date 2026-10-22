package app.ominira.ui.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.ui.kit.Brand
import app.ominira.ui.kit.ChoiceRow
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.ModeChip
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.Tone
import app.ominira.ui.kit.VoiceDock
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.rememberVoice

@Composable
fun PlainHomeScreen(session: Session, actions: HomeActions) {
    var calling by remember { mutableStateOf(false) }
    val w = session.words
    val voice = rememberVoice()

    LaunchedEffect(Unit) { voice.arrive(session.greeting(session.voiceLang), session.spoken.whatToDo) }

    Page(
        top = {
            Brand(Modifier.weight(1f)) { IconAction(Glyphs.Settings, w.settings, actions.settings) }
        },
        dock = { VoiceDock(session) },
    ) {
        Text(session.greeting(), style = Om.type.lead, color = Om.color.muted)
        Text(w.whatToDo, style = Om.type.displayM, color = Om.color.text, modifier = Modifier.semantics { heading() })
        BigChoice(w.sendMoney, Om.color.modeVoice, Glyphs.Send, actions.send)
        BigChoice(w.howMuchHave, Om.color.modeSwitch, Glyphs.Wallet, actions.balance)
        BigChoice(w.myCard, Om.color.modeSign, Glyphs.Card, actions.card)
        BigChoice(w.signHelp, Om.color.modeSign, Glyphs.Sign, actions.sign)
        BigChoice(w.talkToPerson, Om.color.modePlain, Glyphs.Phone) {
            calling = true
            session.say(session.spoken.callingPerson, session.spoken.stayOnLine)
        }
        if (calling) Notice(Tone.Info, w.callingPerson, body = w.stayOnLine, icon = Glyphs.Phone)
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

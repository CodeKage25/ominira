package app.ominira.ui.home

import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Account
import app.ominira.ui.kit.BalancePanel
import app.ominira.ui.kit.Brand
import app.ominira.ui.kit.ChoiceRow
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.Kicker
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.Tone
import app.ominira.ui.kit.VoiceDock
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.LocalVoice
import app.ominira.voice.rememberVoice

class HomeActions(
    val send: () -> Unit,
    val balance: () -> Unit,
    val card: () -> Unit,
    val sign: () -> Unit,
    val settings: () -> Unit,
)

@Composable
fun VoiceHomeScreen(session: Session, actions: HomeActions) {
    val context = LocalContext.current
    var account by remember { mutableStateOf<Account?>(null) }
    val noMic = remember {
        session.activeCloudVoice() == null && !SpeechRecognizer.isRecognitionAvailable(context)
    }
    val w = session.words
    val s = session.spoken
    val voice = LocalVoice.current

    // Silence keeps the mic open a while longer: the customer may still be thinking.
    var quietTries by remember { mutableIntStateOf(0) }
    rememberVoice(
        onSilence = {
            if (session.talksFirst && quietTries < 2) {
                quietTries += 1
                voice.listenNow()
            }
        },
        onHeard = {
            quietTries = 0
            false
        },
    )

    LaunchedEffect(Unit) {
        if (session.talksFirst || voice.cameByVoice()) voice.ask(session.greeting(session.voiceLang), s.whatToDo, listen = session.talksFirst)
        account = session.mainAccount()
    }

    Page(
        top = {
            Brand(Modifier.weight(1f)) {
                IconAction(Glyphs.Settings, w.settings, {
                    voice.cancel()
                    actions.settings()
                })
            }
        },
        dock = { VoiceDock(session) },
    ) {
        Heading(session.greeting())
        account?.let { a ->
            BalancePanel(w.currentAccount, a.balanceKobo, onHear = { session.say(s.youHave(a.balanceKobo)) })
        }
        if (noMic) Notice(Tone.Info, w.noVoice, body = w.tapRequestBelow)
        // By touch too: saved people, or someone new by account number.
        Suggestion(w.sendMoney, Glyphs.Send) {
            voice.cancel()
            actions.send()
        }
        Kicker(w.trySaying)
        // The request sent stays English: the bank inside the app reads English amounts.
        Suggestion(w.suggestSend, Glyphs.Send) { voice.hear("Send twenty thousand to Tolu") }
        Suggestion(w.suggestCameIn, Glyphs.Wallet) {
            voice.cancel()
            actions.balance()
        }
        Suggestion(w.suggestBlock, Glyphs.Card) { voice.hear("Block my card") }
        Suggestion(w.signHelp, Glyphs.Sign) {
            voice.cancel()
            actions.sign()
        }
    }
}

@Composable
private fun Suggestion(text: String, icon: ImageVector, onClick: () -> Unit) {
    ChoiceRow(
        title = text,
        onClick = onClick,
        radio = false,
        minHeight = 60.dp,
        leading = { Icon(icon, contentDescription = null, tint = Om.color.action, modifier = Modifier.size(24.dp)) },
    )
}

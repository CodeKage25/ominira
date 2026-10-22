package app.ominira.ui.home

import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Account
import app.ominira.core.Answers
import app.ominira.core.DraftKind
import app.ominira.core.Turn
import app.ominira.ui.kit.BalancePanel
import app.ominira.ui.kit.Brand
import app.ominira.ui.kit.CaptionBox
import app.ominira.ui.kit.ChoiceRow
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.Kicker
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Orb
import app.ominira.ui.kit.OrbState
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.Tone
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.rememberPrompt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeActions(
    val send: () -> Unit,
    val balance: () -> Unit,
    val card: () -> Unit,
    val sign: () -> Unit,
    val settings: () -> Unit,
)

// False when the turn made no draft; the caller then speaks the reply.
internal fun routeTurn(session: Session, turn: Turn, actions: HomeActions): Boolean {
    val draft = turn.draft ?: return false
    session.pendingTurn = turn
    when (draft.kind) {
        DraftKind.Transfer -> actions.send()
        DraftKind.CardBlock -> actions.card()
    }
    return true
}

@Composable
fun VoiceHomeScreen(session: Session, actions: HomeActions) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var orb by remember { mutableStateOf(OrbState.Idle) }
    var heard by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf<String?>(null) }
    var account by remember { mutableStateOf<Account?>(null) }
    var request by remember { mutableStateOf<String?>(null) }
    val talking by session.speaker.talking.collectAsState()
    val noMic = remember {
        session.activeCloudVoice() == null && !SpeechRecognizer.isRecognitionAvailable(context)
    }
    val w = session.words
    val s = session.spoken

    // Silence keeps the mic open a while longer: the customer may still be thinking.
    var quietTries by remember { mutableIntStateOf(0) }
    val prompt = rememberPrompt(session, onWarned = { orb = OrbState.Speaking }) { said ->
        when {
            !said.isNullOrBlank() -> {
                quietTries = 0
                request = said
            }
            session.talksFirst && quietTries < 2 -> {
                quietTries += 1
                orb = OrbState.Listening
                listenNow()
            }
            else -> orb = OrbState.Idle
        }
    }

    fun ask(vararg lines: String) {
        quietTries = 0
        if (session.talksFirst) orb = OrbState.Listening
        prompt.ask(*lines, listen = session.talksFirst)
    }

    fun send(text: String) {
        if (orb == OrbState.Thinking) return
        prompt.cancel()
        heard = text
        orb = OrbState.Thinking
        scope.launch {
            // The full number never goes to the assistant: the phone answers this one itself.
            if (Answers.asksAccountNumber(text)) {
                val number = session.mainAccount()?.number.orEmpty()
                if (number.isNotBlank()) {
                    reply = w.accountNumberIs(number.chunked(3).joinToString(" "))
                    orb = OrbState.Idle
                    ask(s.accountNumberIs(s.digits(number)), s.anythingElse)
                    return@launch
                }
            }
            val turn = runCatching { session.api.turn(text, session.lang) }.getOrNull()
            if (turn == null) {
                reply = w.couldNotDo
                orb = OrbState.Speaking
                session.say(s.couldNotDo)
                return@launch
            }
            reply = turn.say
            orb = OrbState.Idle
            if (routeTurn(session, turn, actions)) return@launch
            if (session.talksFirst) {
                if (turn.say.trimEnd().endsWith("?")) ask(turn.say) else ask(turn.say, s.anythingElse)
            } else {
                orb = OrbState.Speaking
                session.say(turn.say)
            }
        }
    }

    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        request = null
        send(r)
    }

    LaunchedEffect(Unit) {
        account = session.mainAccount()
        if (session.talksFirst) ask(session.greeting(session.voiceLang), s.whatToDo)
    }

    LaunchedEffect(talking, orb) {
        if (!talking && orb == OrbState.Speaking) {
            delay(1_500)
            if (!session.speaker.talking.value) orb = OrbState.Idle
        }
    }

    Page(
        top = {
            Brand(Modifier.weight(1f)) {
                IconAction(Glyphs.Settings, w.settings, {
                    prompt.cancel()
                    actions.settings()
                })
            }
        },
        dock = {
            Box(Modifier.fillMaxWidth().background(Om.color.raised)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconAction(Glyphs.Captions, w.captions, { session.chooseCaptions(!session.captions) }, on = session.captions)
                    Orb(
                        state = orb,
                        onClick = {
                            if (orb == OrbState.Idle) {
                                orb = OrbState.Listening
                                prompt.listenNow()
                            } else {
                                prompt.cancel()
                                session.speaker.stop()
                                orb = OrbState.Idle
                            }
                        },
                    )
                    IconAction(Glyphs.Speaker, w.hearThisScreen, {
                        orb = OrbState.Speaking
                        val spoken = session.spoken
                        session.say(spoken.homeTitle, spoken.balanceIs(account?.balanceKobo), spoken.youCanSay)
                    })
                }
            }
        },
    ) {
        Heading(session.greeting())
        account?.let { a ->
            BalancePanel(w.currentAccount, a.balanceKobo, onHear = {
                orb = OrbState.Speaking
                session.say(session.spoken.youHave(a.balanceKobo))
            })
        }
        if (session.captions) {
            heard?.let { CaptionBox(w.youSaid, it) }
            reply?.let { CaptionBox(w.ominiraSaid, it) }
        }
        if (noMic) Notice(Tone.Info, w.noVoice, body = w.tapRequestBelow)
        Kicker(w.trySaying)
        // The request sent stays English: the bank inside the app reads English amounts.
        Suggestion(w.suggestSend, Glyphs.Send) { send("Send twenty thousand to Tolu") }
        Suggestion(w.suggestCameIn, Glyphs.Wallet) {
            prompt.cancel()
            actions.balance()
        }
        Suggestion(w.suggestBlock, Glyphs.Card) { send("Block my card") }
        Suggestion(w.signHelp, Glyphs.Sign) {
            prompt.cancel()
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

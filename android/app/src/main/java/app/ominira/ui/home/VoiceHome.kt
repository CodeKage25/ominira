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
import app.ominira.core.DraftKind
import app.ominira.core.Naira
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

/** Where a home screen sends each request. */
class HomeActions(
    val send: () -> Unit,
    val balance: () -> Unit,
    val card: () -> Unit,
    val sign: () -> Unit,
    val settings: () -> Unit,
)

/**
 * Hands a turn that made a draft to the right screen. Returns false when there is no draft, so
 * the caller speaks the reply instead.
 */
internal fun routeTurn(session: Session, turn: Turn, actions: HomeActions): Boolean {
    val draft = turn.draft ?: return false
    session.pendingTurn = turn
    when (draft.kind) {
        DraftKind.Transfer -> actions.send()
        DraftKind.CardBlock -> actions.card()
    }
    return true
}

/**
 * Voice home: it greets the customer and listens straight away. The orb is the way in; the
 * suggestions below are the same requests one tap away.
 */
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

    val prompt = rememberPrompt(session, onWarned = { orb = OrbState.Speaking }) { said ->
        if (said.isNullOrBlank()) orb = OrbState.Idle else request = said
    }

    fun ask(vararg lines: String) {
        if (session.talksFirst) orb = OrbState.Listening
        prompt.ask(*lines)
    }

    fun send(text: String) {
        if (orb == OrbState.Thinking) return // one request at a time
        prompt.cancel()
        heard = text
        orb = OrbState.Thinking
        scope.launch {
            val turn = runCatching { session.api.turn(text, session.lang) }.getOrNull()
            if (turn == null) {
                val line = "Sorry, I could not do that just now. Please try again."
                reply = line
                orb = OrbState.Speaking
                session.say(line)
                return@launch
            }
            reply = turn.say
            orb = OrbState.Idle
            if (routeTurn(session, turn, actions)) return@launch
            if (session.talksFirst) {
                // A follow-up question ("How much should I send to Tolu?") is its own prompt.
                if (turn.say.trimEnd().endsWith("?")) ask(turn.say) else ask(turn.say, "Anything else?")
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
        if (session.talksFirst) ask(session.greeting(), "What would you like to do?")
    }

    // The orb never stays on Speaking after the voice has stopped.
    LaunchedEffect(talking, orb) {
        if (!talking && orb == OrbState.Speaking) {
            delay(1_500)
            if (!session.speaker.talking.value) orb = OrbState.Idle
        }
    }

    Page(
        top = {
            Brand(Modifier.weight(1f)) {
                IconAction(Glyphs.Settings, "Settings", {
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
                    IconAction(Glyphs.Captions, "Captions", { session.chooseCaptions(!session.captions) }, on = session.captions)
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
                    IconAction(Glyphs.Speaker, "Hear this screen", {
                        orb = OrbState.Speaking
                        session.say(
                            "Home.",
                            "Your balance is " + (account?.let { Naira.spoken(it.balanceKobo) } ?: "loading") + ".",
                            "You can say: send money to Tolu, what came in this week, or block my card.",
                        )
                    })
                }
            }
        },
    ) {
        Heading(session.greeting())
        account?.let { a ->
            BalancePanel("Current account", a.balanceKobo, onHear = {
                orb = OrbState.Speaking
                session.say("You have " + Naira.spoken(a.balanceKobo) + ".")
            })
        }
        if (session.captions) {
            heard?.let { CaptionBox("You said", it) }
            reply?.let { CaptionBox("Òmìnira said", it) }
        }
        if (noMic) Notice(Tone.Info, "Voice is not available on this phone yet.", body = "Tap one of the requests below instead.")
        Kicker("Try saying")
        Suggestion("“Send ₦20,000 to Tolu”", Glyphs.Send) { send("Send twenty thousand to Tolu") }
        Suggestion("“What came in this week?”", Glyphs.Wallet) {
            prompt.cancel()
            actions.balance()
        }
        Suggestion("“Block my card”", Glyphs.Card) { send("Block my card") }
        Suggestion("Sign language help", Glyphs.Sign) {
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

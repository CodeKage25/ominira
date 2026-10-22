package app.ominira.voice

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.ominira.Session
import app.ominira.core.Answers
import app.ominira.core.Command
import app.ominira.core.Commands
import app.ominira.core.Place
import app.ominira.core.Turn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// What a screen hears while it needs an answer. onHeard returns false for words it does not take.
class Claim internal constructor() {
    internal var onHeard: (String) -> Boolean = { false }
    internal var onMissed: ((String) -> Unit)? = null
    internal var onSilence: (() -> Unit)? = null
}

// The one microphone for every screen after onboarding. Heard words go to the screen's claim,
// then to the commands answered on the phone, then to the assistant.
class Voice internal constructor(
    private val session: Session,
    private val scope: CoroutineScope,
    private val lifecycle: Lifecycle,
) {
    internal lateinit var ears: Ears
    internal var place: () -> Place? = { null }
    internal var go: (Command) -> Unit = {}
    internal var routeDraft: (Turn) -> Unit = {}

    var listening by mutableStateOf(false)
        private set
    var thinking by mutableStateOf(false)
        private set
    var heard by mutableStateOf<String?>(null)
        private set

    private val claims = mutableListOf<Claim>()
    private var job: Job? = null
    private var owner: Claim? = null
    private var spokeAt = 0L

    private fun mic(open: Boolean) {
        listening = open
        session.micOpen.value = open
    }

    private fun quiet() {
        job?.cancel()
        job = null
        ears.stop()
        mic(false)
    }

    private fun open() {
        mic(true)
        ears.listen()
    }

    // Says the lines, then listens when asked to. TalkBack on: never listens by itself.
    private fun speakThen(listen: Boolean, speak: () -> Unit) {
        quiet()
        owner = claims.lastOrNull()
        job = scope.launch {
            speak()
            if (!listen || session.screenReaderOn()) return@launch
            session.speaker.awaitQuiet()
            delay(250)
            // The app sent to the background never opens the mic.
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            open()
        }
    }

    // Open answers (names, amounts, digits) pass listen = session.talksFirst: en-NG still hears those.
    fun ask(vararg lines: String, listen: Boolean = session.talksFirst && session.understands) =
        speakThen(listen) { session.say(*lines) }

    // The customer spoke to get to this screen: it talks back even outside voice mode. Read once, on arrival.
    fun cameByVoice(): Boolean {
        val fresh = SystemClock.elapsedRealtime() - spokeAt < 6_000L
        spokeAt = 0L
        return fresh
    }

    // On arrival: said when the app talks first, or when the customer spoke to get here.
    fun arrive(vararg lines: String, listen: Boolean = false) {
        val byVoice = cameByVoice()
        if (!session.talksFirst && !byVoice) return
        ask(*lines, listen = listen && session.understands)
    }

    fun listenNow() {
        quiet()
        session.speaker.stop()
        owner = claims.lastOrNull()
        if (!session.screenReaderOn()) {
            open()
            return
        }
        job = scope.launch {
            val line = session.spoken.listening
            session.announcer?.invoke(line)
            // TalkBack must finish first, or the microphone hears it.
            delay(600L + line.length * 40L)
            open()
        }
    }

    // The dock's button: speak, or stop whatever is going on.
    fun press() {
        if (listening || session.speaker.talking.value || job?.isActive == true) stop() else listenNow()
    }

    fun stop() {
        quiet()
        session.speaker.stop()
    }

    fun cancel() = quiet()

    // A tapped suggestion goes the same way as a spoken request.
    fun hear(text: String) = deliver(text)

    internal fun warned() = mic(false)

    internal fun add(claim: Claim) {
        claims += claim
    }

    internal fun remove(claim: Claim) {
        claims -= claim
        // A screen being left never opens the mic over the next one.
        if (owner === claim) {
            owner = null
            quiet()
        }
    }

    internal fun deliver(text: String?) {
        mic(false)
        val top = claims.lastOrNull()
        if (text == null) {
            top?.onSilence?.invoke()
            return
        }
        // Never shown, never sent anywhere.
        if (Answers.soundsLikePin(text)) {
            heard = null
            session.say(session.spoken.pinSpoken)
            return
        }
        heard = text
        val command = Commands.parse(text)
        if (command != null && command in FIRST) return obey(command)
        if (top != null && top.onHeard(text)) return
        if (command != null) return obey(command)
        val missed = top?.onMissed
        if (missed != null) return missed(text)
        assistant(text)
    }

    private fun obey(command: Command) {
        val s = session.spoken
        val here = place()
        when (command) {
            Command.Stop -> stop()
            Command.Again -> speakThen(session.talksFirst && session.understands) { session.sayAgain() }
            Command.WhereAmI -> ask(*listOfNotNull(here?.let { s.placeName(it) }, here?.let { s.mainAction(it) }, s.commandsList.takeIf { here == null }).toTypedArray())
            Command.Help -> ask(*listOfNotNull(here?.let { s.mainAction(it) }, s.commandsList).toTypedArray())
            Command.Home, Command.Back, Command.Balance, Command.Send, Command.Card, Command.Settings -> {
                quiet()
                spokeAt = SystemClock.elapsedRealtime()
                go(command)
            }
        }
    }

    private fun assistant(text: String) {
        if (thinking) return
        quiet()
        owner = claims.lastOrNull()
        thinking = true
        job = scope.launch {
            val s = session.spoken
            var answer: Turn? = null
            var number = ""
            try {
                // The full number never goes to the assistant: the phone answers this one itself.
                if (Answers.asksAccountNumber(text)) number = session.mainAccount()?.number.orEmpty()
                if (number.isBlank()) answer = runCatching { session.api.turn(text, session.lang) }.getOrNull()
            } finally {
                thinking = false
            }
            // Stopped while the assistant thought: say nothing.
            if (!isActive) return@launch
            val turn = answer
            when {
                number.isNotBlank() -> ask(s.accountNumberIs(s.digits(number)), s.anythingElse)
                turn == null -> session.say(s.couldNotDo)
                turn.draft != null -> {
                    spokeAt = SystemClock.elapsedRealtime()
                    routeDraft(turn)
                }
                !session.talksFirst -> session.say(turn.say)
                turn.say.trimEnd().endsWith("?") -> ask(turn.say)
                else -> ask(turn.say, s.anythingElse)
            }
        }
    }

    private companion object {
        // Said even while a screen waits for an answer: no answer sounds like these.
        val FIRST = setOf(Command.Home, Command.Back, Command.Help, Command.WhereAmI)
    }
}

val LocalVoice = staticCompositionLocalOf<Voice> { error("No voice host above this screen") }

@Composable
fun rememberVoiceHost(session: Session): Voice {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val voice = remember(scope, lifecycle) { Voice(session, scope, lifecycle) }
    voice.ears = rememberEars(session, onWarned = { voice.warned() }) { text -> voice.deliver(text) }
    DisposableEffect(voice) { onDispose { session.micOpen.value = false } }
    return voice
}

// Every screen with the dock calls this once: its asks end when it is left, and its claim hears first.
@Composable
fun rememberVoice(
    onSilence: (() -> Unit)? = null,
    onMissed: ((String) -> Unit)? = null,
    onHeard: (String) -> Boolean = { false },
): Voice {
    val voice = LocalVoice.current
    val claim = remember { Claim() }
    claim.onHeard = onHeard
    claim.onMissed = onMissed
    claim.onSilence = onSilence
    DisposableEffect(voice, claim) {
        voice.add(claim)
        onDispose { voice.remove(claim) }
    }
    return voice
}

package app.ominira.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.ominira.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Asks a question out loud, waits until the question has been said, then listens for the
 * answer. This is how a blind customer moves through the app without finding anything on screen.
 *
 * The microphone only opens by itself when [Session.talksFirst] is true (voice mode, TalkBack off).
 * The answer arrives in the handler given to [rememberPrompt]: the words, or null.
 */
class Prompt internal constructor(
    private val scope: CoroutineScope,
    private val session: Session,
    private val lifecycle: Lifecycle,
) {
    internal lateinit var ears: Ears
    internal var handler: Prompt.(String?) -> Unit = {}
    private var job: Job? = null

    /**
     * Says [lines], then listens. [listen] is true by default only when the app talks first; a
     * "Say it" button passes true so anyone who asked to speak is listened to.
     */
    fun ask(vararg lines: String, listen: Boolean = session.talksFirst) {
        job?.cancel()
        // A recording still open would hear this question and hand it back as the answer.
        ears.stop()
        job = scope.launch {
            session.say(*lines)
            if (!listen) return@launch
            session.speaker.awaitQuiet()
            // TalkBack speaks the lines instead of the app; give it time before the microphone opens.
            delay(if (session.screenReaderOn()) 250L + lines.sumOf { it.length } * 55L else 250L)
            // A screen being left is never resumed again, so its question never opens the
            // microphone over the next screen.
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            ears.listen()
        }
    }

    /** Opens the microphone now, for a "Say it" button. */
    fun listenNow() {
        job?.cancel()
        session.speaker.stop()
        ears.listen()
    }

    /** Stops a question before it listens, for example when the customer taps instead. */
    fun cancel() {
        job?.cancel()
        job = null
        ears.stop()
    }

    internal fun deliver(text: String?) = handler(this, text)
}

@Composable
fun rememberPrompt(
    session: Session,
    quietMs: Long = 1_200,
    askForMic: Boolean = true,
    onWarned: () -> Unit = {},
    onAnswer: Prompt.(heard: String?) -> Unit,
): Prompt {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val prompt = remember(scope, lifecycle) { Prompt(scope, session, lifecycle) }
    prompt.handler = onAnswer
    prompt.ears = rememberEars(session, quietMs, askForMic, onWarned) { text -> prompt.deliver(text) }
    return prompt
}

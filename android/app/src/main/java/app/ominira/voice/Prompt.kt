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

class Prompt internal constructor(
    private val scope: CoroutineScope,
    private val session: Session,
    private val lifecycle: Lifecycle,
) {
    internal lateinit var ears: Ears
    internal var handler: Prompt.(String?) -> Unit = {}
    private var job: Job? = null

    // Open answers (names, amounts, digits) pass listen = session.talksFirst: en-NG still hears those.
    fun ask(vararg lines: String, listen: Boolean = session.talksFirst && session.understands) {
        job?.cancel()
        // An open recording would hear this question and return it as the answer.
        ears.stop()
        job = scope.launch {
            session.say(*lines)
            if (!listen) return@launch
            session.speaker.awaitQuiet()
            // TalkBack is reading the lines; give it time before the mic opens.
            delay(if (session.screenReaderOn()) 250L + lines.sumOf { it.length } * 55L else 250L)
            // A screen being left never resumes, so it never opens the mic over the next.
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            ears.listen()
        }
    }

    fun listenNow() {
        job?.cancel()
        session.speaker.stop()
        ears.listen()
    }

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

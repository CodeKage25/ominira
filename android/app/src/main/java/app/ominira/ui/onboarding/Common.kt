package app.ominira.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.ominira.Session
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.StepDots
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal const val STEPS = 4

// While a screen waits for a tap, say its instruction again after each 15 seconds of quiet, three times
// at most, so a voice customer who missed it is never left in silence. TalkBack users have TalkBack.
// listening: the mic is open or an answer is being worked out; the quiet only counts while it is false.
@Composable
internal fun RemindWhileWaiting(session: Session, waiting: Boolean, listening: () -> Boolean, remind: () -> Unit) {
    val latest by rememberUpdatedState(remind)
    val busy by rememberUpdatedState(listening)
    LaunchedEffect(waiting) {
        if (!waiting || !session.talksFirst) return@LaunchedEffect
        // The screen's opening line first: the count starts once it has begun and ended.
        withTimeoutOrNull(5_000) { session.speaker.talking.first { it } }
        repeat(3) {
            var quietMs = 0L
            while (quietMs < 15_000) {
                delay(500)
                quietMs = if (session.speaker.talking.value || busy()) 0L else quietMs + 500
            }
            latest()
        }
    }
}

@Composable
internal fun RowScope.StepBar(step: Int, onBack: (() -> Unit)?) {
    if (onBack != null) IconAction(Glyphs.Back, Om.words.back, onBack)
    Spacer(Modifier.weight(1f))
    StepDots(step, STEPS)
}

internal fun Modifier.tapAnywhere(enabled: Boolean, label: String, onTap: () -> Unit): Modifier =
    if (enabled) this.clickable(onClickLabel = label, role = Role.Button, onClick = onTap) else this

internal class Once {
    private var used = false
    fun run(action: () -> Unit) {
        if (used) return
        used = true
        action()
    }
}

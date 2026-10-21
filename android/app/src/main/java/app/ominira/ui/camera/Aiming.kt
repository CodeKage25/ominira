package app.ominira.ui.camera

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.ominira.Session
import app.ominira.core.Aim
import app.ominira.core.Framing
import app.ominira.core.Steady
import app.ominira.voice.Tones
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

// Frames in; a sonar ping that rises as the face or card settles, and one spoken direction at a time.
class Aiming internal constructor(private val session: Session, private val lens: Lens) {
    var active by mutableStateOf(false)
        private set
    var aim by mutableStateOf<Aim?>(null)
        private set
    var closeness by mutableFloatStateOf(0f)
        private set

    private val steady = Steady()
    private var gate: CompletableDeferred<Sight>? = null
    private var ready: (Sight) -> Boolean = { true }
    private val minSize = if (lens == Lens.Face) 0.28f else 0.5f
    private val maxSize = if (lens == Lens.Face) 0.7f else 0.95f

    // The frame that held still and passed ready, or null when timeoutMs went by first.
    suspend fun run(timeoutMs: Long = 25_000, ready: (Sight) -> Boolean = { true }): Sight? {
        steady.reset()
        aim = null
        closeness = 0f
        this.ready = ready
        val waiting = CompletableDeferred<Sight>()
        gate = waiting
        active = true
        return try {
            withTimeoutOrNull(timeoutMs) { waiting.await() }
        } finally {
            active = false
            gate = null
        }
    }

    fun onSight(sight: Sight) {
        if (!active) return
        val now = SystemClock.elapsedRealtime()
        val next = if (lens == Lens.Face) Framing.face(sight.seen, sight.luma) else Framing.card(sight.seen, sight.luma)
        aim = next
        closeness = Framing.closeness(sight.seen, minSize, maxSize)
        steady.feed(next, now)?.let { session.say(session.spoken.aim(it, card = lens == Lens.Card)) }
        if (steady.locked(now) && ready(sight)) gate?.complete(sight)
    }
}

@Composable
fun rememberAiming(session: Session, lens: Lens): Aiming {
    val aiming = remember(lens) { Aiming(session, lens) }
    LaunchedEffect(aiming.active) {
        while (aiming.active) {
            Tones.play(330.0 + 560.0 * aiming.closeness, 70, volume = 0.18f)
            delay(650)
        }
    }
    return aiming
}

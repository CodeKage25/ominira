package app.ominira.ui.camera

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Aim
import app.ominira.core.Framing
import app.ominira.core.Mode
import app.ominira.core.Steady
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Om
import app.ominira.voice.Tones
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

// Frames in; a sonar ping that rises as the face or card settles, and one spoken direction at a time.
class Aiming internal constructor(private val session: Session, private val lens: Lens) {
    var active by mutableStateOf(false)
        private set
    var aim by mutableStateOf<Aim?>(null)
        private set
    var closeness by mutableFloatStateOf(0f)
        private set

    private val steady = Steady()
    private var quietSince = 0L
    private var unreadySince: Long? = null
    private var hintAt = 0L
    private var gate: CompletableDeferred<Sight>? = null
    private var ready: (Sight) -> Boolean = { true }
    private var unready: (Sight) -> String? = { null }
    private val minSize = if (lens == Lens.Face) 0.28f else 0.5f
    private val maxSize = if (lens == Lens.Face) 0.7f else 0.95f

    // The frame that held still and passed ready. Guides for as long as it takes. unready says why a
    // held frame failed, or null for the general line.
    suspend fun run(unready: (Sight) -> String? = { null }, ready: (Sight) -> Boolean = { true }): Sight {
        steady.reset()
        unreadySince = null
        aim = null
        closeness = 0f
        this.ready = ready
        this.unready = unready
        val waiting = CompletableDeferred<Sight>()
        gate = waiting
        active = true
        return try {
            waiting.await()
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
        // Never over its own voice: wait for quiet, then a breath, so each line is heard whole.
        // Spoken only to a customer who listens: Voice mode, or TalkBack; others read the panel.
        val listens = Mode.Voice in session.modes || session.screenReaderOn()
        val talking = session.speaker.talking.value
        if (talking) quietSince = now
        val canSpeak = listens && !talking && now - quietSince >= 2_500
        val direction = steady.feed(next, now, canSpeak)
        if (steady.locked(now)) {
            if (ready(sight)) {
                gate?.complete(sight)
                return
            }
            // Held still but unreadable: say why, instead of "keep still" over and over.
            val since = unreadySince ?: now.also { unreadySince = it }
            if (canSpeak && now - since >= 2_500 && now - hintAt >= 6_000) {
                hintAt = now
                session.say(unready(sight) ?: session.spoken.cardUnreadable)
            }
            return
        }
        unreadySince = null
        direction?.let { session.say(session.spoken.aim(it, card = lens == Lens.Card)) }
    }
}

// The direction being given, and a bar that fills as the face or card settles.
@Composable
fun AimPanel(line: String, level: Float, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(Corners.lg)
            .background(Color(0xE606170F))
            .padding(16.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(line, style = Om.type.bodyStrong, color = Om.color.bandText)
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(Corners.pill)
                .background(Color(0x33F7F2E7))
                .clearAndSetSemantics { },
        ) {
            Box(
                Modifier
                    .fillMaxWidth(level.coerceIn(0.001f, 1f))
                    .height(10.dp)
                    .clip(Corners.pill)
                    .background(Brush.horizontalGradient(listOf(Om.color.sun, Om.color.mint))),
            )
        }
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

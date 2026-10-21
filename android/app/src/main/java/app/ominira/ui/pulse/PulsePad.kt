package app.ominira.ui.pulse

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ominira.device.Haptics
import app.ominira.device.Signals
import app.ominira.ui.kit.focusRing
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Om
import app.ominira.voice.Tones
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The moving parts of one Pulse PIN entry. Digits live here only until the fourth is taken. */
@Stable
private class PulseCount {
    var running by mutableStateOf(false)
    var round by mutableIntStateOf(0)
    var start by mutableIntStateOf(0)
    var current by mutableIntStateOf(0)
    var beats by mutableIntStateOf(0)
    var taken by mutableIntStateOf(0)
    var inEars by mutableStateOf(false)
    var lastTap = 0L
    val digits = ArrayList<Int>(4)

    /** Each round starts at a random digit, so the moment of a tap tells an onlooker nothing. */
    fun newRound(context: Context) {
        val s = (0..9).random()
        inEars = Signals.wiredEarphones(context)
        start = s
        current = s
        beats = 0
        running = true
        round += 1
    }

    fun forget() {
        digits.clear()
        taken = 0
    }
}

/**
 * Pulse PIN. Tap to start the count; the phone gives one beat per digit, 0 to 9 and round again,
 * starting somewhere new each time. Zero is a strong double buzz, the landmark to count from.
 * Tap when the count reaches your digit. After each digit the count rests 0.7 s, then starts again.
 *
 * With wired earphones in, the count is quiet ticks in the ears (zero higher and longer) and the
 * phone stays still. Zero also sounds on the loudspeaker while practising, or when the customer
 * turned [zeroSound] on. In [practice] nothing is entered: each tap goes to [onPractice].
 *
 * [onDigits] gets the four digits; seal them at once and never keep them. Put a key() around the
 * pad to start it afresh.
 */
@Composable
fun PulsePad(
    haptics: Haptics,
    gapMs: Long,
    showCount: Boolean,
    onDigits: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    practice: Boolean = false,
    zeroSound: Boolean = false,
    onPractice: (Int) -> Unit = {},
) {
    val c = Om.color
    val context = LocalContext.current
    val pad = remember { PulseCount() }
    val practising by rememberUpdatedState(practice)
    val zeroAloud by rememberUpdatedState(zeroSound)
    val deliver by rememberUpdatedState(onDigits)

    LaunchedEffect(pad.round, pad.running, gapMs) {
        if (!pad.running) return@LaunchedEffect
        fun beat(d: Int) {
            when {
                pad.inEars -> launch {
                    if (d == 0) Tones.play(1175.0, 140, volume = 0.24f) else Tones.play(784.0, 80, volume = 0.16f)
                }
                d == 0 -> {
                    haptics.zero()
                    if (practising || zeroAloud) launch { Tones.play(1175.0, 140, volume = 0.3f) }
                }
                else -> haptics.count()
            }
        }
        // Let the "digit taken" buzz finish, and give a breath, before the next count.
        if (pad.round > 1) delay(700)
        beat(pad.current)
        while (true) {
            delay(gapMs)
            pad.current = (pad.current + 1) % 10
            pad.beats += 1
            beat(pad.current)
        }
    }

    fun tap() {
        if (!enabled) return
        // A shaky second tap just after the first must not count as the next digit.
        val now = SystemClock.uptimeMillis()
        if (now - pad.lastTap < 400) return
        pad.lastTap = now
        if (!pad.running) {
            pad.newRound(context)
            return
        }
        haptics.lock()
        if (practice) {
            pad.running = false
            onPractice(pad.current)
            return
        }
        pad.digits.add(pad.current)
        pad.taken = pad.digits.size
        if (pad.digits.size == 4) {
            val four = pad.digits.toList()
            pad.forget()
            pad.running = false
            deliver(four)
        } else {
            pad.newRound(context)
        }
    }

    val passed: Set<Int> = if (pad.running) (0 until pad.beats).map { (pad.start + it) % 10 }.toSet() else emptySet()
    val interaction = remember { MutableInteractionSource() }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // How many digits are in, never which.
        Row(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = if (practice) "Practice round" else "${pad.taken} of 4 digits entered"
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            repeat(4) { i ->
                val filled = !practice && i < pad.taken
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (filled) c.sun else Color.Transparent)
                        .border(3.dp, if (filled) c.sun else c.bandMuted, CircleShape),
                )
            }
        }

        // The count itself, drawn only when asked for.
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (d in listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)) {
                val now = pad.running && showCount && d == pad.current
                val past = pad.running && showCount && d in passed
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(Corners.pill)
                        .background(
                            when {
                                now -> c.sun
                                past -> c.mint
                                else -> Color(0x2EF7F2E7)
                            },
                        ),
                )
            }
        }

        Text(
            when {
                !enabled -> "Pulse PIN is locked."
                !pad.running -> "Tap the space below to start the count."
                practice -> "Practice: wait for the strong double buzz (zero), count to three, then tap. Nothing is sent."
                pad.inEars -> "Listen in your earphones. Tap at your digit. A higher, longer tick means zero."
                showCount -> "Tap when the count reaches your next digit. A strong double buzz means zero."
                else -> "Feel the count. Tap at your digit. A strong double buzz means zero."
            },
            style = Om.type.body,
            color = c.bandMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .focusRing(interaction, Corners.lg)
                .clip(Corners.lg)
                .background(if (showCount) Color(0x0FF7F2E7) else Color(0x66000000))
                .drawBehind {
                    drawRoundRect(
                        color = Color(0x73F7F2E7),
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                    )
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = if (pad.running) "take this digit" else "start the count",
                    onClick = { tap() },
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pad.running && showCount) {
                    Text(
                        pad.current.toString(),
                        style = Om.type.displayXl.copy(fontSize = Om.type.displayXl.fontSize * 1.6f),
                        color = c.sun,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
                Text(if (pad.running) "Tap anywhere here" else "Start the count", style = Om.type.label, color = c.bandText)
            }
        }
    }
}

package app.ominira.ui.pulse

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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

// Digits live here only until the fourth is taken.
@Stable
private class PulseCount {
    var running by mutableStateOf(false)
    var round by mutableIntStateOf(0)
    var start by mutableIntStateOf(0)
    var current by mutableIntStateOf(0)
    var beats by mutableIntStateOf(0)
    var taken by mutableIntStateOf(0)
    var inEars by mutableStateOf(false)
    // Holding: letting go before zero has been felt is a slip, not a digit.
    var zeroFelt = false
    var lastTap = 0L
    var pressedAt = 0L
    val digits = ArrayList<Int>(4)

    // Random start, so the moment of a tap tells an onlooker nothing.
    fun newRound(context: Context) {
        val s = (0..9).random()
        inEars = Signals.wiredEarphones(context)
        start = s
        current = s
        zeroFelt = s == 0
        beats = 0
        running = true
        round += 1
    }

    fun forget() {
        digits.clear()
        taken = 0
    }
}

// Seal onDigits at once and never keep them. Wrap in key() to restart.
@Composable
fun PulsePad(
    haptics: Haptics,
    gapMs: Long,
    showCount: Boolean,
    onDigits: (List<Int>) -> Unit,
    // Hold to count: press starts the count, letting go takes the digit. Off: tap to start, tap at the digit.
    hold: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    practice: Boolean = false,
    zeroSound: Boolean = false,
    onPractice: (Int) -> Unit = {},
    // Holding: let go before zero, or a touch too short to be a hold.
    onTooSoon: () -> Unit = {},
) {
    val c = Om.color
    val context = LocalContext.current
    val pad = remember { PulseCount() }
    // The gesture outlives recompositions: it reads these, never the values it started with.
    val practising by rememberUpdatedState(practice)
    val zeroAloud by rememberUpdatedState(zeroSound)
    val deliver by rememberUpdatedState(onDigits)
    val practised by rememberUpdatedState(onPractice)
    val tooSoon by rememberUpdatedState(onTooSoon)
    val usable by rememberUpdatedState(enabled)
    val w = Om.words

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
        // Earphones pulled out: not one tone may reach the loudspeaker. The next round goes by vibration.
        fun earsGone(): Boolean {
            if (!pad.inEars || Signals.wiredEarphones(context)) return false
            pad.inEars = false
            pad.running = false
            haptics.stop()
            return true
        }
        // Tapping runs round after round; holding waits for the next press instead.
        if (pad.round > 1 && !hold) delay(700)
        if (earsGone()) return@LaunchedEffect
        beat(pad.current)
        while (true) {
            delay(gapMs)
            if (earsGone()) return@LaunchedEffect
            pad.current = (pad.current + 1) % 10
            if (pad.current == 0) pad.zeroFelt = true
            pad.beats += 1
            beat(pad.current)
        }
    }

    // The count stops on the digit; tapping goes straight into the next round, holding waits for the next press.
    fun take(nextRound: Boolean) {
        haptics.lock()
        if (practising) {
            pad.running = false
            practised(pad.current)
            return
        }
        pad.digits.add(pad.current)
        pad.taken = pad.digits.size
        if (pad.digits.size == 4) {
            val four = pad.digits.toList()
            pad.forget()
            pad.running = false
            deliver(four)
        } else if (nextRound) {
            pad.newRound(context)
        } else {
            pad.running = false
        }
    }

    fun tap() {
        if (!usable) return
        // A shaky second tap just after the first must not count as the next digit.
        val now = SystemClock.uptimeMillis()
        if (now - pad.lastTap < 400) return
        pad.lastTap = now
        if (!pad.running) {
            pad.newRound(context)
            return
        }
        take(nextRound = true)
    }

    fun press() {
        if (!usable || pad.running) return
        pad.pressedAt = SystemClock.uptimeMillis()
        pad.newRound(context)
    }

    // Lifted before zero was felt, lifted within 400 ms, or the gesture was cancelled: the round is dropped,
    // and a long buzz says so. A stray touch on a count that started at zero must not take a 0.
    fun release(lifted: Boolean) {
        if (!pad.running) return
        val early = lifted && (!pad.zeroFelt || SystemClock.uptimeMillis() - pad.pressedAt < 400)
        if (!lifted || early) {
            pad.running = false
            haptics.stop()
            if (early) tooSoon()
            return
        }
        take(nextRound = false)
    }

    val passed: Set<Int> = if (pad.running) (0 until pad.beats).map { (pad.start + it) % 10 }.toSet() else emptySet()
    val interaction = remember { MutableInteractionSource() }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // How many digits are in, never which.
        Row(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = if (practice) w.practiceRound else w.digitsEntered(pad.taken)
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
                !enabled -> w.pinLocked
                hold && !pad.running -> w.holdSpaceToStart
                hold && practice -> w.holdPracticeHint
                hold && pad.inEars -> w.holdEarsHint
                hold && showCount -> w.holdCountHint
                hold -> w.holdFeelHint
                !pad.running -> w.tapSpaceToStart
                practice -> w.practiceHint
                pad.inEars -> w.earsHint
                showCount -> w.countHint
                else -> w.feelHint
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
                .then(
                    if (hold) {
                        Modifier
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    press()
                                    var lifted = false
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            // A cancelled gesture comes up already consumed.
                                            lifted = !change.isConsumed
                                            change.consume()
                                            break
                                        }
                                        // A finger drifting through a long hold must not scroll the page.
                                        change.consume()
                                    }
                                    release(lifted)
                                }
                            }
                            // A keyboard or D-pad Enter is a tap, as it is in tap mode.
                            .onKeyEvent { event ->
                                val enter = event.key == Key.Enter || event.key == Key.NumPadEnter ||
                                    event.key == Key.DirectionCenter || event.key == Key.Spacebar
                                if (enter && event.type == KeyEventType.KeyUp) tap()
                                enter
                            }
                            // TalkBack's double tap arrives as a click, not a press: it counts as tapping.
                            .semantics {
                                role = Role.Button
                                onClick(if (pad.running) w.takeThisDigit else w.startTheCount) {
                                    tap()
                                    true
                                }
                                if (!enabled) disabled()
                            }
                            .focusable(enabled, interaction)
                    } else {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                            role = Role.Button,
                            onClickLabel = if (pad.running) w.takeThisDigit else w.startTheCount,
                            onClick = { tap() },
                        )
                    },
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
                Text(
                    when {
                        hold && pad.running -> w.letGoAtDigit
                        hold -> w.pressAndHold
                        pad.running -> w.tapAnywhereHere
                        else -> w.startTheCount
                    },
                    style = Om.type.label,
                    color = c.bandText,
                )
            }
        }
    }
}

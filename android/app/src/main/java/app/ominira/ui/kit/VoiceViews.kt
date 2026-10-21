package app.ominira.ui.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class OrbState(val label: String) {
    Idle("Tap to talk"),
    Listening("Listening"),
    Thinking("Working it out"),
    Speaking("Speaking"),
}

@Composable
fun Orb(state: OrbState, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 88.dp) {
    val c = Om.color
    val still = rememberReduceMotion()
    val cycle = rememberInfiniteTransition(label = "orb")
    val phase by cycle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val fill = when (state) {
        OrbState.Speaking -> c.mint
        OrbState.Thinking -> c.raised
        OrbState.Idle, OrbState.Listening -> c.sun
    }
    val ink = when (state) {
        OrbState.Speaking -> c.modeInk
        OrbState.Thinking -> c.text
        OrbState.Idle, OrbState.Listening -> c.onSun
    }
    val interaction = remember { MutableInteractionSource() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(size)
                .drawBehind {
                    if (state == OrbState.Listening) {
                        val p = if (still) 0.5f else phase
                        drawCircle(c.sun.copy(alpha = 0.35f * (1f - p)), radius = this.size.minDimension / 2f + 26.dp.toPx() * p)
                    }
                }
                .focusRing(interaction, CircleShape)
                .clip(CircleShape)
                .background(fill)
                .then(if (state == OrbState.Thinking) Modifier.border(3.dp, c.lineStrong, CircleShape) else Modifier)
                .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = if (state == OrbState.Idle) "Talk to Òmìnira" else "Stop"
                    stateDescription = state.label
                },
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                OrbState.Idle, OrbState.Listening ->
                    Icon(Glyphs.Mic, contentDescription = null, tint = ink, modifier = Modifier.size(size * 0.4f))
                OrbState.Thinking -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { i ->
                        val a = if (still) 1f else 0.3f + 0.7f * abs(sin((phase + i * 0.15f) * PI)).toFloat()
                        Box(Modifier.size(12.dp).alpha(a).clip(CircleShape).background(ink))
                    }
                }
                OrbState.Speaking -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(4) { i ->
                        val h = if (still) (if (i % 2 == 0) 34f else 24f) else 12f + 26f * abs(sin((phase * 2f + i * 0.2f) * PI)).toFloat()
                        Box(Modifier.width(7.dp).height(h.dp).clip(Corners.pill).background(ink))
                    }
                }
            }
        }
        Text(
            state.label,
            style = Om.type.smallStrong,
            color = c.muted,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
fun CaptionBox(who: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.md)
            .background(Om.color.infoSoft)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(who, style = Om.type.smallStrong, color = Om.color.info)
        Text(text, style = Om.type.lead, color = Om.color.text)
    }
}

@Composable
fun ChatLine(mine: Boolean, text: String, modifier: Modifier = Modifier) {
    val c = Om.color
    val shape = if (mine) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(if (mine) c.actionSoft else c.raised)
                .border(2.dp, if (mine) c.actionSoft else c.line, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { },
        ) {
            Text(if (mine) "You" else "Òmìnira", style = Om.type.smallStrong, color = c.muted)
            Text(text, style = Om.type.body, color = c.text)
        }
    }
}

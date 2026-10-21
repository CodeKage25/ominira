package app.ominira.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Om

enum class Emphasis { Primary, Secondary, Quiet, Caution, Danger, Sun }

private class ButtonPaint(val fill: Color, val ink: Color, val edge: Color)

/**
 * The Òmìnira button: a word on every button, an icon only beside a word, at least 56dp tall
 * (72dp in plain and switch mode). [busy] keeps the words and adds a ring, so nothing jumps.
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Emphasis = Emphasis.Primary,
    icon: ImageVector? = null,
    wide: Boolean = true,
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Om.color
    val paint = when (style) {
        Emphasis.Primary -> ButtonPaint(c.action, c.onAction, Color.Transparent)
        Emphasis.Secondary -> ButtonPaint(c.raised, c.text, c.lineStrong)
        Emphasis.Quiet -> ButtonPaint(Color.Transparent, c.text, Color.Transparent)
        Emphasis.Caution -> ButtonPaint(c.cautionSoft, c.caution, c.caution)
        Emphasis.Danger -> ButtonPaint(c.dangerSoft, c.danger, c.danger)
        Emphasis.Sun -> ButtonPaint(c.sun, c.onSun, Color.Transparent)
    }
    val fill = paint.fill
    val ink = paint.ink
    val edge = paint.edge
    val interaction = remember { MutableInteractionSource() }
    val shape = Corners.md
    Row(
        modifier
            .then(if (wide) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = Om.size.target)
            .widthIn(min = Om.size.target)
            .focusRing(interaction, shape)
            .clip(shape)
            .background(if (enabled) fill else fill.copy(alpha = fill.alpha * 0.55f))
            .border(2.dp, edge, shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled && !busy,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (busy) stateDescription = "Working" }
            .padding(horizontal = if (style == Emphasis.Quiet) 12.dp else 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(color = ink, strokeWidth = 3.dp, modifier = Modifier.size(20.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
        }
        Text(
            text,
            style = if (Om.size.roomy) Om.type.label.copy(fontSize = Om.type.lead.fontSize) else Om.type.label,
            color = if (enabled) ink else ink.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            textDecoration = if (style == Emphasis.Quiet) TextDecoration.Underline else null,
        )
    }
}

/** A round button with only an icon. It always has a spoken [label]; [on] makes it a toggle. */
@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    on: Boolean? = null,
) {
    val c = Om.color
    val pressed = on == true
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(Om.size.target)
            .focusRing(interaction, CircleShape)
            .clip(CircleShape)
            .background(if (pressed) c.actionSoft else c.raised)
            .border(2.dp, if (pressed) c.action else c.lineStrong, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = if (on == null) Role.Button else Role.Switch,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                if (on != null) stateDescription = if (pressed) "On" else "Off"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (pressed) c.action else c.text, modifier = Modifier.size(24.dp))
    }
}

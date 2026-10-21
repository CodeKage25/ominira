package app.ominira.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ominira.core.Mode
import app.ominira.core.OnboardingCopy
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om

/** The icon for each way in. Names come from [OnboardingCopy], in the customer's language. */
fun modeIcon(mode: Mode): ImageVector = when (mode) {
    Mode.Voice -> Glyphs.Mic
    Mode.Sign -> Glyphs.Sign
    Mode.Switch -> Glyphs.Switch
    Mode.Plain -> Glyphs.Plain
}

@Composable
fun modeColor(mode: Mode): Color = when (mode) {
    Mode.Voice -> Om.color.modeVoice
    Mode.Sign -> Om.color.modeSign
    Mode.Switch -> Om.color.modeSwitch
    Mode.Plain -> Om.color.modePlain
}

/** A round chip in a mode colour with a mode-ink icon. Decorative. */
@Composable
fun ModeChip(color: Color, icon: ImageVector, size: Dp = 52.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Om.color.modeInk, modifier = Modifier.size(size * 0.54f))
    }
}

/** A way in that can be ticked or unticked. Read by TalkBack as a checkbox. */
@Composable
fun WayInTile(mode: Mode, copy: OnboardingCopy, ticked: Boolean, onTick: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Om.color
    val interaction = remember { MutableInteractionSource() }
    val shape = Corners.lg
    Column(
        modifier
            .heightIn(min = 176.dp)
            .focusRing(interaction, shape)
            .clip(shape)
            .background(if (ticked) c.actionSoft else c.raised)
            .border(if (ticked) 4.dp else 2.dp, if (ticked) c.action else c.lineStrong, shape)
            .toggleable(
                value = ticked,
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Checkbox,
                onValueChange = onTick,
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ModeChip(modeColor(mode), modeIcon(mode))
            if (ticked) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(c.action), contentAlignment = Alignment.Center) {
                    Icon(Glyphs.Check, contentDescription = null, tint = c.onAction, modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(copy.modeName(mode), style = Om.type.label, color = c.text)
        Text(copy.modeFor(mode), style = Om.type.small, color = c.muted)
    }
}

/** The ways-in tiles, two to a row. */
@Composable
fun WayInGrid(ticked: Set<Mode>, copy: OnboardingCopy, onChange: (Mode, Boolean) -> Unit) {
    Mode.entries.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { mode ->
                WayInTile(mode, copy, mode in ticked, { on -> onChange(mode, on) }, Modifier.weight(1f))
            }
        }
    }
}

/**
 * A full-width row: a radio choice when [radio] is true, otherwise a plain action.
 * [lang] marks text in another language so TalkBack switches voice for it.
 */
@Composable
fun ChoiceRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    radio: Boolean = true,
    sub: String? = null,
    lang: String? = null,
    minHeight: Dp = 72.dp,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Om.color
    val interaction = remember { MutableInteractionSource() }
    val shape = Corners.md
    val locale = lang?.let { LocaleList(it) }
    val tap = if (radio) {
        Modifier.selectable(selected = selected, interactionSource = interaction, indication = ripple(), role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier.clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = maxOf(minHeight, Om.size.target))
            .focusRing(interaction, shape)
            .clip(shape)
            .background(if (selected) c.actionSoft else c.raised)
            .border(2.dp, if (selected) c.action else c.lineStrong, shape)
            .then(tap)
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        leading?.invoke(this)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Om.type.bodyStrong.copy(fontSize = Om.type.lead.fontSize, localeList = locale), color = c.text)
            if (sub != null) Text(sub, style = Om.type.small.copy(localeList = locale), color = c.muted)
        }
        if (radio) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(if (selected) 8.dp else 3.dp, if (selected) c.action else c.lineStrong, CircleShape)
                    .background(if (selected) c.onAction else Color.Transparent),
            )
        }
    }
}

/** An on or off setting, read by TalkBack as a switch with its state. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, sub: String? = null) {
    val c = Om.color
    val interaction = remember { MutableInteractionSource() }
    val shape = Corners.md
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .focusRing(interaction, shape)
            .clip(shape)
            .background(if (checked) c.actionSoft else c.raised)
            .border(2.dp, if (checked) c.action else c.lineStrong, shape)
            .toggleable(value = checked, interactionSource = interaction, indication = ripple(), role = Role.Switch, onValueChange = onChange)
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Om.type.bodyStrong, color = c.text)
            if (sub != null) Text(sub, style = Om.type.small, color = c.muted)
        }
        Box(
            Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(Corners.pill)
                .background(if (checked) c.action else c.sunk)
                .border(2.dp, if (checked) c.action else c.lineStrong, Corners.pill)
                .padding(4.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(if (checked) c.onAction else c.lineStrong))
        }
    }
}

/** Two to four equal choices side by side: speech speed, Pulse speed. Read as radio buttons. */
@Composable
fun <T> Segments(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Om.color
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Om.size.target)
                    .focusRing(interaction, Corners.md)
                    .clip(Corners.md)
                    .background(if (on) c.action else c.raised)
                    .border(2.dp, if (on) c.action else c.lineStrong, Corners.md)
                    .selectable(selected = on, interactionSource = interaction, indication = ripple(), role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Om.type.label, color = if (on) c.onAction else c.text)
            }
        }
    }
}

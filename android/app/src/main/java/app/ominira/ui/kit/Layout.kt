package app.ominira.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ominira.device.Signals
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Om

@Composable
fun Page(
    top: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Om.color.surface,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    dock: (@Composable () -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    // Insets as one modifier: a caller's modifier must not sit between them.
    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
            .then(modifier),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp)
                .heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = top,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = body,
        )
        if (bottom != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = bottom,
            )
        }
        dock?.invoke()
    }
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier, color: Color = Om.color.text) {
    Text(text, style = Om.type.title, color = color, modifier = modifier.semantics { heading() })
}

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Om.color.muted) {
    Text(text.uppercase(), style = Om.type.kicker, color = color, modifier = modifier)
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = Om.color.muted) {
    Text(text, style = Om.type.small, color = color, modifier = modifier)
}

@Composable
fun Modifier.focusRing(interaction: MutableInteractionSource, shape: Shape): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    return if (focused) this.border(Om.size.focusRing, Om.color.focus, shape) else this
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { Signals.reduceMotion(context) }
}

@Composable
fun Mark(size: Dp = 32.dp, onDark: Boolean = Om.color.dark) {
    val dot = if (onDark) Color(0xFFFFC845) else Color(0xFF0E2A20)
    val wave = if (onDark) Color(0xFF4FD69C) else Color(0xFF0D6440)
    Canvas(Modifier.size(size).clearAndSetSemantics { }) {
        val u = this.size.width / 100f
        drawCircle(dot, radius = 11f * u, center = Offset(22f * u, 20f * u))
        drawCircle(dot, radius = 11f * u, center = Offset(22f * u, 80f * u))
        drawCircle(dot, radius = 11f * u, center = Offset(52f * u, 50f * u))
        val pen = Stroke(width = 6f * u, cap = StrokeCap.Round)
        drawArc(wave, -45f, 90f, false, Offset((52f - 26f) * u, (50f - 26f) * u), Size(52f * u, 52f * u), style = pen)
        drawArc(wave.copy(alpha = 0.6f), -45f, 90f, false, Offset((52f - 41f) * u, (50f - 41f) * u), Size(82f * u, 82f * u), style = pen)
    }
}

@Composable
fun Brand(modifier: Modifier = Modifier, onDark: Boolean = Om.color.dark, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Mark(32.dp, onDark)
        Text(
            "Òmìnira",
            style = Om.type.brand,
            color = if (onDark && !Om.color.dark) Om.color.bandText else Om.color.text,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
fun StepDots(step: Int, of: Int, modifier: Modifier = Modifier) {
    val label = Om.words.stepOf(step, of)
    Row(
        modifier.clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(of) { i ->
            Box(
                Modifier
                    .size(width = 28.dp, height = 6.dp)
                    .background(if (i < step) Om.color.action else Om.color.line, Corners.pill),
            )
        }
    }
}

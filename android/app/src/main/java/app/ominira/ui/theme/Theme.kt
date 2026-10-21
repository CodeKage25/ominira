package app.ominira.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ominira.core.Lang
import app.ominira.core.Words

@Immutable
class Sizes(val target: Dp, val roomy: Boolean) {
    val gutter: Dp = 24.dp
    val gap: Dp = 16.dp
    val focusRing: Dp = 3.dp
}

object Corners {
    val sm = RoundedCornerShape(10.dp)
    val md = RoundedCornerShape(18.dp)
    val lg = RoundedCornerShape(28.dp)
    val xl = RoundedCornerShape(40.dp)
    val pill = RoundedCornerShape(percent = 50)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

private val LocalPalette = staticCompositionLocalOf { PaperPalette }
private val LocalTypeScale = staticCompositionLocalOf { TypeScale() }
private val LocalSizes = staticCompositionLocalOf { Sizes(56.dp, roomy = false) }
private val LocalWords = staticCompositionLocalOf { Words.of(Lang.En) }

object Om {
    val color: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
    val type: TypeScale
        @Composable @ReadOnlyComposable get() = LocalTypeScale.current
    val size: Sizes
        @Composable @ReadOnlyComposable get() = LocalSizes.current
    val words: Words
        @Composable @ReadOnlyComposable get() = LocalWords.current
}

@Composable
fun OminiraTheme(look: Look, roomy: Boolean, lang: Lang, content: @Composable () -> Unit) {
    val palette = paletteFor(look)
    val type = remember(roomy) { TypeScale(if (roomy) 1.15f else 1f) }
    val sizes = remember(roomy) { Sizes(if (roomy) 72.dp else 56.dp, roomy) }

    val scheme = if (palette.dark) {
        darkColorScheme(
            primary = palette.action, onPrimary = palette.onAction,
            background = palette.surface, onBackground = palette.text,
            surface = palette.surface, onSurface = palette.text,
            surfaceVariant = palette.sunk, onSurfaceVariant = palette.muted,
            outline = palette.lineStrong, error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = palette.action, onPrimary = palette.onAction,
            background = palette.surface, onBackground = palette.text,
            surface = palette.surface, onSurface = palette.text,
            surfaceVariant = palette.sunk, onSurfaceVariant = palette.muted,
            outline = palette.lineStrong, error = palette.danger,
        )
    }

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalPalette provides palette,
            LocalTypeScale provides type,
            LocalSizes provides sizes,
            LocalWords provides Words.of(lang),
            LocalContentColor provides palette.text,
            LocalTextStyle provides type.body,
            content = content,
        )
    }
}

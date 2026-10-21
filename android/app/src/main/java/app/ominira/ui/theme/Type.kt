package app.ominira.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.ominira.R

val Andika = FontFamily(
    Font(R.font.andika_regular, FontWeight.Normal),
    Font(R.font.andika_bold, FontWeight.Bold),
)

val Unbounded = FontFamily(
    Font(R.font.unbounded_semibold, FontWeight.SemiBold),
    Font(R.font.unbounded_bold, FontWeight.Bold),
    Font(R.font.unbounded_extrabold, FontWeight.ExtraBold),
)

@Immutable
class TypeScale(private val scale: Float = 1f) {
    private fun size(v: Float): TextUnit = (v * scale).sp

    private fun unbounded(weight: FontWeight, s: Float, lh: Float, track: Float) =
        TextStyle(fontFamily = Unbounded, fontWeight = weight, fontSize = size(s), lineHeight = size(lh), letterSpacing = track.sp)

    private fun andika(weight: FontWeight, s: Float, lh: Float) =
        TextStyle(fontFamily = Andika, fontWeight = weight, fontSize = size(s), lineHeight = size(lh))

    val displayXl = unbounded(FontWeight.ExtraBold, 44f, 46f, -1f)
    val displayM = unbounded(FontWeight.Bold, 34f, 38f, -0.8f)
    val title = unbounded(FontWeight.Bold, 26f, 31f, -0.4f)
    val titleSmall = unbounded(FontWeight.Bold, 22f, 27f, -0.3f)
    val amount = unbounded(FontWeight.Bold, 46f, 50f, -1f)
    val kicker = unbounded(FontWeight.Bold, 17f, 22f, 1.2f)
    val brand = unbounded(FontWeight.Bold, 20f, 24f, -0.3f)

    val readback = andika(FontWeight.Bold, 26f, 34f)
    val lead = andika(FontWeight.Normal, 23f, 34f)
    val body = andika(FontWeight.Normal, 20f, 30f)
    val bodyStrong = andika(FontWeight.Bold, 20f, 29f)
    val label = andika(FontWeight.Bold, 20f, 24f)
    val small = andika(FontWeight.Normal, 17f, 25f)
    val smallStrong = andika(FontWeight.Bold, 17f, 24f)
    val digits = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = size(24f), lineHeight = size(30f), letterSpacing = 3.sp)
}

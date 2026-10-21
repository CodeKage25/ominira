package app.ominira.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// A path starting with "*" is filled instead of stroked.
object Glyphs {
    val Mic by lazy { glyph("Mic", "M12 3H12A3 3 0 0 1 15 6V11A3 3 0 0 1 12 14H12A3 3 0 0 1 9 11V6A3 3 0 0 1 12 3z", "M5.5 11a6.5 6.5 0 0 0 13 0", "M12 17.5V21", "M8.5 21h7") }
    val Speaker by lazy { glyph("Speaker", "M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M15.5 9a4 4 0 0 1 0 6", "M18.5 6.5a7.5 7.5 0 0 1 0 11") }
    val Captions by lazy { glyph("Captions", "M6 5.5H18A3 3 0 0 1 21 8.5V15.5A3 3 0 0 1 18 18.5H6A3 3 0 0 1 3 15.5V8.5A3 3 0 0 1 6 5.5z", "M10.5 10.2a2.4 2.4 0 1 0 0 3.6", "M17 10.2a2.4 2.4 0 1 0 0 3.6") }
    val Sign by lazy { glyph("Sign", "M8 12.5V6.2a1.4 1.4 0 0 1 2.8 0V11", "M10.8 10V4.6a1.4 1.4 0 0 1 2.8 0V11", "M13.6 10.3V5.8a1.4 1.4 0 0 1 2.8 0V12", "M16.4 9.6a1.4 1.4 0 0 1 2.8 0V14a7 7 0 0 1-7 7h-.6a6 6 0 0 1-4.9-2.6L4 14.6a1.5 1.5 0 0 1 2.4-1.7L8 14.6") }
    val Switch by lazy { glyph("Switch", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0z", "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z") }
    val Plain by lazy { glyph("Plain", "M4 18.5 9 5.5h1l5 13", "M6 13.5h7", "M17 9.5h3.5", "M17 14h3.5") }
    val Send by lazy { glyph("Send", "M4 12h14", "M13 6.5 18.5 12 13 17.5") }
    val Wallet by lazy { glyph("Wallet", "M4 7.5a2 2 0 0 1 2-2h11.5v3", "M6.5 8.5H18A2.5 2.5 0 0 1 20.5 11V16.5A2.5 2.5 0 0 1 18 19H6.5A2.5 2.5 0 0 1 4 16.5V11A2.5 2.5 0 0 1 6.5 8.5z", "*M14.8 13.75a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z") }
    val Card by lazy { glyph("Card", "M5.5 6H18.5A2.5 2.5 0 0 1 21 8.5V16A2.5 2.5 0 0 1 18.5 18.5H5.5A2.5 2.5 0 0 1 3 16V8.5A2.5 2.5 0 0 1 5.5 6z", "M3 10.5h18", "M7 15h4") }
    val Lock by lazy { glyph("Lock", "M7.5 10.5H16.5A2.5 2.5 0 0 1 19 13V18A2.5 2.5 0 0 1 16.5 20.5H7.5A2.5 2.5 0 0 1 5 18V13A2.5 2.5 0 0 1 7.5 10.5z", "M8 10.5V8a4 4 0 0 1 8 0v2.5", "M12 14.5v2.5") }
    val Pulse by lazy { glyph("Pulse", "M10.5 3.5H13.5A2.5 2.5 0 0 1 16 6V18A2.5 2.5 0 0 1 13.5 20.5H10.5A2.5 2.5 0 0 1 8 18V6A2.5 2.5 0 0 1 10.5 3.5z", "M4.5 9v6", "M19.5 9v6", "M2 10.5v3", "M22 10.5v3") }
    val Camera by lazy { glyph("Camera", "M4 8.5a2 2 0 0 1 2-2h2l1.5-2h5l1.5 2h2a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z", "M8.5 12.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0z") }
    val Sonar by lazy { glyph("Sonar", "*M9.8 12a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0z", "M7.8 7.8a6 6 0 0 0 0 8.4", "M16.2 7.8a6 6 0 0 1 0 8.4", "M4.9 4.9a10 10 0 0 0 0 14.2", "M19.1 4.9a10 10 0 0 1 0 14.2") }
    val IdCard by lazy { glyph("IdCard", "M5.5 5H18.5A2.5 2.5 0 0 1 21 7.5V16.5A2.5 2.5 0 0 1 18.5 19H5.5A2.5 2.5 0 0 1 3 16.5V7.5A2.5 2.5 0 0 1 5.5 5z", "M6.8 11a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0z", "M5.8 16.2c.6-1.6 1.8-2.4 3.2-2.4s2.6.8 3.2 2.4", "M14.5 10h3.5", "M14.5 13.5h3.5") }
    val Check by lazy { glyph("Check", "M5 12.5 10 17.5 19 7") }
    val Pause by lazy { glyph("Pause", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z", "M10 9v6", "M14 9v6") }
    val Alert by lazy { glyph("Alert", "M12 3.5 21.5 19.5h-19z", "M12 10v4", "*M10.9 16.9a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0z") }
    val Info by lazy { glyph("Info", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z", "M12 11v5.5", "*M10.9 7.8a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0z") }
    val People by lazy { glyph("People", "M6 8.5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z", "M3.5 19c.8-3.2 3-5 5.5-5s4.7 1.8 5.5 5", "M14.1 9.5a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0z", "M15.5 14.2c2.4 0 4.3 1.5 5 4.3") }
    val Shield by lazy { glyph("Shield", "M12 3 5 5.8v5.4c0 4.3 2.9 7.9 7 9.3 4.1-1.4 7-5 7-9.3V5.8z", "M8.8 12.2 11 14.4l4.2-4.4") }
    val Language by lazy { glyph("Language", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z", "M3 12h18", "M12 3c2.6 2.6 3.8 5.6 3.8 9s-1.2 6.4-3.8 9c-2.6-2.6-3.8-5.6-3.8-9S9.4 5.6 12 3z") }
    val Phone by lazy { glyph("Phone", "M5.5 4h3.2l1.6 4.2-2 1.4a10.5 10.5 0 0 0 6.1 6.1l1.4-2 4.2 1.6v3.2a1.6 1.6 0 0 1-1.7 1.6C10.9 19.6 4.4 13.1 3.9 5.7A1.6 1.6 0 0 1 5.5 4z") }
    val Repeat by lazy { glyph("Repeat", "M4.5 10a7.5 7.5 0 0 1 13.4-3.5L20 8.5", "M20 4v4.5h-4.5", "M19.5 14a7.5 7.5 0 0 1-13.4 3.5L4 15.5", "M4 20v-4.5h4.5") }
    val Close by lazy { glyph("Close", "M6 6l12 12", "M18 6 6 18") }
    val Back by lazy { glyph("Back", "M20 12H6", "M11 6.5 5.5 12l5.5 5.5") }
    val Settings by lazy { glyph("Settings", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z", "M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7") }
    val Home by lazy { glyph("Home", "M4 10.5 12 4l8 6.5V19a1.5 1.5 0 0 1-1.5 1.5H15V15h-6v5.5H5.5A1.5 1.5 0 0 1 4 19z") }
}

private fun glyph(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    for (d in paths) {
        if (d.startsWith("*")) {
            builder.addPath(pathData = addPathNodes(d.substring(1)), fill = SolidColor(Color.Black))
        } else {
            builder.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }
    return builder.build()
}

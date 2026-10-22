package app.ominira.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om

enum class Grouping { None, Fours, Thousands }

@Composable
fun DigitField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    maxDigits: Int = 11,
    grouping: Grouping = Grouping.Fours,
) {
    val c = Om.color
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = Om.type.label, color = c.text)
        if (hint != null) Text(hint, style = Om.type.small, color = c.muted)
        BasicTextField(
            value = value,
            onValueChange = { typed -> onValueChange(typed.filter { it in '0'..'9' }.take(maxDigits)) },
            singleLine = true,
            textStyle = Om.type.digits.copy(color = c.text),
            cursorBrush = SolidColor(c.text),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = when (grouping) {
                Grouping.None -> VisualTransformation.None
                Grouping.Fours -> SeparatedDigits { raw -> raw.chunked(4).joinToString(" ") }
                Grouping.Thousands -> SeparatedDigits { raw -> raw.reversed().chunked(3).joinToString(",").reversed() }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Om.size.target)
                .onFocusChanged { focused = it.isFocused }
                .background(c.raised, Corners.sm)
                .border(
                    width = if (error != null || focused) 3.dp else 2.dp,
                    color = when {
                        error != null -> c.danger
                        focused -> c.focus
                        else -> c.lineStrong
                    },
                    shape = Corners.sm,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics {
                    contentDescription = label
                    val problem = error
                    if (problem != null) this.error(problem)
                },
        )
        if (error != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Glyphs.Alert, contentDescription = null, tint = c.danger, modifier = Modifier.padding(top = 2.dp).size(22.dp))
                Text(error, style = Om.type.bodyStrong, color = c.danger)
            }
        }
    }
}

private class SeparatedDigits(private val separate: (String) -> String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val shown = separate(raw)
        val toShown = IntArray(raw.length + 1)
        val toRaw = IntArray(shown.length + 1)
        var r = 0
        for (t in shown.indices) {
            toRaw[t] = r
            if (shown[t] in '0'..'9' && r < raw.length) {
                toShown[r] = t
                r++
            }
        }
        toRaw[shown.length] = raw.length
        toShown[raw.length] = shown.length
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = toShown[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int): Int = toRaw[offset.coerceIn(0, shown.length)]
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }

    // Compose compares transformations to decide whether to re-run them.
    override fun equals(other: Any?): Boolean = other is SeparatedDigits && other.separate === separate
    override fun hashCode(): Int = System.identityHashCode(separate)
}

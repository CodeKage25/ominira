package app.ominira.ui.kit

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

// Yes and no without sight or speech: tap anywhere, or touch and hold. Buttons on the screen keep their own taps.
fun Modifier.touchAnswer(enabled: Boolean, onTap: () -> Unit, onHold: () -> Unit): Modifier = composed {
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    if (enabled) Modifier.pointerInput(Unit) { detectTapGestures(onTap = { tap() }, onLongPress = { hold() }) } else Modifier
}

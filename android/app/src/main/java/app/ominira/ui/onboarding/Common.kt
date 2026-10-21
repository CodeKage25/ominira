package app.ominira.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.StepDots
import app.ominira.ui.theme.Glyphs

/** After the language, onboarding has four steps with dots: ways in, selfie, ID, BVN. */
internal const val STEPS = 4

/** The top row of an onboarding step: back, then where you are. */
@Composable
internal fun RowScope.StepBar(step: Int, onBack: (() -> Unit)?) {
    if (onBack != null) IconAction(Glyphs.Back, "Back", onBack)
    Spacer(Modifier.weight(1f))
    StepDots(step, STEPS)
}

/** Makes a whole screen one button, so "tap anywhere" is literally true. */
internal fun Modifier.tapAnywhere(enabled: Boolean, label: String, onTap: () -> Unit): Modifier =
    if (enabled) this.clickable(onClickLabel = label, role = Role.Button, onClick = onTap) else this

/** Runs [action] once, however many taps or answers ask for it. */
internal class Once {
    private var used = false
    fun run(action: () -> Unit) {
        if (used) return
        used = true
        action()
    }
}

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
import app.ominira.ui.theme.Om

internal const val STEPS = 4

@Composable
internal fun RowScope.StepBar(step: Int, onBack: (() -> Unit)?) {
    if (onBack != null) IconAction(Glyphs.Back, Om.words.back, onBack)
    Spacer(Modifier.weight(1f))
    StepDots(step, STEPS)
}

internal fun Modifier.tapAnywhere(enabled: Boolean, label: String, onTap: () -> Unit): Modifier =
    if (enabled) this.clickable(onClickLabel = label, role = Role.Button, onClick = onTap) else this

internal class Once {
    private var used = false
    fun run(action: () -> Unit) {
        if (used) return
        used = true
        action()
    }
}

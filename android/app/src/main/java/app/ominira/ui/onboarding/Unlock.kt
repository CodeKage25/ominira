package app.ominira.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.PinBlock
import app.ominira.core.Unlock
import app.ominira.ui.kit.Mark
import app.ominira.ui.pulse.PulseStep
import app.ominira.ui.theme.Om
import app.ominira.voice.Tones
import kotlinx.coroutines.launch

@Composable
fun UnlockScreen(session: Session, onUnlocked: () -> Unit) {
    val scope = rememberCoroutineScope()
    val leave = remember { Once() }
    var attempt by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }

    val w = session.words
    val s = session.spoken

    fun home() = leave.run(onUnlocked)

    PulseStep(
        session = session,
        title = w.yourPulsePin(session.customerName),
        attempt = attempt,
        problem = problem,
        busy = busy,
        locked = locked,
        topStart = {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Mark(32.dp, onDark = true)
                Text("Òmìnira", style = Om.type.brand, color = Om.color.bandText)
            }
        },
        doneLine = if (unlocked) w.unlocked else null,
        doneAction = w.goToMyHome,
        onDoneAction = { home() },
        onDigits = { digits ->
            // Sealed at once; the digits are not kept.
            val block = PinBlock.seal(digits)
            busy = true
            problem = null
            scope.launch {
                val answer = runCatching { session.api.unlock(block) }.getOrNull()
                busy = false
                when (answer) {
                    is Unlock.Open -> {
                        unlocked = true
                        session.haptics.done()
                        session.say(s.unlocked)
                        Tones.done()
                        if (session.talksFirst) {
                            session.speaker.awaitQuiet()
                            home()
                        }
                    }
                    is Unlock.Wrong -> {
                        attempt += 1
                        locked = answer.triesLeft == 0
                        problem = answer.say
                        session.haptics.stop()
                        session.say(answer.say)
                    }
                    null -> {
                        attempt += 1
                        problem = w.pinCheckFailed
                        session.say(s.pinCheckFailed)
                    }
                }
            }
        },
    )
}

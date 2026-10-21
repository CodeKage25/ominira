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
import app.ominira.core.PinRules
import app.ominira.ui.kit.Mark
import app.ominira.ui.pulse.PulseStep
import app.ominira.ui.theme.Om
import app.ominira.voice.Tones
import kotlinx.coroutines.launch

// The new customer chooses their own Pulse PIN: pulsed twice, easy ones refused, then sealed for the bank.
@Composable
fun SetPinScreen(session: Session, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val leave = remember { Once() }
    val w = session.words
    val s = session.spoken
    // The first pulsing, in memory only until the second one matches it.
    var first by remember { mutableStateOf<List<Int>?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var set by remember { mutableStateOf(false) }
    val arrival = remember { listOf(s.choosePinIntro, session.pulseIntro()) }

    fun home() = leave.run(onDone)

    // A fresh pad each time, with the line to show and the line to say.
    fun again(shown: String?, said: String) {
        attempt += 1
        problem = shown
        session.haptics.stop()
        session.say(said)
    }

    fun save(digits: List<Int>) {
        // Sealed at once; the digits are not kept.
        val block = PinBlock.seal(digits)
        first = null
        busy = true
        problem = null
        scope.launch {
            val ok = runCatching { session.api.setPin(session.onboardingSession(), block) }.getOrDefault(false)
            busy = false
            if (!ok) {
                again(w.pinSetFailed, s.pinSetFailed)
                return@launch
            }
            set = true
            session.haptics.done()
            session.say(s.pinSet)
            Tones.done()
            if (session.talksFirst) {
                session.speaker.awaitQuiet()
                home()
            }
        }
    }

    PulseStep(
        session = session,
        title = if (first == null) w.choosePinTitle else w.confirmPinTitle,
        attempt = attempt,
        problem = problem,
        busy = busy,
        topStart = {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Mark(32.dp, onDark = true)
                Text("Òmìnira", style = Om.type.brand, color = Om.color.bandText)
            }
        },
        doneLine = if (set) w.pinSet else null,
        doneAction = w.goToMyHome,
        onDoneAction = { home() },
        arrival = arrival,
        onDigits = { digits ->
            val earlier = first
            when {
                earlier == null -> when (PinRules.check(digits, session.idOnFile?.bornOn)) {
                    PinRules.Weak.Easy -> again(w.pinTooEasy, s.pinTooEasy)
                    PinRules.Weak.Birthday -> again(w.pinIsBirthday, s.pinIsBirthday)
                    null -> {
                        first = digits
                        again(null, s.confirmPinSay)
                    }
                }
                earlier != digits -> {
                    first = null
                    again(w.pinMismatch, s.pinMismatch)
                }
                else -> save(digits)
            }
        },
    )
}

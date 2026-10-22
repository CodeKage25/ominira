package app.ominira.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.ominira.Session
import app.ominira.core.Answers
import app.ominira.core.IdDetails
import app.ominira.ui.kit.ActionButton
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.Kicker
import app.ominira.ui.kit.NameField
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.Tone
import app.ominira.voice.rememberPrompt
import kotlinx.coroutines.launch

// The customer's name, said or typed. A real bank reads it from the BVN record; the demo asks for it.
@Composable
fun YourNameScreen(session: Session, onBack: () -> Unit, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    val leave = remember { Once() }
    var name by remember { mutableStateOf("") }
    var heard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val w = session.words
    val s = session.spoken

    fun save() {
        val full = name.trim()
        if (full.length < 2 || saving) return
        saving = true
        problem = null
        scope.launch {
            val ok = runCatching {
                session.api.readIdDocument(session.onboardingSession(), IdDetails(full, "", ""))
            }.isSuccess
            saving = false
            if (ok) {
                session.speaker.stop()
                leave.run(onNext)
            } else {
                problem = w.nameFailed
                session.say(s.nameFailed)
            }
        }
    }

    val prompt = rememberPrompt(session) { said ->
        when {
            said.isNullOrBlank() -> Unit
            heard && Answers.isYes(said) -> save()
            else -> {
                name = cleanName(said)
                heard = true
                ask(s.nameHeard(name), listen = session.talksFirst)
            }
        }
    }

    LaunchedEffect(Unit) {
        // Names are open answers: the phone hears them even where it cannot hear a yes in the language.
        if (session.talksFirst) prompt.ask(s.askName, s.sayOrTypeName, listen = true)
    }

    Page(
        top = { StepBar(3, onBack) },
        bottom = {
            ActionButton(w.nameSave, busy = saving, onClick = {
                prompt.cancel()
                save()
            })
        },
    ) {
        Kicker(w.nameKicker)
        Heading(w.askName)
        NameField(w.nameLabel, name, { name = it; heard = false }, hint = w.nameHint)
        problem?.let { Notice(Tone.Caution, it) }
    }
}

// "My name is Adebayo Tolulope." -> "Adebayo Tolulope"
internal fun cleanName(said: String): String {
    val lead = Regex("^(my name is|my full name is|i am|i'm|it is|it's|orúkọ mi ni|orukọ mi ni|sunana|aha m bụ)\\s+", RegexOption.IGNORE_CASE)
    return said.trim().trimEnd('.', '!', '?').replace(lead, "")
        .split(Regex("\\s+")).filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
}

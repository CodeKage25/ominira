package app.ominira.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Answers
import app.ominira.core.Lang
import app.ominira.core.LanguagePrompts
import app.ominira.core.Mode
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.focusRing
import app.ominira.ui.theme.Andika
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.Clips
import app.ominira.voice.rememberEars
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One answer from the microphone. A new object each time, so the same words twice still count. */
private class Answer(val text: String?)

/**
 * The very first screen, before anything in English is asked: not everyone speaks English.
 *
 * It speaks straight away, one line per language, each in that language and its own voice:
 * a bundled clip if there is one, else the Gateway's voice, else the phone's voice for English and
 * Pidgin (a line is skipped rather than read in Yorùbá, Hausa or Igbo by an English voice). Then it
 * listens for a language name. Nothing heard: the lines play once more, then it waits for a tap.
 *
 * Five big bands, each labelled only in its own language, are the touch way in. With TalkBack on
 * nothing plays and the microphone stays shut: the bands are the interface.
 *
 * From Settings ([fromSettings]), it plays only in voice mode, and choosing goes back.
 */
@Composable
fun LanguageScreen(session: Session, fromSettings: Boolean, onBack: (() -> Unit)?, onChosen: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val leave = remember { Once() }
    val readerOn = remember { session.screenReaderOn() }
    val autoplay = !readerOn && (!fromSettings || Mode.Voice in session.modes)
    var rounds by remember { mutableIntStateOf(0) }
    var listening by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<Answer?>(null) }
    var roll by remember { mutableStateOf<Job?>(null) }

    val ears = rememberEars(session, askForMic = false) { text ->
        listening = false
        answer = Answer(text)
    }

    /** May the phone's own engine say a line in [lang]? English and Pidgin, or a real voice for it. */
    fun phoneMay(lang: Lang): Boolean = lang == Lang.En || lang == Lang.Pcm || session.speaker.hasPhoneVoiceFor(lang)

    suspend fun sayLine(lang: Lang, text: String, clip: String?) {
        if (clip != null && Clips.play(context, clip)) return
        session.speaker.speakAndWait(lang, text, phoneFallback = phoneMay(lang))
    }

    fun playRoll() {
        roll?.cancel()
        ears.stop()
        roll = scope.launch {
            rounds += 1
            for (lang in LanguagePrompts.order) {
                sayLine(lang, LanguagePrompts.line(lang), LanguagePrompts.clipName(lang))
                delay(250)
            }
            listening = true
            ears.listen()
        }
    }

    fun choose(lang: Lang) = leave.run {
        roll?.cancel()
        ears.stop()
        listening = false
        session.speaker.stop()
        session.chooseLang(lang)
        session.haptics.lock()
        scope.launch {
            val line = LanguagePrompts.chosen(lang)
            if (readerOn) session.sayIn(lang, line) else sayLine(lang, line, null)
            onChosen()
        }
    }

    LaunchedEffect(answer) {
        val a = answer ?: return@LaunchedEffect
        val lang = a.text?.let { Answers.language(it) }
        when {
            lang != null -> choose(lang)
            rounds < 2 -> playRoll()
            else -> Unit // wait for a tap
        }
    }

    LaunchedEffect(Unit) {
        if (autoplay) playRoll()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Om.color.surface)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null || listening) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconAction(Glyphs.Back, "Back", {
                        roll?.cancel()
                        ears.stop()
                        session.speaker.stop()
                        onBack()
                    })
                }
                Box(Modifier.weight(1f))
                // Decorative: the rising tone already says "speak now".
                if (listening) Icon(Glyphs.Mic, contentDescription = null, tint = Om.color.action, modifier = Modifier.size(32.dp))
            }
        }
        for (lang in LanguagePrompts.order) {
            LanguageBand(lang, selected = fromSettings && session.lang == lang, modifier = Modifier.weight(1f)) { choose(lang) }
        }
    }
}

/**
 * One language, labelled only in its own words. The words carry their language, so TalkBack reads
 * each band in that language's voice where the phone has one.
 */
@Composable
private fun LanguageBand(lang: Lang, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Om.color
    val interaction = remember { MutableInteractionSource() }
    val locale = LocaleList(lang.code)
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = Om.size.target)
            .focusRing(interaction, Corners.lg)
            .clip(Corners.lg)
            .background(if (selected) c.actionSoft else c.raised)
            .border(if (selected) 4.dp else 2.dp, if (selected) c.action else c.lineStrong, Corners.lg)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            lang.nativeName,
            style = Om.type.displayM.copy(fontFamily = Andika, fontWeight = FontWeight.Bold, localeList = locale),
            color = c.text,
        )
        Text(
            LanguagePrompts.line(lang),
            style = Om.type.small.copy(localeList = locale),
            color = c.muted,
            // The band is named by the language alone; the spoken prompt is for sighted readers.
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

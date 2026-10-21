package app.ominira.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ominira.BuildConfig
import app.ominira.Session
import app.ominira.core.Mode
import app.ominira.core.OnboardingCopy
import app.ominira.ui.kit.ActionButton
import app.ominira.ui.kit.ChoiceRow
import app.ominira.ui.kit.Emphasis
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.Hint
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.Kicker
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.Segments
import app.ominira.ui.kit.SwitchRow
import app.ominira.ui.kit.WayInGrid
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Look
import app.ominira.ui.theme.Om
import app.ominira.ui.theme.paletteFor
import kotlin.math.abs

/** The nearest of [options] to [value], so an old stored value still shows a choice. */
private fun <T : Comparable<T>> nearest(options: List<T>, value: T, distance: (T, T) -> Double): T =
    options.minByOrNull { distance(it, value) } ?: options.first()

/** Look and sound, ways in, language, and the switches for the demo. Every choice is remembered. */
@Composable
fun SettingsScreen(session: Session, onBack: () -> Unit, onLanguage: () -> Unit, onRestart: () -> Unit) {
    Page(
        top = {
            IconAction(Glyphs.Back, "Back", onBack)
            Heading("Look and sound", Modifier.weight(1f))
        },
        bottom = {
            ActionButton("Hear a sample", onClick = {
                session.say("This is how I will sound.", "Twenty thousand naira to Adebayo Tolulope.")
            }, icon = Glyphs.Speaker)
        },
    ) {
        Label("Theme")
        for (look in Look.entries) {
            val swatch = paletteFor(look)
            ChoiceRow(
                title = look.label,
                onClick = { session.chooseLook(look) },
                selected = session.look == look,
                minHeight = 64.dp,
                leading = {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(swatch.surface).border(3.dp, swatch.action, CircleShape))
                },
            )
        }

        Label("Speaking speed")
        val rates = listOf(0.8f, 0.95f, 1.15f)
        Segments(
            options = listOf(0.8f to "Slower", 0.95f to "Normal", 1.15f to "Faster"),
            selected = nearest(rates, session.speechRate) { a, b -> abs(a - b).toDouble() },
            onSelect = { session.chooseSpeechRate(it) },
        )

        Label("Pulse PIN speed")
        val gaps = listOf(1_800L, 1_300L, 900L)
        Segments(
            options = listOf(1_800L to "Slower", 1_300L to "Normal", 900L to "Faster"),
            selected = nearest(gaps, session.pulseGapMs) { a, b -> abs(a - b).toDouble() },
            onSelect = { session.choosePulseGap(it) },
        )

        Label("Zero sound on the loudspeaker")
        Segments(
            options = listOf(false to "Off", true to "On"),
            selected = session.zeroSound,
            onSelect = { session.chooseZeroSound(it) },
        )
        Hint("Zero always sounds in earphones and while practising. On the loudspeaker, someone nearby could hear zero and count to your digit.")

        if (Mode.Switch in session.modes) {
            Label("Scanning speed")
            val scans = listOf(1_500L, 2_500L, 4_000L)
            Segments(
                options = listOf(1_500L to "Faster", 2_500L to "Normal", 4_000L to "Slower"),
                selected = nearest(scans, session.scanMs) { a, b -> abs(a - b).toDouble() },
                onSelect = { session.chooseScanMs(it) },
            )
        }

        Label("Ways in")
        WayInGrid(session.modes, OnboardingCopy.of(session.lang)) { mode, on ->
            val next = if (on) session.modes + mode else session.modes - mode
            if (next.isNotEmpty()) session.chooseModes(next)
        }
        SwitchRow("Captions", session.captions, { session.chooseCaptions(it) }, sub = "Write every spoken line on the screen")

        ChoiceRow(
            title = "Language",
            onClick = onLanguage,
            radio = false,
            sub = session.lang.nativeName,
            leading = { Icon(Glyphs.Language, contentDescription = null, tint = Om.color.action) },
        )

        Kicker("For the demo")
        SwitchRow(
            title = "Pretend I am on a phone call",
            checked = session.demoCallActive,
            onChange = { session.demoCallActive = it },
            sub = "Shows the scam pause when sending ₦150,000 to Musa",
        )
        SwitchRow(
            title = "Use the live Gateway",
            checked = session.useGateway,
            onChange = { if (BuildConfig.GATEWAY_URL.isNotBlank()) session.chooseGateway(it) },
            sub = when {
                BuildConfig.GATEWAY_URL.isBlank() -> "No Gateway address in this build, so the bank runs inside the app"
                !session.useGateway -> "Off: the bank runs inside the app, with no network"
                session.gatewayLost -> "Lost the Gateway, so this session runs offline. Start again to retry."
                else -> "On: " + BuildConfig.GATEWAY_URL
            },
        )
        ActionButton("Start again from welcome", onClick = onRestart, style = Emphasis.Secondary)
        Hint(
            "Òmìnira, a Zecathon 6.0 prototype. With the live Gateway it speaks and listens through YarnGPT; " +
                "otherwise it uses the phone's own speech engines.",
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = Om.type.label, color = Om.color.text, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

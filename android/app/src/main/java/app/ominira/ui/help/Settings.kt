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

private fun <T : Comparable<T>> nearest(options: List<T>, value: T, distance: (T, T) -> Double): T =
    options.minByOrNull { distance(it, value) } ?: options.first()

@Composable
fun SettingsScreen(session: Session, onBack: () -> Unit, onLanguage: () -> Unit, onRestart: () -> Unit) {
    val w = session.words
    Page(
        top = {
            IconAction(Glyphs.Back, w.back, onBack)
            Heading(w.lookAndSound, Modifier.weight(1f))
        },
        bottom = {
            ActionButton(w.hearSample, onClick = {
                session.say(*session.spoken.sampleLines.toTypedArray())
            }, icon = Glyphs.Speaker)
        },
    ) {
        Label(w.theme)
        for (look in Look.entries) {
            val swatch = paletteFor(look)
            ChoiceRow(
                title = when (look) {
                    Look.Paper -> w.paper
                    Look.Night -> w.night
                    Look.Contrast -> w.highContrast
                },
                onClick = { session.chooseLook(look) },
                selected = session.look == look,
                minHeight = 64.dp,
                leading = {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(swatch.surface).border(3.dp, swatch.action, CircleShape))
                },
            )
        }

        Label(w.speakingSpeed)
        val rates = listOf(0.8f, 0.95f, 1.15f)
        Segments(
            options = listOf(0.8f to w.slower, 0.95f to w.normal, 1.15f to w.faster),
            selected = nearest(rates, session.speechRate) { a, b -> abs(a - b).toDouble() },
            onSelect = { session.chooseSpeechRate(it) },
        )

        Label(w.pulseSpeed)
        val gaps = listOf(1_800L, 1_300L, 900L)
        Segments(
            options = listOf(1_800L to w.slower, 1_300L to w.normal, 900L to w.faster),
            selected = nearest(gaps, session.pulseGapMs) { a, b -> abs(a - b).toDouble() },
            onSelect = { session.choosePulseGap(it) },
        )

        Label(w.zeroSoundLabel)
        Segments(
            options = listOf(false to w.off, true to w.on),
            selected = session.zeroSound,
            onSelect = { session.chooseZeroSound(it) },
        )
        Hint(w.zeroHint)

        if (Mode.Switch in session.modes) {
            Label(w.scanningSpeed)
            val scans = listOf(1_500L, 2_500L, 4_000L)
            Segments(
                options = listOf(1_500L to w.faster, 2_500L to w.normal, 4_000L to w.slower),
                selected = nearest(scans, session.scanMs) { a, b -> abs(a - b).toDouble() },
                onSelect = { session.chooseScanMs(it) },
            )
        }

        Label(w.waysIn)
        WayInGrid(session.modes, OnboardingCopy.of(session.lang)) { mode, on ->
            val next = if (on) session.modes + mode else session.modes - mode
            if (next.isNotEmpty()) session.chooseModes(next)
        }
        SwitchRow(w.captions, session.captions, { session.chooseCaptions(it) }, sub = w.captionsSub)

        ChoiceRow(
            title = w.language,
            onClick = onLanguage,
            radio = false,
            sub = session.lang.nativeName,
            leading = { Icon(Glyphs.Language, contentDescription = null, tint = Om.color.action) },
        )

        Kicker(w.forTheDemo)
        SwitchRow(
            title = w.pretendCall,
            checked = session.demoCallActive,
            onChange = { session.demoCallActive = it },
            sub = w.pretendCallSub,
        )
        SwitchRow(
            title = w.useGateway,
            checked = session.useGateway,
            onChange = { if (BuildConfig.GATEWAY_URL.isNotBlank()) session.chooseGateway(it) },
            sub = when {
                BuildConfig.GATEWAY_URL.isBlank() -> w.gatewayNone
                !session.useGateway -> w.gatewayOff
                session.gatewayLost -> w.gatewayLost
                else -> w.gatewayOn(BuildConfig.GATEWAY_URL)
            },
        )
        ActionButton(w.startAgain, onClick = onRestart, style = Emphasis.Secondary)
        Hint(w.about)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = Om.type.label, color = Om.color.text, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

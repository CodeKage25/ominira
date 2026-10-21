package app.ominira.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Answers
import app.ominira.ui.kit.ActionButton
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.Kicker
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.Tone
import app.ominira.ui.kit.rememberReduceMotion
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.Tones
import app.ominira.voice.rememberPrompt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class SonarStep(val line: String, val tone: Float, val offCentre: Float, val hz: Double)

private val sonarSteps = listOf(
    SonarStep("Hold the phone at arm's length, then press start.", 0f, 0.36f, 0.0),
    SonarStep("Move the phone up a little.", 0.3f, 0.26f, 450.0),
    SonarStep("A little to the left.", 0.58f, 0.14f, 650.0),
    SonarStep("Almost there. Keep still.", 0.84f, 0.05f, 790.0),
    SonarStep("Got it. Now say the four numbers.", 1f, 0f, 880.0),
    SonarStep("Thank you. That is you, and you are here.", 1f, 0f, 0.0),
)

@Composable
fun SonarScreen(session: Session, onBack: () -> Unit, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    val leave = remember { Once() }
    val still = rememberReduceMotion()
    var phase by remember { mutableIntStateOf(0) }
    var digits by remember { mutableStateOf<List<Int>>(emptyList()) }
    var started by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var misses by remember { mutableIntStateOf(0) }
    val now = sonarSteps[phase]
    val offCentre by animateFloatAsState(now.offCentre, if (still) snap<Float>() else tween<Float>(600), label = "face")
    val toneLevel by animateFloatAsState(now.tone, if (still) snap<Float>() else tween<Float>(600), label = "tone")

    fun next() = leave.run(onNext)

    fun start() {
        if (started) return
        started = true
        problem = null
        scope.launch {
            // New session each time, so the liveness digits are always fresh.
            val fresh = runCatching { session.api.startOnboarding(session.lang) }.getOrNull()
            if (fresh == null) {
                val line = "I could not start the selfie. Check the connection and try again."
                problem = line
                session.say(line)
                started = false
                return@launch
            }
            session.onboardingId = fresh.id
            digits = fresh.livenessDigits
            val id = fresh.id
            for (n in 1..4) {
                delay(1_300)
                phase = n
                session.say(sonarSteps[n].line)
                Tones.play(sonarSteps[n].hz, if (n == 4) 400 else 160)
            }
            session.haptics.lock()
            session.say("Got it. Now say these numbers.", digits.joinToString(", "))
            val live = runCatching { session.api.sendSelfie(id, digits) }.getOrDefault(true)
            delay(4_000)
            if (!live) {
                val line = "I could not confirm that was you. Let's try once more."
                problem = line
                session.say(line)
                phase = 0
                started = false
                return@launch
            }
            phase = 5
            session.haptics.done()
            session.say(sonarSteps[5].line)
            if (session.talksFirst) {
                session.speaker.awaitQuiet()
                next()
            }
        }
    }

    val prompt = rememberPrompt(session) { heard ->
        when {
            heard == null -> session.say("Tap anywhere on the screen to start.")
            Answers.isStart(heard) -> start()
            misses < 2 -> {
                misses += 1
                ask("Say start when you are ready, or tap anywhere.")
            }
            else -> session.say("Tap anywhere on the screen when you are ready.")
        }
    }

    LaunchedEffect(Unit) {
        if (session.talksFirst) {
            prompt.ask(
                "Let's take your selfie by sound.",
                "Hold the phone at arm's length, with the screen facing you.",
                "Say start when you are ready, or tap anywhere.",
            )
        }
    }

    Page(
        modifier = Modifier.tapAnywhere(phase == 0 && !started, "start the sonar") {
            prompt.cancel()
            start()
        },
        top = { StepBar(2, onBack) },
        bottom = {
            if (phase == 5) {
                ActionButton("Next: your ID card", onClick = {
                    session.speaker.stop()
                    next()
                }, icon = Glyphs.Send)
            } else {
                ActionButton(
                    if (phase == 0) "Start the sonar" else "Finding your face",
                    onClick = {
                        prompt.cancel()
                        start()
                    },
                    icon = Glyphs.Sonar,
                    busy = phase in 1..4,
                )
            }
        },
    ) {
        Kicker("Sonar selfie")
        Heading("Let's take your selfie by sound.")
        problem?.let { Notice(Tone.Danger, it) }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(Corners.xl)
                .background(Color(0xFF0A1712)),
        ) {
            val locked = phase >= 4
            val sun = Om.color.sun
            val mint = Om.color.mint
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics { contentDescription = if (locked) "Face in the frame" else "Finding your face" },
            ) {
                val cx = size.width / 2f
                val cy = size.height * 0.42f
                val ow = size.width * 0.58f
                val oh = size.height * 0.5f
                drawOval(
                    brush = Brush.verticalGradient(listOf(Color(0xFF5B4636), Color(0xFF3D2E23))),
                    topLeft = Offset(cx - ow * 0.4f + offCentre * size.width, cy - oh * 0.42f),
                    size = Size(ow * 0.8f, oh * 0.84f),
                )
                drawOval(
                    color = if (locked) mint else sun,
                    topLeft = Offset(cx - ow / 2f, cy - oh / 2f),
                    size = Size(ow, oh),
                    style = Stroke(width = 4.dp.toPx(), pathEffect = if (locked) null else PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
                )
            }
            if (phase == 4 && digits.isNotEmpty()) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "Say these numbers: " + digits.joinToString(", ") },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (d in digits) {
                        Box(
                            Modifier.size(width = 48.dp, height = 56.dp).clip(Corners.sm).background(Om.color.sun),
                            contentAlignment = Alignment.Center,
                        ) { Text(d.toString(), style = Om.type.titleSmall, color = Om.color.onSun) }
                    }
                }
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(Corners.lg)
                    .background(Color(0xE606170F))
                    .padding(16.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(now.line, style = Om.type.bodyStrong, color = Om.color.bandText)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(Corners.pill)
                        .background(Color(0x33F7F2E7))
                        .clearAndSetSemantics { },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(toneLevel.coerceIn(0.001f, 1f))
                            .height(10.dp)
                            .clip(Corners.pill)
                            .background(Brush.horizontalGradient(listOf(Om.color.sun, Om.color.mint))),
                    )
                }
            }
        }
    }
}

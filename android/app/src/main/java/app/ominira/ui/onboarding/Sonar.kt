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
import app.ominira.ui.camera.CameraFeed
import app.ominira.ui.camera.Lens
import app.ominira.ui.camera.rememberAiming
import app.ominira.ui.camera.rememberCameraAccess
import app.ominira.ui.kit.rememberReduceMotion
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.Tones
import app.ominira.voice.rememberPrompt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Each step's line is Words.sonarSteps at the same index.
private class SonarStep(val tone: Float, val offCentre: Float, val hz: Double)

private val sonarSteps = listOf(
    SonarStep(0f, 0.36f, 0.0),
    SonarStep(0.3f, 0.26f, 450.0),
    SonarStep(0.58f, 0.14f, 650.0),
    SonarStep(0.84f, 0.05f, 790.0),
    SonarStep(1f, 0f, 880.0),
    SonarStep(1f, 0f, 0.0),
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
    val w = session.words
    val s = session.spoken
    val camera = rememberCameraAccess()
    val aiming = rememberAiming(session, Lens.Face)
    // The real camera when it may be used; the simulated sonar otherwise.
    var useCamera by remember { mutableStateOf(false) }
    val now = sonarSteps[phase]
    val offCentre by animateFloatAsState(now.offCentre, if (still) snap<Float>() else tween<Float>(600), label = "face")
    val toneLevel by animateFloatAsState(now.tone, if (still) snap<Float>() else tween<Float>(600), label = "tone")

    fun next() = leave.run(onNext)

    fun selfie() {
        scope.launch {
            // New session each time, so the liveness digits are always fresh.
            val fresh = runCatching { session.api.startOnboarding(session.lang) }.getOrNull()
            if (fresh == null) {
                problem = w.selfieFailed
                session.say(s.selfieFailed)
                started = false
                return@launch
            }
            session.onboardingId = fresh.id
            digits = fresh.livenessDigits
            val id = fresh.id
            if (useCamera) {
                phase = 1
                // Timed out: carry on, so a face the detector misses never strands the customer.
                aiming.run()
                phase = 4
                Tones.play(sonarSteps[4].hz, 400)
            } else {
                for (n in 1..4) {
                    delay(1_300)
                    phase = n
                    session.say(s.sonarSteps[n])
                    Tones.play(sonarSteps[n].hz, if (n == 4) 400 else 160)
                }
            }
            session.haptics.lock()
            session.say(s.sayTheseNumbers, digits.joinToString(", "))
            val live = runCatching { session.api.sendSelfie(id, digits) }.getOrDefault(true)
            delay(4_000)
            if (!live) {
                problem = w.notYou
                session.say(s.notYou)
                phase = 0
                started = false
                return@launch
            }
            phase = 5
            session.haptics.done()
            session.say(s.sonarSteps[5])
            if (session.talksFirst) {
                session.speaker.awaitQuiet()
                next()
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        problem = null
        camera.ask(session) { ok ->
            useCamera = ok
            selfie()
        }
    }

    val prompt = rememberPrompt(session) { heard ->
        when {
            heard == null -> session.say(s.tapToStart)
            Answers.isStart(heard) -> start()
            misses < 2 -> {
                misses += 1
                ask(s.sayStartOrTap)
            }
            else -> session.say(s.tapWhenReady)
        }
    }

    LaunchedEffect(Unit) {
        if (session.talksFirst) {
            prompt.ask(
                s.selfieHeading,
                s.holdAtArm,
                if (session.answersByTouch) s.tapToStart else s.sayStartOrTap,
            )
        }
    }

    Page(
        modifier = Modifier.tapAnywhere(phase == 0 && !started, w.startSonar) {
            prompt.cancel()
            start()
        },
        top = { StepBar(2, onBack) },
        bottom = {
            if (phase == 5) {
                ActionButton(w.nextIdCard, onClick = {
                    session.speaker.stop()
                    next()
                }, icon = Glyphs.Send)
            } else {
                ActionButton(
                    if (phase == 0) w.startSonar else w.findingFace,
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
        Kicker(w.sonarKicker)
        Heading(w.selfieHeading)
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
            if (useCamera && phase < 5) CameraFeed(Lens.Face, aiming::onSight, Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics { contentDescription = if (locked) w.faceInFrame else w.findingFace },
            ) {
                val cx = size.width / 2f
                val cy = size.height * 0.42f
                val ow = size.width * 0.58f
                val oh = size.height * 0.5f
                // The painted face is only for the simulation; the camera shows the real one.
                if (!useCamera) {
                    drawOval(
                        brush = Brush.verticalGradient(listOf(Color(0xFF5B4636), Color(0xFF3D2E23))),
                        topLeft = Offset(cx - ow * 0.4f + offCentre * size.width, cy - oh * 0.42f),
                        size = Size(ow * 0.8f, oh * 0.84f),
                    )
                }
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
                        .semantics(mergeDescendants = true) { contentDescription = w.sayNumbers(digits.joinToString(", ")) },
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
                val line = aiming.aim?.takeIf { aiming.active }?.let { w.aim(it, card = false) } ?: w.sonarSteps[phase]
                Text(line, style = Om.type.bodyStrong, color = Om.color.bandText)
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
                            .fillMaxWidth((if (aiming.active) aiming.closeness else toneLevel).coerceIn(0.001f, 1f))
                            .height(10.dp)
                            .clip(Corners.pill)
                            .background(Brush.horizontalGradient(listOf(Om.color.sun, Om.color.mint))),
                    )
                }
            }
        }
    }
}

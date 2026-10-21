package app.ominira.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Answers
import app.ominira.core.Days
import app.ominira.core.DraftKind
import app.ominira.core.IdDetails
import app.ominira.core.Naira
import app.ominira.core.Readback
import app.ominira.ui.kit.ActionButton
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.Hint
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.Kicker
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.ReadbackPanel
import app.ominira.ui.kit.Tone
import app.ominira.ui.kit.rememberReduceMotion
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.rememberPrompt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val cardLines = listOf("Ready when you are.", "Hold your card up a little.", "Tilt left a little.", "Got it.")
private val cardTilt = listOf(-14f, -14f, -5f, 0f)

private fun idLines(id: IdDetails): Array<String> = arrayOf(
    "Is this you?",
    id.fullName + ".",
    "N I N ending " + Naira.digitWords(id.ninLast4) + ".",
    "Born " + Days.bornOn(id.bornOn) + ".",
)

@Composable
fun IdByEarScreen(session: Session, onBack: () -> Unit, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    val leave = remember { Once() }
    val still = rememberReduceMotion()
    var phase by remember { mutableIntStateOf(0) }
    var details by remember { mutableStateOf<IdDetails?>(null) }
    var started by remember { mutableStateOf(false) }
    var misses by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }
    var startAsked by remember { mutableStateOf(false) }
    val tilt by animateFloatAsState(cardTilt[phase], if (still) snap<Float>() else tween<Float>(600), label = "tilt")
    val confirmLine = "Say yes if that is you, or say again to hear it again."
    val startLines = arrayOf(
        "Now your ID card.",
        "Hold your NIN slip or ID card in front of the back camera.",
        "Say start when you are ready, or tap anywhere.",
    )

    fun next() = leave.run(onNext)

    // Declared before begin(), which asks through it.
    val prompt = rememberPrompt(session) { heard ->
        val id = details
        when {
            heard == null -> session.say(if (id == null) "Tap anywhere on the screen to start." else "You can also tap Yes, that is me.")
            id == null && Answers.isStart(heard) -> startAsked = true
            id == null && misses < 2 -> {
                misses += 1
                ask("Say start when you are ready, or tap anywhere.")
            }
            id == null -> session.say("Tap anywhere when you are ready.")
            Answers.isNo(heard) -> {
                details = null
                phase = 0
                started = false
                misses = 0
                ask("Let's try the card again.", *startLines.drop(1).toTypedArray())
            }
            Answers.isAgain(heard) -> ask(*idLines(id), confirmLine)
            Answers.isYes(heard) -> next()
            misses < 2 -> {
                misses += 1
                ask(confirmLine)
            }
            else -> session.say("You can also tap Yes, that is me.")
        }
    }

    fun begin() {
        if (started) return
        started = true
        problem = null
        prompt.cancel()
        scope.launch {
            for (n in 1..3) {
                delay(1_600)
                phase = n
                session.say(cardLines[n])
            }
            session.haptics.lock()
            val read = runCatching { session.api.readIdDocument(session.onboardingSession()) }.getOrNull()
            if (read == null) {
                val line = "I could not read the card. Check the connection and try again."
                problem = line
                session.say(line)
                phase = 0
                started = false
                return@launch
            }
            details = read
            misses = 0
            if (session.talksFirst) prompt.ask(*idLines(read), confirmLine) else session.say(*idLines(read))
        }
    }

    LaunchedEffect(Unit) {
        if (session.talksFirst) prompt.ask(*startLines)
    }
    LaunchedEffect(startAsked) {
        if (startAsked) {
            startAsked = false
            begin()
        }
    }

    Page(
        modifier = Modifier.tapAnywhere(details == null && !started, "start the camera") { begin() },
        top = { StepBar(3, onBack) },
        bottom = {
            val id = details
            if (id != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionButton(
                        "Yes, that is me",
                        onClick = {
                            prompt.cancel()
                            session.speaker.stop()
                            next()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(Glyphs.Repeat, "Read it to me again", { session.say(*idLines(id)) })
                }
            } else {
                ActionButton(
                    if (phase == 0) "Start the camera" else "Finding your card",
                    onClick = { begin() },
                    icon = Glyphs.Camera,
                    busy = phase in 1..2,
                )
            }
        },
    ) {
        Kicker("ID by ear")
        Heading("Now your ID card.")
        problem?.let { Notice(Tone.Danger, it) }
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(Corners.xl)
                .background(Color(0xFF0A1712)),
            contentAlignment = Alignment.Center,
        ) {
            val locked = phase == 3
            val sun = Om.color.sun
            val mint = Om.color.mint
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics { contentDescription = if (locked) "Card captured" else "Finding your card" },
            ) {
                val fw = size.width * 0.8f
                val fh = size.height * 0.62f
                val fx = (size.width - fw) / 2f
                val fy = size.height * 0.1f
                rotate(tilt, pivot = Offset(size.width / 2f, fy + fh / 2f)) {
                    drawRoundRect(
                        brush = Brush.linearGradient(listOf(Color(0xFF2E7A58), Color(0xFF1D5A40))),
                        topLeft = Offset(fx + fw * 0.05f, fy + fh * 0.08f),
                        size = Size(fw * 0.9f, fh * 0.84f),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                    )
                    drawRoundRect(
                        color = Color(0x8CF7F2E7),
                        topLeft = Offset(fx + fw * 0.12f, fy + fh * 0.25f),
                        size = Size(fw * 0.22f, fh * 0.5f),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )
                }
                drawRoundRect(
                    color = if (locked) mint else sun,
                    topLeft = Offset(fx, fy),
                    size = Size(fw, fh),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(4.dp.toPx(), pathEffect = if (locked) null else PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
                )
            }
            Text(
                cardLines[phase],
                style = Om.type.bodyStrong,
                color = Om.color.bandText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val id = details
        if (id == null) {
            Hint("Hold your NIN slip or ID card in front of the back camera. I will guide you, then read it back.")
        } else {
            ReadbackPanel(
                readback = Readback(
                    kind = DraftKind.Transfer,
                    name = id.fullName,
                    bank = "NIN",
                    last4 = id.ninLast4,
                    amountKobo = null,
                    amountText = null,
                    lang = session.lang.code,
                    spoken = listOf("${id.fullName}.", "NIN ending ${Naira.digitWords(id.ninLast4)}.", "Born ${Days.bornOn(id.bornOn)}."),
                ),
                heading = "Is this you?",
            )
        }
    }
}

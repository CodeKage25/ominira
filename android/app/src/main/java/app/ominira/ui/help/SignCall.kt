package app.ominira.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Words
import app.ominira.ui.kit.ActionButton
import app.ominira.ui.kit.Badge
import app.ominira.ui.kit.CaptionBox
import app.ominira.ui.kit.Emphasis
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.Tone
import app.ominira.ui.kit.VoiceDock
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om
import app.ominira.voice.rememberVoice
import kotlinx.coroutines.delay

// Funmi signs the even lines, the customer the odd ones.
private fun signScript(w: Words): List<Pair<String, String>> =
    w.signScript.mapIndexed { i, line -> (if (i % 2 == 0) w.funmiSigning else w.youSigned) to line }

@Composable
fun SignCallScreen(session: Session, onHome: () -> Unit) {
    var seconds by remember { mutableIntStateOf(372) }
    var shown by remember { mutableIntStateOf(1) }
    var captions by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    val w = session.words
    val script = signScript(w)
    val voice = rememberVoice()

    LaunchedEffect(Unit) { voice.arrive(session.spoken.placeSign, session.spoken.mainSign) }

    LaunchedEffect(ended) {
        while (!ended) {
            delay(1_000)
            seconds += 1
            if (seconds % 4 == 0) shown += 1
        }
    }

    Page(
        top = {
            IconAction(Glyphs.Back, w.backToHome, onHome)
            Heading(w.signHelpTitle, Modifier.weight(1f))
            if (!ended) Badge(Tone.Ok, w.live("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"), showIcon = false)
        },
        bottom = {
            if (ended) {
                ActionButton(w.backToHome, onClick = onHome, icon = Glyphs.Home)
                ActionButton(w.callAgain, onClick = {
                    ended = false
                    seconds = 0
                    shown = 1
                }, style = Emphasis.Quiet)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(Glyphs.Camera, w.camera, { camera = !camera }, on = camera)
                    IconAction(Glyphs.Captions, w.captions, { captions = !captions }, on = captions)
                    ActionButton(w.endCall, onClick = {
                        ended = true
                        session.haptics.lock()
                    }, modifier = Modifier.weight(1f), style = Emphasis.Danger, icon = Glyphs.Phone)
                }
            }
        },
        dock = { VoiceDock(session) },
    ) {
        if (ended) {
            Heading(w.callEnded)
            Notice(Tone.Ok, w.newCardOnWay, body = w.arrivesThursday)
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .clip(Corners.lg)
                    .background(Brush.radialGradient(listOf(Color(0xFF2A5A46), Color(0xFF0E2A20))))
                    .clearAndSetSemantics { contentDescription = w.videoOfFunmi },
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.64f)
                        .fillMaxHeight(0.78f)
                        .clip(RoundedCornerShape(topStartPercent = 46, topEndPercent = 46))
                        .background(Brush.verticalGradient(listOf(Color(0xFF2E5D82), Color(0xFF1F4566)))),
                )
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 60.dp)
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6B4D3A)),
                )
                Row(
                    Modifier
                        .padding(12.dp)
                        .clip(Corners.pill)
                        .background(Color(0xD906170F))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Om.color.mint))
                    Text(w.funmiAgent, style = Om.type.smallStrong, color = Om.color.bandText)
                }
                if (camera) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .fillMaxWidth(0.28f)
                            .aspectRatio(3f / 4f)
                            .clip(Corners.md)
                            .background(Brush.verticalGradient(listOf(Color(0xFF4B3A2C), Color(0xFF2C2119))))
                            .border(2.dp, Color(0x99F7F2E7), Corners.md),
                    )
                }
            }
            if (captions) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in maxOf(0, shown - 3) until shown) {
                        val (who, text) = script[k % script.size]
                        CaptionBox(who, text)
                    }
                }
            }
        }
    }
}

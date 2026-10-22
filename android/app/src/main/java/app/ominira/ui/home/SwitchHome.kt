package app.ominira.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.ominira.Session
import app.ominira.core.Words
import app.ominira.ui.kit.ActionButton
import app.ominira.ui.kit.Brand
import app.ominira.ui.kit.Emphasis
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.Hint
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

// label picks from Words, so the screen and the voice can each use their own language.
private class ScanItem(val label: Words.() -> String, val icon: ImageVector, val open: () -> Unit)

@Composable
fun SwitchHomeScreen(session: Session, actions: HomeActions) {
    val voice = rememberVoice()
    val items = remember(actions, voice) {
        listOf(
            // Speak opens the one microphone, and closes it when it is open.
            ScanItem({ if (voice.listening) stop else speak }, Glyphs.Mic) { if (voice.listening) voice.stop() else voice.listenNow() },
            ScanItem({ sendMoney }, Glyphs.Send, actions.send),
            ScanItem({ myBalance }, Glyphs.Wallet, actions.balance),
            ScanItem({ myCard }, Glyphs.Card, actions.card),
            ScanItem({ signHelp }, Glyphs.Sign, actions.sign),
            ScanItem({ settings }, Glyphs.Settings, actions.settings),
        )
    }
    var ring by remember { mutableIntStateOf(0) }
    var picked by remember { mutableIntStateOf(-1) }
    var paused by remember { mutableStateOf(false) }
    val scanMs = session.scanMs
    val focus = remember { FocusRequester() }
    val w = session.words

    LaunchedEffect(paused, picked, scanMs) {
        if (paused) return@LaunchedEffect
        if (picked >= 0) {
            delay(maxOf(4_000L, scanMs * 3))
            picked = -1
        }
        while (true) {
            delay(scanMs)
            ring = (ring + 1) % items.size
            session.haptics.tick()
        }
    }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        voice.arrive(session.spoken.homeTitle, session.spoken.whatToDo)
    }

    fun press() {
        session.haptics.lock()
        if (picked == ring) {
            items[ring].open()
            return
        }
        picked = ring
        session.say(items[ring].label(session.spoken))
    }

    Page(
        modifier = Modifier
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                val isSwitch = e.key == Key.Spacebar || e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter
                if (isSwitch && e.type == KeyEventType.KeyUp) {
                    press()
                    true
                } else {
                    isSwitch
                }
            },
        top = {
            Brand(Modifier.weight(1f)) { IconAction(Glyphs.Settings, w.settings, actions.settings) }
        },
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(
                    if (paused) Glyphs.Send else Glyphs.Pause,
                    if (paused) w.resumeScanning else w.pauseScanning,
                    { paused = !paused },
                )
                ActionButton(
                    w.pressSwitch,
                    onClick = { press() },
                    modifier = Modifier.weight(1f).heightIn(min = 96.dp),
                    style = Emphasis.Sun,
                    icon = Glyphs.Switch,
                )
            }
        },
        dock = { VoiceDock(session, showButton = false) },
    ) {
        Heading(w.whatToDo)
        Hint(if (paused) w.scanPaused else w.ringMoves(scanMs / 1000.0))
        items.forEachIndexed { i, item ->
            val ringed = i == ring
            val chosen = i == picked
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .then(if (ringed) Modifier.border(5.dp, Om.color.focus, Corners.lg) else Modifier)
                    .padding(6.dp)
                    .clip(Corners.md)
                    .background(if (chosen) Om.color.actionSoft else Om.color.raised)
                    .border(2.dp, if (chosen) Om.color.action else Om.color.lineStrong, Corners.md)
                    .clickable(role = Role.Button, onClickLabel = w.open) { item.open() }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .semantics {
                        stateDescription = when {
                            chosen -> w.chosenPressAgain
                            ringed -> w.highlighted
                            else -> ""
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(item.icon, contentDescription = null, tint = Om.color.text, modifier = Modifier.size(28.dp))
                Text(item.label(w), style = Om.type.bodyStrong.copy(fontSize = Om.type.lead.fontSize), color = Om.color.text)
            }
        }
        if (picked >= 0) Notice(Tone.Ok, w.youChose(items[picked].label(w)), body = w.pressOrWait)
        Spacer(Modifier.size(8.dp))
    }
}

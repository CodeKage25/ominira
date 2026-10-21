package app.ominira.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om

enum class Tone { Ok, Caution, Danger, Info }

private class TonePaint(val ink: Color, val soft: Color, val icon: ImageVector)

@Composable
private fun paint(tone: Tone): TonePaint = when (tone) {
    Tone.Ok -> TonePaint(Om.color.ok, Om.color.okSoft, Glyphs.Check)
    Tone.Caution -> TonePaint(Om.color.caution, Om.color.cautionSoft, Glyphs.Pause)
    Tone.Danger -> TonePaint(Om.color.danger, Om.color.dangerSoft, Glyphs.Alert)
    Tone.Info -> TonePaint(Om.color.info, Om.color.infoSoft, Glyphs.Info)
}

/**
 * A status message: an icon, a bold line saying what happened, then what to do. Never colour
 * alone. Announced by TalkBack when it appears; a danger notice interrupts.
 */
@Composable
fun Notice(tone: Tone, title: String, modifier: Modifier = Modifier, body: String? = null, icon: ImageVector? = null) {
    val p = paint(tone)
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corners.md)
            .background(p.soft)
            .border(2.dp, p.ink, Corners.md)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                liveRegion = if (tone == Tone.Danger) LiveRegionMode.Assertive else LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon ?: p.icon, contentDescription = null, tint = p.ink, modifier = Modifier.padding(top = 2.dp).size(24.dp))
        Column {
            Text(title, style = Om.type.bodyStrong, color = Om.color.text)
            if (body != null) Text(body, style = Om.type.body, color = Om.color.text)
        }
    }
}

/** A small label: "On a call", "Blocked", "Live 6:12". */
@Composable
fun Badge(tone: Tone, text: String, modifier: Modifier = Modifier, showIcon: Boolean = true, icon: ImageVector? = null) {
    val p = paint(tone)
    Row(
        modifier
            .heightIn(min = 32.dp)
            .clip(Corners.pill)
            .background(p.soft)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showIcon) Icon(icon ?: p.icon, contentDescription = null, tint = p.ink, modifier = Modifier.size(18.dp))
        Text(text, style = Om.type.smallStrong, color = p.ink)
    }
}

/** A short "done" line on the forest band. */
@Composable
fun Confirmation(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corners.pill)
            .background(Om.color.forest)
            .border(2.dp, Om.color.forestEdge, Corners.pill)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Glyphs.Check, contentDescription = null, tint = Om.color.mint, modifier = Modifier.size(24.dp))
        Text(text, style = Om.type.bodyStrong, color = Om.color.bandText)
    }
}

enum class Progress { Done, Now, Next }

class Milestone(val title: String, val sub: String, val stage: Progress)

/** A short timeline: done, happening now, next. Each step reads as one line. */
@Composable
fun Timeline(steps: List<Milestone>, modifier: Modifier = Modifier) {
    val c = Om.color
    Column(modifier) {
        steps.forEachIndexed { i, s ->
            Row(Modifier.semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val fill = if (s.stage == Progress.Done) c.ok else c.raised
                    val edge = when (s.stage) {
                        Progress.Done -> c.ok
                        Progress.Now -> c.caution
                        Progress.Next -> c.lineStrong
                    }
                    val ink = when (s.stage) {
                        Progress.Done -> c.raised
                        Progress.Now -> c.caution
                        Progress.Next -> c.text
                    }
                    val glyph = when (s.stage) {
                        Progress.Done -> Glyphs.Check
                        Progress.Now -> Glyphs.Pause
                        Progress.Next -> Glyphs.Send
                    }
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .border(if (s.stage == Progress.Now) 3.dp else 2.dp, edge, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(glyph, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                    }
                    if (i < steps.lastIndex) Box(Modifier.width(2.dp).heightIn(min = 40.dp).background(c.line))
                }
                Column(Modifier.padding(bottom = 16.dp)) {
                    Text(s.title, style = Om.type.bodyStrong, color = c.text)
                    Text(s.sub, style = Om.type.small, color = c.muted)
                }
            }
        }
    }
}

/**
 * A sheet over a scrim that cannot be swiped or tapped away. Used for the scam pause: while it is
 * up, the screen behind should clear its own semantics so TalkBack only finds the sheet.
 */
@Composable
fun BlockingSheet(caution: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val c = Om.color
    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Corners.sheet)
                .background(if (caution) c.cautionSoft else c.raised)
                .border(2.dp, if (caution) c.caution else c.line, Corners.sheet)
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 56.dp, height = 6.dp)
                    .clip(Corners.pill)
                    .background(c.lineStrong),
            )
            content()
        }
    }
}

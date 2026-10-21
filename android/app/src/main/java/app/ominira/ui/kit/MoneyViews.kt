package app.ominira.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ominira.core.Card
import app.ominira.core.Days
import app.ominira.core.Naira
import app.ominira.core.Readback
import app.ominira.core.Txn
import app.ominira.core.Words
import app.ominira.ui.theme.Corners
import app.ominira.ui.theme.Glyphs
import app.ominira.ui.theme.Om

@Composable
fun ReadbackPanel(
    readback: Readback,
    modifier: Modifier = Modifier,
    heading: String = Om.words.checkBeforeSend,
    accent: Color = Om.color.action,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Om.color
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.lg)
            .background(c.raised)
            .border(3.dp, accent, Corners.lg)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.clearAndSetSemantics { contentDescription = heading + ". " + readback.spoken.joinToString(" ") },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Glyphs.Shield, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Text(heading.uppercase(), style = Om.type.kicker, color = accent)
            }
            Text(readback.name, style = Om.type.readback, color = c.text)
            if (readback.last4.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${readback.bank} · ${Om.words.ending} ", style = Om.type.body, color = c.muted)
                    Text(
                        readback.last4.toList().joinToString(" "),
                        style = Om.type.body.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = c.text,
                    )
                }
            }
            readback.amountKobo?.let { kobo ->
                Text(readback.amountText ?: Naira.format(kobo), style = Om.type.amount, color = c.text)
            }
            val heard = readback.spoken.filter { it.isNotBlank() }
            if (heard.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Corners.md)
                        .background(c.sunk)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Glyphs.Speaker, contentDescription = null, tint = c.text, modifier = Modifier.padding(top = 2.dp).size(22.dp))
                    Text("“" + heard.joinToString(" ") + "”", style = Om.type.small, color = c.text)
                }
            }
        }
        actions()
    }
}

@Composable
fun BalancePanel(label: String, balanceKobo: Long, modifier: Modifier = Modifier, note: String? = null, onHear: (() -> Unit)? = null) {
    val c = Om.color
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.lg)
            .background(c.forest)
            .border(2.dp, c.forestEdge, Corners.lg)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.clearAndSetSemantics { contentDescription = "$label, ${Naira.format(balanceKobo)}" + (note?.let { ", $it" } ?: "") }) {
            Text(label, style = Om.type.body, color = c.bandMuted)
            Text(Naira.format(balanceKobo), style = Om.type.amount, color = c.bandText)
            if (note != null) Text(note, style = Om.type.small, color = c.bandMuted)
        }
        if (onHear != null) {
            ActionButton(Om.words.readItToMe, onHear, Modifier.padding(top = 8.dp), style = Emphasis.Sun, icon = Glyphs.Speaker, wide = false)
        }
    }
}

fun txnSentence(t: Txn, w: Words): String = w.txn(t.incoming, Naira.format(t.amountKobo), t.who, Days.spoken(t.at, lang = w.lang))

@Composable
fun TxnRows(items: List<Txn>, modifier: Modifier = Modifier) {
    val c = Om.color
    val w = Om.words
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.lg)
            .background(c.raised)
            .border(2.dp, c.line, Corners.lg),
    ) {
        items.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(thickness = 2.dp, color = c.line)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clearAndSetSemantics { contentDescription = txnSentence(t, w) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (t.incoming) c.okSoft else c.sunk),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (t.incoming) Glyphs.Back else Glyphs.Send,
                        contentDescription = null,
                        tint = if (t.incoming) c.ok else c.text,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(t.who, style = Om.type.bodyStrong, color = c.text)
                    Text(Days.label(t.at, lang = w.lang), style = Om.type.small, color = c.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Naira.format(t.amountKobo), style = Om.type.bodyStrong, color = c.text, textAlign = TextAlign.End)
                    Text(if (t.incoming) "In" else "Out", style = Om.type.smallStrong, color = c.muted)
                }
            }
        }
    }
}

@Composable
fun BankCardView(card: Card, modifier: Modifier = Modifier) {
    val c = Om.color
    val w = Om.words
    Column(
        modifier
            .fillMaxWidth()
            .height(212.dp)
            .alpha(if (card.blocked) 0.75f else 1f)
            .clip(Corners.lg)
            .background(if (card.blocked) Color(0xFF3A3F3C) else c.forest)
            .border(2.dp, c.forestEdge, Corners.lg)
            .padding(horizontal = 24.dp, vertical = 22.dp)
            .clearAndSetSemantics {
                contentDescription = w.cardSummary(card.last4, card.blocked)
            },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Zenith", style = Om.type.brand, color = c.bandText)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (row in listOf(true to false, false to true, true to false)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (raised in listOf(row.first, row.second)) {
                            Box(Modifier.size(11.dp).clip(CircleShape).background(if (raised) c.sun else Color(0x33F7F2E7)))
                        }
                    }
                }
            }
        }
        Box(Modifier.size(width = 46.dp, height = 34.dp).clip(Corners.sm).background(Color(0xFFD9B95E)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("•••• ${card.last4}", style = Om.type.bodyStrong.copy(fontFamily = FontFamily.Monospace), color = c.bandText)
                Text(card.holder, style = Om.type.small, color = c.bandMuted)
            }
            if (card.blocked) Badge(Tone.Danger, w.blocked, icon = Glyphs.Lock)
        }
    }
}

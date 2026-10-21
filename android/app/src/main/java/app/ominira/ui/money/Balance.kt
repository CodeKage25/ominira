package app.ominira.ui.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.ominira.Session
import app.ominira.core.Account
import app.ominira.core.Days
import app.ominira.core.Txn
import app.ominira.core.Words
import app.ominira.ui.kit.ActionButton
import app.ominira.ui.kit.BalancePanel
import app.ominira.ui.kit.ChatLine
import app.ominira.ui.kit.Emphasis
import app.ominira.ui.kit.Heading
import app.ominira.ui.kit.Hint
import app.ominira.ui.kit.IconAction
import app.ominira.ui.kit.Notice
import app.ominira.ui.kit.Page
import app.ominira.ui.kit.Tone
import app.ominira.ui.kit.TxnRows
import app.ominira.ui.theme.Glyphs

private fun spokenTxn(w: Words, t: Txn): String = w.txn(t.incoming, w.money(t.amountKobo), t.who, Days.spoken(t.at, lang = w.lang))

@Composable
fun BalanceScreen(session: Session, onHome: () -> Unit) {
    var account by remember { mutableStateOf<Account?>(null) }
    var balanceSay by remember { mutableStateOf("") }
    var txns by remember { mutableStateOf<List<Txn>>(emptyList()) }
    var cameIn by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val w = session.words

    LaunchedEffect(Unit) {
        val a = session.mainAccount()
        if (a == null) {
            failed = true
            session.say(session.spoken.accountUnreachable, session.spoken.tryInAMinute)
            return@LaunchedEffect
        }
        account = a
        runCatching { session.api.balance(a.id) }.getOrNull()?.let {
            account = a.copy(balanceKobo = it.balanceKobo)
            balanceSay = it.say
        }
        txns = runCatching { session.api.transactions(a.id, 20, null) }.getOrDefault(emptyList())
        cameIn = runCatching { session.api.incomeSummary(a.id) }.getOrDefault("")
        session.sayOnArrival(*listOf(balanceSay, cameIn).filter { it.isNotBlank() }.toTypedArray())
    }

    Page(
        top = {
            IconAction(Glyphs.Back, w.backToHome, onHome)
            Heading(w.yourMoney, Modifier.weight(1f))
        },
        bottom = {
            ActionButton(
                w.readLatest,
                onClick = { session.say(*txns.take(5).map { spokenTxn(session.spoken, it) }.toTypedArray()) },
                style = Emphasis.Secondary,
                icon = Glyphs.Speaker,
                enabled = txns.isNotEmpty(),
            )
        },
    ) {
        if (failed) Notice(Tone.Danger, w.accountUnreachable, body = w.tryInAMinute)
        account?.let { a ->
            BalancePanel(w.currentAccount, a.balanceKobo, note = w.updatedJustNow, onHear = {
                session.say(balanceSay.ifBlank { session.spoken.youHave(a.balanceKobo) })
            })
        }
        if (cameIn.isNotBlank()) ChatLine(false, cameIn)
        if (txns.isNotEmpty()) {
            Hint(w.latestInOut)
            TxnRows(txns.take(5))
        }
    }
}

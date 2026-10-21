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
import app.ominira.core.Naira
import app.ominira.core.Txn
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

private fun spokenTxn(t: Txn): String {
    val day = Days.spoken(t.at)
    return (if (t.incoming) "In: " else "Out: ") + Naira.spoken(t.amountKobo) +
        (if (t.incoming) " from " else " to ") + t.who + (if (day.isBlank()) "." else ", ${day.lowercase()}.")
}

@Composable
fun BalanceScreen(session: Session, onHome: () -> Unit) {
    var account by remember { mutableStateOf<Account?>(null) }
    var balanceSay by remember { mutableStateOf("") }
    var txns by remember { mutableStateOf<List<Txn>>(emptyList()) }
    var cameIn by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val a = session.mainAccount()
        if (a == null) {
            failed = true
            session.say("I could not reach your account just now. Please try again in a minute.")
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
            IconAction(Glyphs.Back, "Back to home", onHome)
            Heading("Your money", Modifier.weight(1f))
        },
        bottom = {
            ActionButton(
                "Read the latest to me",
                onClick = { session.say(*txns.take(5).map { spokenTxn(it) }.toTypedArray()) },
                style = Emphasis.Secondary,
                icon = Glyphs.Speaker,
                enabled = txns.isNotEmpty(),
            )
        },
    ) {
        if (failed) Notice(Tone.Danger, "I could not reach your account.", body = "Please try again in a minute.")
        account?.let { a ->
            BalancePanel("Current account", a.balanceKobo, note = "Updated just now", onHear = {
                session.say(balanceSay.ifBlank { "You have " + Naira.spoken(a.balanceKobo) + "." })
            })
        }
        if (cameIn.isNotBlank()) ChatLine(false, cameIn)
        if (txns.isNotEmpty()) {
            Hint("Latest money in and out")
            TxnRows(txns.take(5))
        }
    }
}

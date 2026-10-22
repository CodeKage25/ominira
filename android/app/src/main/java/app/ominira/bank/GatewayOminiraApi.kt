package app.ominira.bank

import android.util.Log
import app.ominira.core.Account
import app.ominira.core.Balance
import app.ominira.core.BvnCheck
import app.ominira.core.Card
import app.ominira.core.Draft
import app.ominira.core.DraftKind
import app.ominira.core.DraftStatus
import app.ominira.core.Drafted
import app.ominira.core.IdDetails
import app.ominira.core.Lang
import app.ominira.core.OnboardingSession
import app.ominira.core.Outcome
import app.ominira.core.Payee
import app.ominira.core.PreferencePatch
import app.ominira.core.Readback
import app.ominira.core.Turn
import app.ominira.core.Txn
import app.ominira.core.Unlock
import app.ominira.core.Words
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private class GatewayTrouble(message: String) : IOException(message)

class GatewayOminiraApi(
    baseUrl: String,
    token: String,
    private val mockAdminToken: String,
    private val fallback: OminiraApi = FakeOminiraApi(),
    // The new customer's own token, once onboarding gives it: the phone keeps it.
    private val onToken: (String) -> Unit = {},
    // The customer's language, for the lines the phone fills in when the Gateway gives none.
    private val customerLang: () -> Lang = { Lang.En },
) : OminiraApi {

    private val base = baseUrl.trim().trimEnd('/')
    private val origin = base.removeSuffix("/v1")

    @Volatile private var bearer = token

    @Volatile var offline = false
        private set

    @Volatile private var accountId: String? = null
    @Volatile private var conversationId: String? = null
    @Volatile private var syncedLang: Lang? = null

    private val words: Words get() = Words.of(customerLang())
    private val kinds = ConcurrentHashMap<String, DraftKind>()
    // The readback hash the customer last confirmed, per draft.
    private val heardHashes = ConcurrentHashMap<String, String>()

    private class Reply(val status: Int, val body: Any?) {
        val ok: Boolean get() = status in 200..299
        val obj: JSONObject get() = body as? JSONObject ?: JSONObject()
        val list: JSONArray get() = body as? JSONArray ?: JSONArray()
        val code: String get() = obj.text("code")
        fun say(fallbackLine: String): String = obj.text("say").ifBlank { fallbackLine }
    }

    private suspend fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true,
        timeoutMs: Int = 20_000,
        key: String = UUID.randomUUID().toString(),
    ): Reply = send(method, base + path, body, auth, timeoutMs, extraHeaders = emptyMap(), key = key)

    private suspend fun send(
        method: String,
        url: String,
        body: JSONObject?,
        auth: Boolean,
        timeoutMs: Int,
        extraHeaders: Map<String, String>,
        key: String = UUID.randomUUID().toString(),
    ): Reply = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8_000
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", "application/json")
            if (auth) conn.setRequestProperty("Authorization", "Bearer $bearer")
            extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (method != "GET") {
                // Fresh key per attempt; the same key only to retry the same request.
                conn.setRequestProperty("Idempotency-Key", key)
                val bytes = (body ?: JSONObject()).toString().toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            if (conn.getHeaderField("Idempotent-Replayed") == "true") Log.i(TAG, "Gateway replayed an earlier answer for $method $url")
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty().trim()
            val parsed: Any? = when {
                text.startsWith("{") -> JSONObject(text)
                text.startsWith("[") -> JSONArray(text)
                else -> null
            }
            Reply(status, parsed)
        } finally {
            conn.disconnect()
        }
    }

    // Non-money calls only. A Gateway reply (401, 429, 5xx) is thrown to the caller; only a lost connection goes offline.
    private suspend fun <T> orOffline(offlineCall: suspend () -> T, online: suspend () -> T): T {
        if (offline) return offlineCall()
        return try {
            online()
        } catch (e: IOException) {
            if (!Reach.unreachable(e)) throw e
            goOffline(e)
            offlineCall()
        }
    }

    private fun goOffline(e: Exception) {
        Log.w(TAG, "Gateway unreachable; this session carries on offline", e)
        offline = true
    }

    private fun Reply.orTrouble(): Reply = if (ok) this else throw GatewayTrouble("HTTP $status $code")

    // Gateway on: the customer is whoever this token says. A new one starts a new conversation.
    fun switchCustomer(token: String) {
        bearer = token
        conversationId = null
        accountId = null
        syncedLang = null
        offline = false
    }

    // The first name and language the Gateway holds for this customer; null when it cannot say.
    suspend fun profile(): Pair<String?, Lang?>? = try {
        val o = call("GET", "/me/preferences").orTrouble().obj
        o.text("first_name").ifBlank { null } to Lang.fromCode(o.text("lang"))
    } catch (e: IOException) {
        null
    } catch (e: JSONException) {
        null
    }

    override suspend fun startOnboarding(lang: Lang): OnboardingSession = orOffline({ fallback.startOnboarding(lang) }) {
        val o = call("POST", "/onboarding/sessions", JSONObject().put("lang", lang.code), auth = false).orTrouble().obj
        val digits = o.getJSONArray("liveness_digits")
        OnboardingSession(o.getString("id"), List(digits.length()) { digits.getInt(it) })
    }

    override suspend fun sendSelfie(sessionId: String, spokenDigits: List<Int>?): Boolean =
        orOffline({ fallback.sendSelfie(sessionId, spokenDigits) }) {
            val body = JSONObject()
            if (spokenDigits != null) body.put("spoken_digits", JSONArray(spokenDigits))
            call("POST", "/onboarding/$sessionId/selfie", body, auth = false).orTrouble().obj.optBoolean("live", true)
        }

    override suspend fun readIdDocument(sessionId: String, read: IdDetails?): IdDetails = orOffline({ fallback.readIdDocument(sessionId, read) }) {
        // Only what is known: a name said or typed comes with no card details.
        val body = read?.let { r ->
            JSONObject().put("full_name", r.fullName).apply {
                if (r.ninLast4.isNotBlank()) put("nin_last4", r.ninLast4)
                if (r.bornOn.isNotBlank()) put("born_on", r.bornOn)
            }
        }
        val o = call("POST", "/onboarding/$sessionId/id-document", body, auth = false).orTrouble().obj
        IdDetails(o.getString("full_name"), o.text("nin_last4"), o.text("born_on"))
    }

    override suspend fun checkBvn(sessionId: String, bvn: String): BvnCheck = orOffline({ fallback.checkBvn(sessionId, bvn) }) {
        val r = call("POST", "/onboarding/$sessionId/bvn", JSONObject().put("bvn", bvn), auth = false)
        if (r.ok) {
            val match = r.obj.optBoolean("match", false)
            BvnCheck(match, r.say(if (match) "Your BVN matches." else "That BVN does not match your card. Please check it and try again."))
        } else if (r.status in 400..499) {
            BvnCheck(false, r.say("That BVN did not go through. Please check it and try again."))
        } else {
            throw GatewayTrouble("HTTP ${r.status}")
        }
    }

    override suspend fun confirmBvn(sessionId: String): BvnCheck = orOffline({ fallback.confirmBvn(sessionId) }) {
        val r = call("POST", "/onboarding/$sessionId/bvn-check", auth = false)
        if (r.ok) {
            val match = r.obj.optBoolean("match", false)
            BvnCheck(match, r.say(if (match) "Zenith found your BVN with NIBSS." else "Zenith could not find your BVN with NIBSS."))
        } else if (r.status in 400..499) {
            BvnCheck(false, r.say("Zenith could not find your BVN with NIBSS."))
        } else {
            throw GatewayTrouble("HTTP ${r.status}")
        }
    }

    // The PIN that moves money is set with the Gateway or not at all.
    override suspend fun setPin(sessionId: String, pinBlock: String): Boolean {
        val r = try {
            call("POST", "/onboarding/$sessionId/pin", JSONObject().put("pin_block", pinBlock), auth = false)
        } catch (e: IOException) {
            if (Reach.unreachable(e)) goOffline(e)
            return false
        } catch (e: JSONException) {
            return false
        }
        return r.ok
    }

    override suspend fun completeOnboarding(sessionId: String): Boolean = orOffline({ fallback.completeOnboarding(sessionId) }) {
        val r = call("POST", "/onboarding/$sessionId/complete", auth = false)
        when {
            r.ok -> {
                r.obj.text("token").takeIf { it.isNotBlank() }?.let {
                    bearer = it
                    onToken(it)
                }
                true
            }
            r.code == "onboarding_incomplete" -> false
            else -> throw GatewayTrouble("HTTP ${r.status}")
        }
    }

    override suspend fun unlock(pinBlock: String): Unlock = orOffline({ fallback.unlock(pinBlock) }) {
        val r = call("POST", "/auth/unlock", JSONObject().put("pin_block", pinBlock))
        when {
            r.ok -> Unlock.Open(r.say(words.welcomeBack))
            r.code == "pin_incorrect" -> r.obj.optInt("tries_left", 0).let { Unlock.Wrong(it, r.say(pinWrongLine(it))) }
            else -> throw GatewayTrouble("HTTP ${r.status} ${r.code}")
        }
    }

    override suspend fun firstName(): String? =
        runCatching { call("GET", "/me/preferences").orTrouble().obj.optString("first_name").ifBlank { null } }.getOrNull()

    override suspend fun accounts(): List<Account> = orOffline({ fallback.accounts() }) {
        val a = call("GET", "/accounts").orTrouble().list
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Account(o.getString("id"), o.text("name").ifBlank { "Current account" }, o.text("last4"), o.getLong("balance_kobo"), o.text("number"))
        }.also { list -> list.firstOrNull()?.let { accountId = it.id } }
    }

    private suspend fun accountIdOr(given: String): String {
        if (given.isNotBlank()) return given
        accountId?.let { return it }
        val first = call("GET", "/accounts").orTrouble().list.getJSONObject(0).getString("id")
        accountId = first
        return first
    }

    override suspend fun balance(accountId: String): Balance = orOffline({ fallback.balance(accountId) }) {
        val o = call("GET", "/accounts/${accountIdOr(accountId)}/balance").orTrouble().obj
        Balance(o.getLong("balance_kobo"), o.text("say"))
    }

    override suspend fun transactions(accountId: String, limit: Int, direction: String?): List<Txn> =
        orOffline({ fallback.transactions(accountId, limit, direction) }) { liveTransactions(accountId, limit, direction) }

    // Money in to announce: a real Gateway reply or nothing, never the bank inside the app.
    suspend fun incomingNow(limit: Int): List<Txn>? = try {
        liveTransactions("", limit, "in")
    } catch (e: IOException) {
        null
    } catch (e: JSONException) {
        null
    }

    private suspend fun liveTransactions(accountId: String, limit: Int, direction: String?): List<Txn> {
        val query = "?limit=$limit" + (direction?.let { "&direction=$it" } ?: "")
        val a = call("GET", "/accounts/${accountIdOr(accountId)}/transactions$query").orTrouble().list
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Txn(
                id = o.getString("id"),
                who = o.text("who"),
                amountKobo = o.getLong("amount_kobo"),
                incoming = o.optBoolean("incoming", false),
                at = o.text("at"),
                reference = o.text("reference").ifBlank { null },
            )
        }
    }

    override suspend fun incomeSummary(accountId: String): String = orOffline({ fallback.incomeSummary(accountId) }) {
        call("GET", "/accounts/${accountIdOr(accountId)}/income-summary").orTrouble().obj.text("say")
    }

    override suspend fun payees(): List<Payee> = orOffline({ fallback.payees() }) {
        val a = call("GET", "/payees").orTrouble().list
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Payee(
                id = o.getString("id"),
                nickname = o.text("nickname").ifBlank { o.text("name") },
                name = o.text("name"),
                bank = o.text("bank"),
                last4 = o.text("account_last4"),
                isNew = o.optBoolean("is_new", false),
                flagged = o.optBoolean("flagged", false),
            )
        }
    }

    override suspend fun cards(): List<Card> = orOffline({ fallback.cards() }) {
        val a = call("GET", "/cards").orTrouble().list
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Card(o.getString("id"), o.text("last4"), o.text("holder"), o.text("status") == "blocked")
        }
    }

    override suspend fun savePreferences(patch: PreferencePatch) {
        if (offline) return fallback.savePreferences(patch)
        val body = JSONObject()
        patch.lang?.let { body.put("lang", it.code) }
        patch.modes?.let { modes -> body.put("modes", JSONArray(modes.map { it.wire })) }
        patch.theme?.let { body.put("theme", it) }
        patch.speechRate?.let { body.put("speech_rate", it.coerceIn(0.5f, 2.0f).toDouble()) }
        patch.pulseGapMs?.let { body.put("pulse_gap_ms", it.coerceIn(400L, 3_000L)) }
        if (body.length() == 0) return
        try {
            val r = call("PUT", "/me/preferences", body)
            if (r.ok) patch.lang?.let { syncedLang = it }
        } catch (e: IOException) {
            Log.w(TAG, "Preferences not saved on the Gateway", e)
        } catch (e: JSONException) {
            Log.w(TAG, "Preferences not saved on the Gateway", e)
        }
    }

    override suspend fun turn(text: String, lang: Lang): Turn = orOffline({ offlineTurn(text, lang) }) {
        if (syncedLang != lang) {
            call("PUT", "/me/preferences", JSONObject().put("lang", lang.code))
            syncedLang = lang
        }
        var r = postTurn(text)
        if (r.status == 404) {
            conversationId = null
            r = postTurn(text)
        }
        if (r.status >= 500 && r.code.isBlank()) throw GatewayTrouble("HTTP ${r.status}")
        if (!r.ok) {
            val line = r.say(words.couldNotDo)
            Turn(text, line, line, "gateway", null)
        } else {
            val o = r.obj
            val made = o.optJSONObject("draft")?.let { draftFrom(it) }
            val say = o.text("say").ifBlank { o.text("reply") }
            Turn(o.text("heard").ifBlank { text }, o.text("reply").ifBlank { say }, say, o.text("engine"), made)
        }
    }

    // Offline, the bank inside the app may answer a question, but never hands back a draft of its own.
    private suspend fun offlineTurn(text: String, lang: Lang): Turn {
        val t = fallback.turn(text, lang)
        if (t.draft == null) return t
        val line = Words.of(lang).cannotReachBank
        return Turn(t.heard, line, line, t.engine, null)
    }

    private suspend fun postTurn(text: String): Reply {
        val id = conversationId ?: call("POST", "/conversations").orTrouble().obj.getString("id").also { conversationId = it }
        // The Gateway answers by rules after 8 s; keep this timeout above that.
        return call("POST", "/conversations/$id/turns", JSONObject().put("text", text), timeoutMs = 25_000)
    }

    override suspend fun draftTransfer(payeeId: String, amountKobo: Long): Drafted {
        val body = JSONObject().put("kind", "transfer").put("amount_kobo", amountKobo).put("payee_id", payeeId).put("source", "app")
        return drafting(body, words.couldNotPrepare)
    }

    override suspend fun draftTransferToAccount(bankCode: String, accountNumber: String, amountKobo: Long): Drafted {
        val body = JSONObject().put("kind", "transfer").put("amount_kobo", amountKobo)
            .put("bank_code", bankCode).put("account_number", accountNumber).put("source", "app")
        return drafting(body, words.couldNotPrepare)
    }

    override suspend fun draftCardBlock(cardId: String): Drafted {
        val body = JSONObject().put("kind", "card_block").put("card_id", cardId).put("source", "app")
        return drafting(body, words.couldNotPrepareBlock)
    }

    // Money is drafted by the Gateway or not at all, even when the session has gone offline.
    private suspend fun drafting(body: JSONObject, line: String): Drafted = try {
        made(call("POST", "/drafts", body), line)
    } catch (e: IOException) {
        if (Reach.unreachable(e)) goOffline(e)
        Drafted.Refused(Drafted.UNREACHABLE, words.cannotReachBank)
    } catch (e: JSONException) {
        Drafted.Refused(Drafted.UNREACHABLE, words.cannotReachBank)
    }

    private fun made(r: Reply, line: String): Drafted = when {
        r.ok -> Drafted.Made(draftFrom(r.obj))
        r.status >= 500 && r.code.isBlank() -> throw GatewayTrouble("HTTP ${r.status}")
        else -> Drafted.Refused(r.code.ifBlank { "failed" }, r.say(line))
    }

    override suspend fun draft(draftId: String): Draft? = peek(draftId)?.let { draftFrom(it) }

    private suspend fun peek(draftId: String): JSONObject? = try {
        val r = call("GET", "/drafts/$draftId")
        if (r.ok && r.obj.text("status").isNotBlank()) r.obj else null
    } catch (e: IOException) {
        null
    } catch (e: JSONException) {
        null
    }

    override suspend fun confirm(draftId: String, readbackHash: String, pinBlock: String, callActive: Boolean): Outcome {
        val body = JSONObject().put("readback_hash", readbackHash).put("pin_block", pinBlock).put("call_active", callActive)
        heardHashes[draftId] = readbackHash
        // Reuse this key only to resend a lost answer: the Gateway replays it.
        val key = UUID.randomUUID().toString()
        var r: Reply? = null
        for (attempt in 0 until 2) {
            r = try {
                call("POST", "/drafts/$draftId/confirm", body, timeoutMs = 30_000, key = key)
            } catch (e: IOException) {
                Log.w(TAG, "Confirm answer lost (try ${attempt + 1})", e)
                null
            } catch (e: JSONException) {
                Log.w(TAG, "Confirm answer unreadable (try ${attempt + 1})", e)
                null
            }
            // 5xx and 429 are not stored by the Gateway, so a same-key retry reruns.
            val lost = r == null || (r.status >= 500 && r.code.isBlank())
            if (!lost) break
            if (attempt == 0) delay(1_500)
        }
        val answer = r
        if (answer == null || (answer.status >= 500 && answer.code.isBlank())) return afterLostAnswer(draftId)
        return gateAnswer(answer, draftId)
    }

    private suspend fun gateAnswer(r: Reply, draftId: String): Outcome {
        return when {
            r.status == 200 -> Outcome.Sent(r.obj.text("reference"), r.say(words.sentShort))
            r.status == 202 && r.obj.text("status") == "awaiting_approval" || r.code == "approval_required" -> {
                val approver = r.obj.text("approver").ifBlank { words.yourApprover }
                Outcome.NeedsApproval(approver, r.say(words.approvalWait(approver)))
            }
            r.status == 202 -> settle(draftId)
            else -> problem(r, draftId)
        }
    }

    // Sort by `code`, never by title.
    private suspend fun problem(r: Reply, draftId: String): Outcome {
        val code = r.code
        val o = r.obj
        return when (code) {
            "pin_incorrect" -> o.optInt("tries_left", 0).let { Outcome.WrongPin(it, r.say(pinWrongLine(it))) }
            "pause_required" -> Outcome.Paused(r.say(words.pauseFull))
            "readback_mismatch" -> {
                val rb = o.optJSONObject("readback")
                val hash = o.text("readback_hash")
                if (rb != null && hash.isNotBlank()) {
                    Outcome.HearAgain(readbackFrom(rb), hash, r.say(words.readbackChanged))
                } else {
                    Outcome.NotSent(code, true, r.say(words.changedNothingSent))
                }
            }
            "already_executed" -> Outcome.Sent(o.text("reference"), r.say(words.alreadyDone))
            "processing" -> settle(draftId)
            "draft_closed" -> Outcome.Stopped(r.say(words.requestClosed))
            "draft_expired" -> Outcome.Expired(r.say(words.expired))
            "approval_expired" -> Outcome.Expired(r.say(words.approvalExpired))
            "bank_unavailable", "rate_limited" -> Outcome.NotSent(code, true, r.say(words.bankBusy))
            "limit_exceeded", "insufficient_funds", "failed", "invalid_amount", "invalid_account",
            "account_dormant", "unknown_bank", "payee_not_found", "card_not_found", "already_blocked",
            "not_found", "forbidden", "unauthorized", "invalid_request", "idempotency_key_reused" ->
                Outcome.NotSent(code, false, r.say(words.didNotGoThrough))
            else -> when {
                // A proxy page or unknown 5xx is not the gate's answer: treat as lost.
                r.status >= 500 -> afterLostAnswer(draftId)
                else -> Outcome.NotSent(code.ifBlank { "http_${r.status}" }, false, r.say(words.didNotGoThrough))
            }
        }
    }

    private fun pinWrongLine(triesLeft: Int): String = if (triesLeft > 0) words.pinWrong(triesLeft) else words.pinLockedFull

    // A card block is no transfer: an unknown kind gets the plain lines.
    private fun unsure(draftId: String): String = if (kinds[draftId] == DraftKind.Transfer) words.unsureSent else words.unsureDone

    private fun onItsWay(draftId: String): String = if (kinds[draftId] == DraftKind.Transfer) words.onItsWay else words.stillWorking

    private suspend fun afterLostAnswer(draftId: String): Outcome {
        var o: JSONObject? = null
        for (attempt in 0 until 3) {
            if (attempt > 0) delay(1_500)
            o = peek(draftId)
            if (o != null) break
        }
        val seen = o ?: return Outcome.Unsure(unsure(draftId))
        val say = seen.text("say")
        return when (DraftStatus.fromWire(seen.text("status"))) {
            DraftStatus.Executed -> Outcome.Sent(seen.text("reference"), say.ifBlank { words.sentShort })
            DraftStatus.AwaitingApproval -> Outcome.NeedsApproval(words.yourApprover, say.ifBlank { words.stillWithApprover })
            DraftStatus.Processing -> settle(draftId)
            DraftStatus.Paused -> Outcome.Paused(words.pauseFull)
            DraftStatus.Cancelled -> Outcome.Stopped(say.ifBlank { words.cancelled })
            DraftStatus.Expired -> Outcome.Expired(say.ifBlank { words.expired })
            DraftStatus.Failed -> Outcome.NotSent("failed", false, say.ifBlank { words.didNotGoThrough })
            // The gate may still be working, and its last `say` may be stale.
            DraftStatus.AwaitingConfirm, null -> Outcome.Unsure(unsure(draftId))
        }
    }

    private suspend fun settle(draftId: String): Outcome {
        repeat(20) {
            delay(2_000)
            val o = peek(draftId) ?: return@repeat // a dropped check proves nothing
            val say = o.text("say")
            when (DraftStatus.fromWire(o.text("status"))) {
                DraftStatus.Executed -> return Outcome.Sent(o.text("reference"), say.ifBlank { words.sentShort })
                DraftStatus.Processing -> Unit
                DraftStatus.AwaitingConfirm -> return backWithCustomer(draftId, o, words.bankDidNotTake)
                DraftStatus.Paused -> return Outcome.Paused(say.ifBlank { words.pauseFull })
                DraftStatus.Cancelled -> return Outcome.Stopped(say.ifBlank { words.cancelled })
                DraftStatus.Expired -> return Outcome.Expired(say.ifBlank { words.expired })
                DraftStatus.AwaitingApproval -> return Outcome.NeedsApproval(words.yourApprover, say.ifBlank { words.stillWithApprover })
                DraftStatus.Failed -> return Outcome.NotSent("failed", false, say.ifBlank { words.didNotGoThrough })
                null -> return Outcome.Unsure(unsure(draftId))
            }
        }
        return Outcome.Unsure(onItsWay(draftId))
    }

    override suspend fun answerPause(draftId: String, stop: Boolean): Outcome {
        val body = JSONObject().put("answer", if (stop) "stop" else "chose_myself")
        // Same key for the resend: a lost answer is replayed, not refused as not paused.
        val key = UUID.randomUUID().toString()
        var r: Reply? = null
        for (attempt in 0 until 2) {
            r = try {
                call("POST", "/drafts/$draftId/pause-answer", body, key = key)
            } catch (e: IOException) {
                null
            } catch (e: JSONException) {
                null
            }
            val lost = r == null || (r.status >= 500 && r.code.isBlank())
            if (!lost) break
            if (attempt == 0) delay(1_500)
        }
        // A pause answer never moves money.
        val answer = r ?: return Outcome.NotSent("unreachable", true, words.cannotReachBank)
        return when {
            answer.ok && answer.obj.text("status") == "cancelled" -> Outcome.Stopped(answer.say(words.stoppedSafeNothingSent))
            answer.ok -> Outcome.Resumed(answer.say(words.pulseAgain))
            answer.code == "not_paused" -> afterPauseAnswer(draftId, stop)
            answer.code == "draft_closed" -> Outcome.Stopped(answer.say(words.requestClosed))
            answer.code == "draft_expired" -> Outcome.Expired(answer.say(words.expired))
            else -> Outcome.NotSent(answer.code.ifBlank { "failed" }, answer.status >= 500, answer.say(words.didNotGoThrough))
        }
    }

    // An earlier answer may have landed after all: carry on from where the draft really is.
    private suspend fun afterPauseAnswer(draftId: String, stop: Boolean): Outcome {
        val o = peek(draftId) ?: return Outcome.NotSent("unreachable", true, words.cannotReachBank)
        val say = o.text("say")
        return when (DraftStatus.fromWire(o.text("status"))) {
            DraftStatus.AwaitingConfirm -> if (stop) cancel(draftId) else Outcome.Resumed(words.pulseAgain)
            DraftStatus.Paused -> Outcome.Paused(say.ifBlank { words.pauseFull })
            DraftStatus.Cancelled -> Outcome.Stopped(say.ifBlank { if (stop) words.stoppedSafeNothingSent else words.cancelled })
            DraftStatus.Expired -> Outcome.Expired(say.ifBlank { words.expired })
            DraftStatus.AwaitingApproval -> Outcome.NeedsApproval(words.yourApprover, say.ifBlank { words.stillWithApprover })
            DraftStatus.Processing -> settle(draftId)
            DraftStatus.Executed -> Outcome.Sent(o.text("reference"), say.ifBlank { words.sentShort })
            DraftStatus.Failed -> Outcome.NotSent("failed", false, say.ifBlank { words.didNotGoThrough })
            null -> Outcome.NotSent("unreachable", true, words.cannotReachBank)
        }
    }

    override suspend fun cancel(draftId: String): Outcome {
        val r = try {
            call("POST", "/drafts/$draftId/cancel")
        } catch (e: IOException) {
            return Outcome.NotSent("unreachable", true, words.cancelFailed)
        } catch (e: JSONException) {
            return Outcome.NotSent("unreachable", true, words.cancelFailed)
        }
        return when {
            r.ok -> Outcome.Stopped(r.say(words.cancelled))
            r.code == "already_executed" -> Outcome.Sent(r.obj.text("reference"), r.say(words.alreadySent))
            r.code == "draft_closed" -> Outcome.Stopped(r.say(words.cancelled))
            r.code == "processing" -> Outcome.Unsure(onItsWay(draftId))
            else -> Outcome.NotSent(r.code.ifBlank { "failed" }, true, r.say(words.cancelFailed))
        }
    }

    override suspend fun awaitDecision(draftId: String): Outcome {
        var misses = 0
        var atBank = 0
        while (true) {
            delay(2_000)
            // A dropped poll or a proxy error page is not a decision.
            val o = peek(draftId)
            if (o == null) {
                misses += 1
                if (misses >= 60) return Outcome.Unsure(words.approverUnreachable)
                continue
            }
            misses = 0
            val say = o.text("say")
            when (DraftStatus.fromWire(o.text("status"))) {
                DraftStatus.AwaitingApproval -> continue
                DraftStatus.Processing -> {
                    // About two minutes at the bank: money may have moved, so never say it did not.
                    atBank += 1
                    if (atBank >= 60) return Outcome.Unsure(unsure(draftId))
                }
                DraftStatus.Executed -> return Outcome.Sent(o.text("reference"), say.ifBlank { words.approvedSent })
                DraftStatus.Cancelled -> return Outcome.Stopped(say.ifBlank { words.approverSaidNo })
                DraftStatus.Expired -> return Outcome.Expired(say.ifBlank { words.approvalExpired })
                DraftStatus.AwaitingConfirm -> return backWithCustomer(draftId, o, words.bankDidNotTake)
                // Held after the approval: the customer answers the pause.
                DraftStatus.Paused -> return Outcome.Paused(say.ifBlank { words.pauseFull })
                DraftStatus.Failed -> return Outcome.NotSent(o.text("code").ifBlank { "failed" }, false, say.ifBlank { words.didNotGoThrough })
                null -> return Outcome.Unsure(unsure(draftId))
            }
        }
    }

    // Back with the customer: a readback that changed since they confirmed is heard again, else the same one can be confirmed again.
    private fun backWithCustomer(draftId: String, o: JSONObject, fallbackLine: String): Outcome {
        val say = o.text("say")
        val rb = o.optJSONObject("readback")
        val hash = o.text("readback_hash")
        val changed = if (rb != null && hash.isNotBlank() && hash != heardHashes[draftId]) runCatching { readbackFrom(rb) }.getOrNull() else null
        return if (changed != null) {
            Outcome.HearAgain(changed, hash, say.ifBlank { words.readbackChanged })
        } else {
            Outcome.NotSent("not_sent", true, say.ifBlank { fallbackLine })
        }
    }

    suspend fun resetMock(): Boolean {
        if (mockAdminToken.isBlank()) return false
        return try {
            val r = send("POST", "$origin/_mock/reset", null, auth = false, timeoutMs = 15_000, extraHeaders = mapOf("X-Mock-Admin" to mockAdminToken))
            if (!r.ok) Log.w(TAG, "Mock reset refused: HTTP ${r.status}")
            r.ok
        } catch (e: IOException) {
            Log.w(TAG, "Mock reset failed", e)
            false
        } catch (e: JSONException) {
            false
        }
    }

    private fun draftFrom(o: JSONObject): Draft = Draft(
        id = o.getString("id"),
        kind = DraftKind.fromWire(o.text("kind")),
        status = DraftStatus.fromWire(o.text("status")) ?: DraftStatus.AwaitingConfirm,
        source = o.text("source").ifBlank { "app" },
        readback = readbackFrom(o.getJSONObject("readback")),
        readbackHash = o.getString("readback_hash"),
        expiresAt = o.text("expires_at").ifBlank { null },
        reference = o.text("reference").ifBlank { null },
        say = o.text("say").ifBlank { null },
    ).also { kinds[it.id] = it.kind }

    private fun readbackFrom(rb: JSONObject): Readback {
        val spoken = rb.optJSONArray("spoken") ?: JSONArray()
        return Readback(
            kind = DraftKind.fromWire(rb.text("kind")),
            name = rb.text("name"),
            bank = rb.text("bank"),
            last4 = rb.text("account_last4"),
            amountKobo = if (rb.has("amount_kobo") && !rb.isNull("amount_kobo")) rb.getLong("amount_kobo") else null,
            amountText = rb.text("amount_text").ifBlank { null },
            lang = rb.text("lang").ifBlank { "en" },
            spoken = List(spoken.length()) { spoken.getString(it) },
        )
    }

    private companion object {
        const val TAG = "OminiraGateway"
    }
}

// optString turns JSON null into "null"; this gives "" instead.
private fun JSONObject.text(key: String): String = if (!has(key) || isNull(key)) "" else optString(key, "")

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.UUID

private class GatewayTrouble(message: String) : IOException(message)

class GatewayOminiraApi(
    baseUrl: String,
    token: String,
    private val mockAdminToken: String,
    private val fallback: OminiraApi = FakeOminiraApi(),
) : OminiraApi {

    private val base = baseUrl.trim().trimEnd('/')
    private val origin = base.removeSuffix("/v1")

    @Volatile private var bearer = token

    @Volatile var offline = false
        private set

    private val gatewayDrafts: MutableSet<String> = Collections.synchronizedSet(HashSet())

    @Volatile private var accountId: String? = null
    @Volatile private var conversationId: String? = null
    @Volatile private var syncedLang: Lang? = null

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

    // Non-money calls only: a money call never falls back offline.
    private suspend fun <T> orOffline(offlineCall: suspend () -> T, online: suspend () -> T): T {
        if (offline) return offlineCall()
        return try {
            online()
        } catch (e: IOException) {
            goOffline(e)
            offlineCall()
        } catch (e: JSONException) {
            goOffline(e)
            offlineCall()
        }
    }

    private fun goOffline(e: Exception) {
        Log.w(TAG, "Gateway unreachable; this session carries on offline", e)
        offline = true
    }

    private fun Reply.orTrouble(): Reply = if (ok) this else throw GatewayTrouble("HTTP $status $code")

    private fun ours(draftId: String): Boolean = draftId in gatewayDrafts

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

    override suspend fun readIdDocument(sessionId: String): IdDetails = orOffline({ fallback.readIdDocument(sessionId) }) {
        val o = call("POST", "/onboarding/$sessionId/id-document", auth = false).orTrouble().obj
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

    override suspend fun setPin(sessionId: String, pinBlock: String): Boolean = orOffline({ fallback.setPin(sessionId, pinBlock) }) {
        val r = call("POST", "/onboarding/$sessionId/pin", JSONObject().put("pin_block", pinBlock), auth = false)
        when {
            r.ok -> true
            r.status in 400..499 -> false
            else -> throw GatewayTrouble("HTTP ${r.status}")
        }
    }

    override suspend fun completeOnboarding(sessionId: String): Boolean = orOffline({ fallback.completeOnboarding(sessionId) }) {
        val r = call("POST", "/onboarding/$sessionId/complete", auth = false)
        when {
            r.ok -> {
                r.obj.text("token").takeIf { it.isNotBlank() }?.let { bearer = it }
                true
            }
            r.code == "onboarding_incomplete" -> false
            else -> throw GatewayTrouble("HTTP ${r.status}")
        }
    }

    override suspend fun unlock(pinBlock: String): Unlock = orOffline({ fallback.unlock(pinBlock) }) {
        val r = call("POST", "/auth/unlock", JSONObject().put("pin_block", pinBlock))
        when {
            r.ok -> Unlock.Open(r.say("Welcome back."))
            r.code == "pin_incorrect" -> Unlock.Wrong(r.obj.optInt("tries_left", 0), r.say("That PIN did not match."))
            else -> throw GatewayTrouble("HTTP ${r.status} ${r.code}")
        }
    }

    override suspend fun accounts(): List<Account> = orOffline({ fallback.accounts() }) {
        val a = call("GET", "/accounts").orTrouble().list
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Account(o.getString("id"), o.text("name").ifBlank { "Current account" }, o.text("last4"), o.getLong("balance_kobo"))
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
        orOffline({ fallback.transactions(accountId, limit, direction) }) {
            val query = "?limit=$limit" + (direction?.let { "&direction=$it" } ?: "")
            val a = call("GET", "/accounts/${accountIdOr(accountId)}/transactions$query").orTrouble().list
            List(a.length()) { i ->
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

    override suspend fun turn(text: String, lang: Lang): Turn = orOffline({ fallback.turn(text, lang) }) {
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
            val line = r.say("Sorry, could you say that again?")
            Turn(text, line, line, "gateway", null)
        } else {
            val o = r.obj
            val made = o.optJSONObject("draft")?.let { draftFrom(it) }
            val say = o.text("say").ifBlank { o.text("reply") }
            Turn(o.text("heard").ifBlank { text }, o.text("reply").ifBlank { say }, say, o.text("engine"), made)
        }
    }

    private suspend fun postTurn(text: String): Reply {
        val id = conversationId ?: call("POST", "/conversations").orTrouble().obj.getString("id").also { conversationId = it }
        // The Gateway answers by rules after 8 s; keep this timeout above that.
        return call("POST", "/conversations/$id/turns", JSONObject().put("text", text), timeoutMs = 25_000)
    }

    override suspend fun draftTransfer(payeeId: String, amountKobo: Long): Drafted =
        orOffline({ fallback.draftTransfer(payeeId, amountKobo) }) {
            val body = JSONObject().put("kind", "transfer").put("amount_kobo", amountKobo).put("payee_id", payeeId).put("source", "app")
            made(call("POST", "/drafts", body), "I could not prepare that transfer. Nothing was sent.")
        }

    override suspend fun draftCardBlock(cardId: String): Drafted =
        orOffline({ fallback.draftCardBlock(cardId) }) {
            val body = JSONObject().put("kind", "card_block").put("card_id", cardId).put("source", "app")
            made(call("POST", "/drafts", body), "I could not prepare blocking that card. Nothing has changed.")
        }

    private fun made(r: Reply, line: String): Drafted = when {
        r.ok -> Drafted.Made(draftFrom(r.obj))
        r.status >= 500 && r.code.isBlank() -> throw GatewayTrouble("HTTP ${r.status}")
        else -> Drafted.Refused(r.code.ifBlank { "failed" }, r.say(line))
    }

    override suspend fun draft(draftId: String): Draft? {
        if (!ours(draftId)) return fallback.draft(draftId)
        return peek(draftId)?.let { draftFrom(it) }
    }

    private suspend fun peek(draftId: String): JSONObject? = try {
        val r = call("GET", "/drafts/$draftId")
        if (r.ok && r.obj.text("status").isNotBlank()) r.obj else null
    } catch (e: IOException) {
        null
    } catch (e: JSONException) {
        null
    }

    override suspend fun confirm(draftId: String, readbackHash: String, pinBlock: String, callActive: Boolean): Outcome {
        if (!ours(draftId)) return fallback.confirm(draftId, readbackHash, pinBlock, callActive)
        val body = JSONObject().put("readback_hash", readbackHash).put("pin_block", pinBlock).put("call_active", callActive)
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
            r.status == 200 -> Outcome.Sent(r.obj.text("reference"), r.say("Sent."))
            r.status == 202 && r.obj.text("status") == "awaiting_approval" || r.code == "approval_required" ->
                Outcome.NeedsApproval(r.obj.text("approver").ifBlank { "your approver" }, r.say("I have asked your approver. Nothing moves until they say yes."))
            r.status == 202 -> settle(draftId)
            else -> problem(r, draftId)
        }
    }

    // Sort by `code`, never by title.
    private suspend fun problem(r: Reply, draftId: String): Outcome {
        val code = r.code
        val o = r.obj
        return when (code) {
            "pin_incorrect" -> Outcome.WrongPin(o.optInt("tries_left", 0), r.say("That PIN did not match."))
            "pause_required" -> Outcome.Paused(r.say(PAUSE))
            "readback_mismatch" -> {
                val rb = o.optJSONObject("readback")
                val hash = o.text("readback_hash")
                if (rb != null && hash.isNotBlank()) {
                    Outcome.HearAgain(readbackFrom(rb), hash, r.say("Something changed since I read it to you. Please listen again."))
                } else {
                    Outcome.NotSent(code, true, r.say("Something changed since I read it to you. Nothing was sent."))
                }
            }
            "already_executed" -> Outcome.Sent(o.text("reference"), r.say("That was already sent. Nothing more was sent."))
            "processing" -> settle(draftId)
            "draft_closed" -> Outcome.Stopped(r.say("That request is closed. Nothing was sent."))
            "draft_expired", "approval_expired" -> Outcome.Expired(r.say("That request timed out. Nothing was sent."))
            "bank_unavailable", "rate_limited" -> Outcome.NotSent(code, true, r.say("The bank is busy. Nothing was sent. Try again in a minute."))
            "limit_exceeded", "insufficient_funds", "failed", "invalid_amount", "invalid_account",
            "account_dormant", "unknown_bank", "payee_not_found", "card_not_found", "already_blocked",
            "not_found", "forbidden", "unauthorized", "invalid_request", "idempotency_key_reused" ->
                Outcome.NotSent(code, false, r.say("That did not go through. Nothing was sent."))
            else -> when {
                // A proxy page or unknown 5xx is not the gate's answer: treat as lost.
                r.status >= 500 -> afterLostAnswer(draftId)
                else -> Outcome.NotSent(code.ifBlank { "http_${r.status}" }, false, r.say("That did not go through. Nothing was sent."))
            }
        }
    }

    private suspend fun afterLostAnswer(draftId: String): Outcome {
        var o: JSONObject? = null
        for (attempt in 0 until 3) {
            if (attempt > 0) delay(1_500)
            o = peek(draftId)
            if (o != null) break
        }
        val seen = o ?: return Outcome.Unsure(UNSURE)
        val say = seen.text("say")
        return when (DraftStatus.fromWire(seen.text("status"))) {
            DraftStatus.Executed -> Outcome.Sent(seen.text("reference"), say.ifBlank { "Sent." })
            DraftStatus.AwaitingApproval -> Outcome.NeedsApproval("your approver", say.ifBlank { "Waiting for your approver. Nothing has moved yet." })
            DraftStatus.Processing -> settle(draftId)
            DraftStatus.Paused -> Outcome.Paused(PAUSE)
            DraftStatus.Cancelled -> Outcome.Stopped(say.ifBlank { "Cancelled. Nothing was sent." })
            DraftStatus.Expired -> Outcome.Expired(say.ifBlank { "That request timed out. Nothing was sent." })
            DraftStatus.Failed -> Outcome.NotSent("failed", false, say.ifBlank { "That did not go through. Nothing was sent." })
            // The gate may still be working, and its last `say` may be stale.
            DraftStatus.AwaitingConfirm, null -> Outcome.Unsure(UNSURE)
        }
    }

    private suspend fun settle(draftId: String): Outcome {
        repeat(20) {
            delay(2_000)
            val o = peek(draftId) ?: return@repeat // a dropped check proves nothing
            val say = o.text("say")
            when (DraftStatus.fromWire(o.text("status"))) {
                DraftStatus.Executed -> return Outcome.Sent(o.text("reference"), say.ifBlank { "Sent." })
                DraftStatus.Processing -> Unit
                DraftStatus.AwaitingConfirm -> return backWithCustomer(o, "The bank did not take it. Nothing was sent. You can try again.")
                DraftStatus.Cancelled -> return Outcome.Stopped(say.ifBlank { "Cancelled. Nothing was sent." })
                DraftStatus.Expired -> return Outcome.Expired(say.ifBlank { "That request timed out. Nothing was sent." })
                DraftStatus.AwaitingApproval -> return Outcome.NeedsApproval("your approver", say.ifBlank { "Waiting for your approver." })
                DraftStatus.Failed, DraftStatus.Paused, null ->
                    return Outcome.NotSent("failed", false, say.ifBlank { "That did not go through. Nothing was sent." })
            }
        }
        return Outcome.Unsure(ON_ITS_WAY)
    }

    override suspend fun answerPause(draftId: String, stop: Boolean): Outcome {
        if (!ours(draftId)) return fallback.answerPause(draftId, stop)
        val r = try {
            call("POST", "/drafts/$draftId/pause-answer", JSONObject().put("answer", if (stop) "stop" else "chose_myself"))
        } catch (e: IOException) {
            return Outcome.NotSent("unreachable", true, UNREACHABLE) // a pause answer never moves money
        } catch (e: JSONException) {
            return Outcome.NotSent("unreachable", true, UNREACHABLE)
        }
        return when {
            r.ok && r.obj.text("status") == "cancelled" -> Outcome.Stopped(r.say("Stopped. Your money is safe. Nothing was sent."))
            r.ok -> Outcome.Resumed(r.say("Okay. Pulse your PIN again to send it."))
            r.code == "draft_closed" -> Outcome.Stopped(r.say("That request is closed. Nothing was sent."))
            r.code == "draft_expired" -> Outcome.Expired(r.say("That request timed out. Nothing was sent."))
            else -> Outcome.NotSent(r.code.ifBlank { "failed" }, r.status >= 500, r.say("That did not go through. Nothing was sent."))
        }
    }

    override suspend fun cancel(draftId: String): Outcome {
        if (!ours(draftId)) return fallback.cancel(draftId)
        val r = try {
            call("POST", "/drafts/$draftId/cancel")
        } catch (e: IOException) {
            return Outcome.NotSent("unreachable", true, CANNOT_CANCEL)
        } catch (e: JSONException) {
            return Outcome.NotSent("unreachable", true, CANNOT_CANCEL)
        }
        return when {
            r.ok -> Outcome.Stopped(r.say("Cancelled. Nothing was sent."))
            r.code == "already_executed" -> Outcome.Sent(r.obj.text("reference"), r.say("That was already sent."))
            r.code == "draft_closed" -> Outcome.Stopped(r.say("Cancelled. Nothing was sent."))
            r.code == "processing" -> Outcome.Unsure(ON_ITS_WAY)
            else -> Outcome.NotSent(r.code.ifBlank { "failed" }, true, r.say(CANNOT_CANCEL))
        }
    }

    override suspend fun awaitDecision(draftId: String): Outcome {
        if (!ours(draftId)) return fallback.awaitDecision(draftId)
        var misses = 0
        while (true) {
            delay(2_000)
            // A dropped poll or a proxy error page is not a decision.
            val o = peek(draftId)
            if (o == null) {
                misses += 1
                if (misses >= 60) return Outcome.Unsure("I cannot reach the bank to hear your approver's answer. Do not send it again. Check your balance in a few minutes.")
                continue
            }
            misses = 0
            val say = o.text("say")
            when (DraftStatus.fromWire(o.text("status"))) {
                DraftStatus.AwaitingApproval, DraftStatus.Processing -> continue
                DraftStatus.Executed -> return Outcome.Sent(o.text("reference"), say.ifBlank { "Approved and sent." })
                DraftStatus.Cancelled -> return Outcome.Stopped(say.ifBlank { "Your approver said no. Nothing was sent." })
                DraftStatus.Expired -> return Outcome.Expired(say.ifBlank { "Your approver did not answer in time. Nothing was sent." })
                DraftStatus.AwaitingConfirm -> return backWithCustomer(o, "The bank did not answer after your approver said yes. Nothing was sent. Try again in a minute.")
                DraftStatus.Failed, DraftStatus.Paused, null ->
                    return Outcome.NotSent(o.text("code").ifBlank { "failed" }, false, say.ifBlank { "That did not go through. Nothing was sent." })
            }
        }
        return Outcome.Unsure(UNSURE)
    }

    private fun backWithCustomer(o: JSONObject, fallbackLine: String): Outcome {
        val say = o.text("say").ifBlank { fallbackLine }
        val code = o.text("code")
        val rb = o.optJSONObject("readback")
        val hash = o.text("readback_hash")
        return if (code == "readback_mismatch" && rb != null && hash.isNotBlank()) {
            Outcome.HearAgain(readbackFrom(rb), hash, say)
        } else {
            Outcome.NotSent(code.ifBlank { "not_sent" }, true, say)
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

    private fun draftFrom(o: JSONObject): Draft {
        val d = Draft(
            id = o.getString("id"),
            kind = DraftKind.fromWire(o.text("kind")),
            status = DraftStatus.fromWire(o.text("status")) ?: DraftStatus.AwaitingConfirm,
            source = o.text("source").ifBlank { "app" },
            readback = readbackFrom(o.getJSONObject("readback")),
            readbackHash = o.getString("readback_hash"),
            expiresAt = o.text("expires_at").ifBlank { null },
            reference = o.text("reference").ifBlank { null },
            say = o.text("say").ifBlank { null },
        )
        gatewayDrafts += d.id
        return d
    }

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
        const val PAUSE = "Is someone on the phone telling you to send this? Nothing has been sent."
        const val UNREACHABLE = "I cannot reach the bank right now. Nothing was sent. Try again in a minute."
        const val CANNOT_CANCEL = "I could not reach the bank to cancel it. It is still waiting. Try again."
        const val UNSURE = "I could not tell if it went through. Do not send it again. Check your balance in a minute."
        const val ON_ITS_WAY = "Your transfer is still on its way. Do not send it again. Check your balance in a few minutes."
    }
}

// optString turns JSON null into "null"; this gives "" instead.
private fun JSONObject.text(key: String): String = if (!has(key) || isNull(key)) "" else optString(key, "")

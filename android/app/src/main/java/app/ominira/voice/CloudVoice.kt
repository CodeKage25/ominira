package app.ominira.voice

import android.util.Log
import app.ominira.core.Lang
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Speech through the Gateway, which holds the YarnGPT key; the phone never sees it.
 * Speaking: POST /v1/speech/say returns a short-lived audio link that [Speaker] plays.
 * Listening: POST /v1/speech/listen takes a 16 kHz mono WAV and returns the words.
 *
 * Every call here blocks: run it off the main thread. After any failure cloud speech rests for a
 * minute and the phone's own voice and recogniser take over.
 */
class CloudVoice(baseUrl: String, private val token: String) {

    private val base = baseUrl.trim().trimEnd('/')
    private val origin = base.removeSuffix("/v1")

    @Volatile private var restUntil = 0L

    /** null until GET /v1/speech/status has answered; false when the Gateway has no speech. */
    @Volatile private var serverHasSpeech: Boolean? = null

    val usable: Boolean
        get() = serverHasSpeech != false && System.currentTimeMillis() >= restUntil

    /** What the Gateway heard. A spoken PIN never comes back as words: [pinSpoken] is set instead. */
    class Heard(val text: String, val pinSpoken: Boolean, val say: String?)

    /** An audio link for [text] in [lang], or null (cloud speech then rests). */
    fun audioUrl(text: String, lang: Lang): String? {
        if (!checkStatus()) return null
        val body = JSONObject().put("text", text.take(600)).put("lang", lang.code)
        val o = post("/speech/say", "application/json; charset=utf-8", body.toString().toByteArray(Charsets.UTF_8), 10_000) ?: return null
        val url = if (o.isNull("audio_url")) "" else o.optString("audio_url", "")
        return when {
            url.startsWith("http") -> url
            url.startsWith("/") -> origin + url
            else -> null
        }
    }

    /** The words in [wav], or null (cloud speech then rests). */
    fun transcribe(wav: ByteArray): Heard? {
        if (!checkStatus()) return null
        val o = post("/speech/listen", "audio/wav", wav, 35_000) ?: return null
        val say = if (o.isNull("say")) null else o.optString("say", "").ifBlank { null }
        val text = if (o.isNull("text")) "" else o.optString("text", "")
        return Heard(text, o.optBoolean("pin_spoken", false), say)
    }

    /** Asks GET /v1/speech/status once. A Gateway without speech is never asked again. */
    private fun checkStatus(): Boolean {
        serverHasSpeech?.let { return it }
        var conn: HttpURLConnection? = null
        try {
            conn = URL("$base/speech/status").openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            val status = conn.responseCode
            if (status in 200..299) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val enabled = JSONObject(text).optBoolean("enabled", false)
                serverHasSpeech = enabled
                return enabled
            }
            rest("status HTTP $status")
        } catch (e: IOException) {
            rest(e.toString())
        } catch (e: JSONException) {
            rest(e.toString())
        } catch (e: RuntimeException) {
            rest(e.toString())
        } finally {
            conn?.disconnect()
        }
        return false
    }

    private fun post(path: String, contentType: String, body: ByteArray, readTimeoutMs: Int): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(base + path).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 6_000
            conn.readTimeout = readTimeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", contentType)
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            val status = conn.responseCode
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            when {
                status in 200..299 -> JSONObject(text)
                // Too long, too short or not audio: this answer only. Anything else: rest.
                status == 413 || status == 415 || status == 422 -> {
                    Log.w(TAG, "Speech call refused: HTTP $status")
                    null
                }
                else -> {
                    rest("HTTP $status")
                    null
                }
            }
        } catch (e: IOException) {
            rest(e.toString())
            null
        } catch (e: JSONException) {
            rest(e.toString())
            null
        } catch (e: RuntimeException) {
            // Nothing from the network stack may crash a worker thread.
            rest(e.toString())
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun rest(why: String) {
        Log.w(TAG, "Cloud speech resting for a minute: $why")
        restUntil = System.currentTimeMillis() + 60_000
    }

    private companion object {
        const val TAG = "OminiraSpeech"
    }
}

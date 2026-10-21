package app.ominira.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.ominira.core.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class Speaker(context: Context, private val cloud: () -> CloudVoice?) : TextToSpeech.OnInitListener {

    private class Request(val lang: Lang, val lines: List<String>, val gapMs: Long, val phoneFallback: Boolean)

    @Volatile private var engineOk = false
    private val engineDone = MutableStateFlow(false)
    private var waiting: Request? = null

    private val app = context.applicationContext
    private val clips = ClipIndex(app)
    private val afterLine = ConcurrentHashMap<String, () -> Unit>()
    private val speech = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newCachedThreadPool()
    private val turn = AtomicInteger(0)
    private var player: MediaPlayer? = null
    @Volatile private var lastPhoneLine: String? = null
    private var counter = 0

    @Volatile var rate: Float = 0.95f

    private val _talking = MutableStateFlow(false)
    val talking: StateFlow<Boolean> = _talking.asStateFlow()

    private val _caption = MutableStateFlow("")
    val caption: StateFlow<String> = _caption.asStateFlow()

    // Declared last: with no engine, onInit runs inside this constructor.
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        engineOk = status == TextToSpeech.SUCCESS
        if (engineOk) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = ended(utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = ended(utteranceId)

                override fun onError(utteranceId: String?, errorCode: Int) = ended(utteranceId)
            })
        }
        engineDone.value = true
        main.post {
            val w = waiting
            waiting = null
            if (w != null) onPhone(w)
        }
    }

    // Main thread. phoneFallback false: skip a line rather than read it in English.
    fun speak(lang: Lang, lines: List<String>, gapMs: Long = 500, phoneFallback: Boolean = true) {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty() || pool.isShutdown) return
        val ticket = turn.incrementAndGet()
        releasePlayer()
        waiting = null
        _talking.value = true
        _caption.value = clean.joinToString(" ")
        val request = Request(lang, clean, gapMs, phoneFallback)
        // Bundled clips first: they need no network. Then the cloud voice, then the phone's.
        val bundled = clean.map { clips.find(lang, it) }
        val voice = cloud()
        when {
            bundled.all { it != null } -> onDevice(ticket, request, bundled)
            voice != null && voice.usable -> inCloud(voice, ticket, request)
            else -> fallBack(request, ticket)
        }
    }

    private fun ended(utteranceId: String?) {
        if (utteranceId == null) return
        if (utteranceId == lastPhoneLine) _talking.value = false
        afterLine.remove(utteranceId)?.let { main.post(it) }
    }

    // Main thread. A bundled clip for each line that has one, the phone's voice for the rest, in order.
    private fun onDevice(ticket: Int, request: Request, bundled: List<String?>) {
        if (bundled.any { it == null } && !engineDone.value) return onPhone(request)
        if (engineOk) tts.stop()
        fun line(i: Int) {
            if (ticket != turn.get()) return
            if (i > bundled.lastIndex) {
                _talking.value = false
                return
            }
            val next: () -> Unit = { main.postDelayed({ line(i + 1) }, if (i < bundled.lastIndex) request.gapMs else 0L) }
            val clip = bundled[i]
            when {
                clip != null -> playClip(clip, ticket, next)
                request.phoneFallback && engineOk -> phoneLine(request.lang, request.lines[i], next)
                else -> next()
            }
        }
        line(0)
    }

    private fun playClip(path: String, ticket: Int, then: () -> Unit) {
        releasePlayer()
        val mp = MediaPlayer()
        player = mp
        fun finish() {
            if (player === mp) releasePlayer() else runCatching { mp.release() }
            if (ticket == turn.get()) then()
        }
        try {
            app.assets.openFd(path).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            mp.setAudioAttributes(speech)
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ ->
                finish()
                true
            }
            mp.prepare()
            mp.start()
            runCatching { mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed()) }
        } catch (e: Exception) {
            finish()
        }
    }

    private fun phoneLine(lang: Lang, line: String, then: () -> Unit) {
        chooseLanguage(lang)
        tts.setSpeechRate(rate)
        counter += 1
        val id = "om-$counter-line"
        afterLine[id] = then
        tts.speak(line, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    // 0.95 is the app's normal rate; recorded voices play normal at 1.0.
    private fun playbackSpeed(): Float = (rate / 0.95f).coerceIn(0.6f, 2.0f)

    private fun inCloud(voice: CloudVoice, ticket: Int, request: Request) {
        if (engineOk) tts.stop()
        // The cloud voice pauses at full stops, so end each line with one.
        val text = request.lines.joinToString(" ") { line ->
            if (line.endsWith(".") || line.endsWith("?") || line.endsWith("!")) line else "$line."
        }
        pool.execute {
            if (ticket != turn.get()) return@execute
            val url = voice.audioUrl(text, request.lang)
            main.post {
                if (ticket != turn.get()) return@post
                if (url == null) fallBack(request, ticket) else play(url, ticket, request)
            }
        }
    }

    // Main thread only.
    private fun play(url: String, ticket: Int, request: Request) {
        releasePlayer()
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(speech)
            mp.setDataSource(url)
            mp.setOnPreparedListener { prepared ->
                if (ticket != turn.get()) {
                    releasePlayer()
                } else {
                    prepared.start()
                    runCatching { prepared.playbackParams = prepared.playbackParams.setSpeed(playbackSpeed()) }
                }
            }
            mp.setOnCompletionListener {
                if (ticket == turn.get()) _talking.value = false
                releasePlayer()
            }
            mp.setOnErrorListener { _, _, _ ->
                releasePlayer()
                if (ticket == turn.get()) fallBack(request, ticket)
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            releasePlayer()
            fallBack(request, ticket)
        }
    }

    // Clips for the lines that have one, then the phone's voice; phoneFallback false skips the rest.
    private fun fallBack(request: Request, ticket: Int) {
        val bundled = request.lines.map { clips.find(request.lang, it) }
        when {
            bundled.any { it != null } -> onDevice(ticket, request, bundled)
            request.phoneFallback -> onPhone(request)
            else -> _talking.value = false
        }
    }

    private fun releasePlayer() {
        val p = player ?: return
        player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    private fun onPhone(request: Request) {
        if (!engineOk) {
            if (engineDone.value) {
                _talking.value = false
            } else {
                waiting = request
            }
            return
        }
        chooseLanguage(request.lang)
        tts.setSpeechRate(rate)
        counter += 1
        request.lines.forEachIndexed { i, line ->
            val id = "om-$counter-$i"
            lastPhoneLine = id
            tts.speak(line, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
            if (i < request.lines.lastIndex) tts.playSilentUtterance(request.gapMs, TextToSpeech.QUEUE_ADD, "$id-gap")
        }
    }

    private fun chooseLanguage(lang: Lang) {
        for (locale in listOf(Locale.forLanguageTag(lang.tag), Locale.forLanguageTag("en-NG"), Locale.UK)) {
            val result = tts.setLanguage(locale)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) return
        }
    }

    fun hasVoiceFor(lang: Lang): Boolean {
        if (cloud()?.usable == true) return true
        if (!engineOk) return false
        return tts.isLanguageAvailable(Locale.forLanguageTag(lang.tag)) >= TextToSpeech.LANG_AVAILABLE
    }

    fun hasPhoneVoiceFor(lang: Lang): Boolean {
        if (!engineOk) return false
        return tts.isLanguageAvailable(Locale.forLanguageTag(lang.tag)) >= TextToSpeech.LANG_AVAILABLE
    }

    suspend fun speakAndWait(lang: Lang, line: String, phoneFallback: Boolean): Boolean {
        val voice = cloud()
        if ((voice == null || !voice.usable) && !phoneFallback && clips.find(lang, line) == null) return false
        speak(lang, listOf(line), phoneFallback = phoneFallback)
        awaitQuiet()
        return true
    }

    fun stop() {
        turn.incrementAndGet()
        waiting = null
        afterLine.clear()
        if (Looper.myLooper() == Looper.getMainLooper()) releasePlayer() else main.post { releasePlayer() }
        if (engineOk) tts.stop()
        _talking.value = false
    }

    suspend fun awaitQuiet(maxMs: Long = 30_000) {
        withTimeoutOrNull(15_000) { engineDone.first { it } }
        withTimeoutOrNull(maxMs) { talking.first { !it } }
    }

    fun shutdown() {
        stop()
        pool.shutdown()
        tts.shutdown()
    }
}

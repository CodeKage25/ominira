package app.ominira.voice

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.ominira.Session
import app.ominira.core.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Ears internal constructor() {
    internal var open: () -> Unit = {}
    internal var close: () -> Unit = {}

    fun listen() = open()

    fun stop() = close()
}

private class Hold {
    var job: Job? = null
    var recognizer: SpeechRecognizer? = null
    var afterPermission: (Boolean) -> Unit = {}

    fun release() {
        job?.cancel()
        job = null
        val r = recognizer ?: return
        recognizer = null
        runCatching { r.cancel() }
        runCatching { r.destroy() }
    }
}

// Phone recognisers often fail quietly on yo/ha/ig: listen in en-NG.
private fun phoneTag(lang: Lang): String = when (lang) {
    Lang.Yo, Lang.Ha, Lang.Ig, Lang.Pcm -> "en-NG"
    Lang.En -> lang.tag
}

@Composable
fun rememberEars(
    session: Session,
    quietMs: Long = 1_200,
    askForMic: Boolean = true,
    onWarned: () -> Unit = {},
    onHeard: (String?) -> Unit,
): Ears {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val heard by rememberUpdatedState(onHeard)
    val warned by rememberUpdatedState(onWarned)
    val ears = remember { Ears() }
    val hold = remember { Hold() }
    DisposableEffect(hold) { onDispose { hold.release() } }

    fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, phoneTag(session.lang))
        .putExtra(RecognizerIntent.EXTRA_PROMPT, session.words.answerOutLoud)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, quietMs)

    val box = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        heard(text?.takeIf { it.isNotBlank() })
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hold.afterPermission(granted)
    }

    fun micGranted(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun systemBox() {
        hold.release()
        try {
            box.launch(intent())
        } catch (e: ActivityNotFoundException) {
            heard(null)
        }
    }

    fun inApp() {
        hold.release()
        val recognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (recognizer == null) {
            systemBox()
            return
        }
        hold.recognizer = recognizer
        var finished = false
        fun finish(text: String?) {
            if (finished) return
            finished = true
            // A recogniser the screen already let go of must not answer any more.
            val current = hold.recognizer === recognizer
            if (current) hold.recognizer = null
            // Destroy after the callback returns, never from inside it.
            scope.launch { runCatching { recognizer.destroy() } }
            if (current) heard(text?.takeIf { it.isNotBlank() })
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) scope.launch { Tones.gotIt() }
                finish(text)
            }

            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS && !finished && hold.recognizer === recognizer) {
                    finished = true
                    systemBox()
                } else {
                    finish(null)
                }
            }
        })
        hold.job = scope.launch {
            Tones.speakNow()
            runCatching { recognizer.startListening(intent()) }.onFailure { finish(null) }
        }
    }

    fun phoneWay() {
        if (micGranted() && SpeechRecognizer.isRecognitionAvailable(context)) inApp() else systemBox()
    }

    fun inCloud(voice: CloudVoice) {
        hold.release()
        hold.job = scope.launch {
            Tones.speakNow()
            when (val recorded = WavRecorder.record(quietMs = quietMs)) {
                WavRecorder.Result.Failed -> phoneWay()
                WavRecorder.Result.Silence -> heard(null)
                is WavRecorder.Result.Audio -> {
                    Tones.gotIt()
                    val words = withContext(Dispatchers.IO) { voice.transcribe(recorded.wav) }
                    when {
                        words == null -> heard(null)
                        words.pinSpoken -> {
                            session.say(words.say ?: session.spoken.pinSpoken)
                            warned()
                        }
                        words.text.isBlank() -> heard(null)
                        else -> heard(words.text)
                    }
                }
            }
        }
    }

    fun afterGrant() {
        val voice = session.activeCloudVoice()
        if (voice != null && voice.usable) inCloud(voice) else phoneWay()
    }

    hold.afterPermission = { granted ->
        session.speaker.stop()
        if (granted) afterGrant() else systemBox()
    }

    ears.open = {
        // Never record the app's own voice.
        session.speaker.stop()
        when {
            micGranted() -> afterGrant()
            askForMic && !session.micAsked -> {
                session.micAsked = true
                // The permission box is silent and easy to miss without sight.
                val allow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "While using the app" else "Allow"
                session.say(session.spoken.tapAllow(allow))
                askMic.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> systemBox()
        }
    }
    ears.close = { hold.release() }
    return ears
}

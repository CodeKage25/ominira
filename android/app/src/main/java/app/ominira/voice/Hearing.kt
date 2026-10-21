package app.ominira.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import app.ominira.BuildConfig
import app.ominira.core.Lang

// Asks the phone's recogniser whether it takes a language (Android 13 and later), instead of guessing.
// English and Pidgin always go to en-NG; yo, ha and ig only to a recogniser that says it knows them.
object Hearing {

    fun englishTag(lang: Lang): String? = if (lang == Lang.En || lang == Lang.Pcm) "en-NG" else null

    // Main thread. done(tag) gets the tag to listen in, or null when the phone cannot take the language.
    fun check(context: Context, lang: Lang, done: (String?) -> Unit) {
        englishTag(lang)?.let { return done(it) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !SpeechRecognizer.isRecognitionAvailable(context)) {
            return done(null)
        }
        val recognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull() ?: return done(null)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.tag)
        fun finish(tag: String?) {
            runCatching { recognizer.destroy() }
            done(tag)
        }
        val asked = runCatching {
            recognizer.checkRecognitionSupport(intent, context.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    // On the phone first: it works with no network. Online languages need one.
                    val tag = pick(support.installedOnDeviceLanguages, lang) ?: pick(support.onlineLanguages, lang)
                    // Ready next time: the phone fetches the model in the background.
                    if (tag == null && pick(support.supportedOnDeviceLanguages, lang) != null) {
                        runCatching { recognizer.triggerModelDownload(intent) }
                    }
                    if (BuildConfig.DEBUG) Log.d("OminiraEars", "recogniser for ${lang.code}: ${tag ?: "none"}")
                    finish(tag)
                }

                override fun onError(error: Int) = finish(null)
            })
        }
        if (asked.isFailure) finish(null)
    }

    private fun pick(tags: List<String>, lang: Lang): String? =
        tags.firstOrNull { it.equals(lang.tag, ignoreCase = true) }
            ?: tags.firstOrNull { it.substringBefore('-').equals(lang.code, ignoreCase = true) }
}

package app.ominira.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object Clips {
    private const val FOLDER = "audio"

    fun has(context: Context, name: String): Boolean =
        runCatching { context.assets.list(FOLDER)?.contains(name) == true }.getOrDefault(false)

    suspend fun play(context: Context, name: String): Boolean {
        if (!has(context, name)) return false
        return suspendCancellableCoroutine { cont ->
            val player = MediaPlayer()
            var finished = false
            fun end(ok: Boolean) {
                if (finished) return
                finished = true
                runCatching { player.release() }
                if (cont.isActive) cont.resume(ok)
            }
            cont.invokeOnCancellation {
                // MediaPlayer is not thread safe; releasing here is fine for a short clip.
                if (!finished) {
                    finished = true
                    runCatching { player.stop() }
                    runCatching { player.release() }
                }
            }
            try {
                context.assets.openFd("$FOLDER/$name").use { fd ->
                    player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                player.setOnCompletionListener { end(true) }
                player.setOnErrorListener { _, _, _ ->
                    end(false)
                    true
                }
                player.setOnPreparedListener { it.start() }
                player.prepareAsync()
            } catch (e: Exception) {
                // A compressed asset cannot be opened as an fd; mp3 is stored as is.
                end(false)
            }
        }
    }
}

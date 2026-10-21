package app.ominira.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Earcons from the design system, synthesised as soft sine tones so nothing clicks:
 * a rising pair for "speak now", a falling pair for "got it", a three-note "done" (523, 659,
 * 784 Hz) and the sonar range (330 to 880 Hz). Played on the media stream, so they are heard on a
 * phone set to silent.
 */
object Tones {
    private const val RATE = 22_050

    suspend fun play(hz: Double, ms: Int, volume: Float = 0.22f) = withContext(Dispatchers.Default) {
        val n = RATE * ms / 1000
        if (n <= 0) return@withContext
        val fade = min(n / 4, RATE / 100)
        val pcm = ShortArray(n) { i ->
            val envelope = when {
                fade == 0 -> 1f
                i < fade -> i / fade.toFloat()
                i > n - fade -> (n - i) / fade.toFloat()
                else -> 1f
            }
            (sin(2.0 * PI * hz * i / RATE) * Short.MAX_VALUE * volume * envelope).toInt().toShort()
        }
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(n * 2)
                .build()
        } catch (e: Exception) {
            return@withContext // a tone is a nicety; never fail over one
        }
        try {
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.write(pcm, 0, n)
                track.play()
                delay(ms + 40L)
            }
        } catch (e: IllegalStateException) {
            // As above.
        } finally {
            track.release()
        }
    }

    /** Rising: speak now. */
    suspend fun speakNow() {
        play(660.0, 90)
        play(880.0, 90)
    }

    /** Falling: got it. */
    suspend fun gotIt() {
        play(880.0, 70)
        play(660.0, 70)
    }

    /** Money sent, account open. */
    suspend fun done() {
        play(523.0, 110)
        play(659.0, 110)
        play(784.0, 160)
    }
}

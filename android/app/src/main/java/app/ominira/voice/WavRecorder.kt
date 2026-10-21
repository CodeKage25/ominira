package app.ominira.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object WavRecorder {

    sealed interface Result {
        data object Silence : Result
        data object Failed : Result
        class Audio(val wav: ByteArray) : Result
    }

    private const val RATE = 16_000
    private const val FRAME_MS = 30
    private const val FRAME = RATE * FRAME_MS / 1000

    // The caller holds RECORD_AUDIO.
    @SuppressLint("MissingPermission")
    suspend fun record(waitMs: Long = 6_000, maxMs: Long = 15_000, quietMs: Long = 1_200): Result =
        withContext(Dispatchers.IO) {
            val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) return@withContext Result.Failed
            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, max(minBuffer, RATE),
                )
            } catch (e: Exception) {
                return@withContext Result.Failed
            }
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@withContext Result.Failed
            }

            val frame = ShortArray(FRAME)
            val speech = ByteArrayOutputStream()
            val before = ArrayDeque<ByteArray>()
            var noiseFloor = 0.0
            var calibrated = 0
            var loudFrames = 0
            var speaking = false
            var waited = 0L
            var kept = 0L
            var quiet = 0L
            var broken = false
            var emptyReads = 0

            try {
                recorder.startRecording()
                while (isActive && kept < maxMs) {
                    val n = recorder.read(frame, 0, FRAME)
                    if (n < 0) { broken = true; break }
                    if (n == 0) {
                        if (++emptyReads > 50) { broken = true; break }
                        continue
                    }
                    emptyReads = 0
                    val ms = n * 1000L / RATE
                    var energy = 0.0
                    val bytes = ByteArray(n * 2)
                    for (i in 0 until n) {
                        val s = frame[i].toInt()
                        energy += s.toDouble() * s
                        bytes[2 * i] = (s and 0xff).toByte()
                        bytes[2 * i + 1] = ((s shr 8) and 0xff).toByte()
                    }
                    val level = sqrt(energy / n)
                    if (calibrated < 8) {
                        noiseFloor += level / 8
                        calibrated++
                    }
                    val loud = level > max(600.0, min(noiseFloor * 3.0, 3_000.0))
                    if (!speaking) {
                        waited += ms
                        before.addLast(bytes)
                        if (before.size > 16) before.removeFirst()
                        loudFrames = if (loud) loudFrames + 1 else 0
                        if (loudFrames >= 3) {
                            speaking = true
                            before.forEach { speech.write(it) }
                            before.clear()
                        } else if (waited > waitMs) {
                            break
                        }
                    } else {
                        speech.write(bytes)
                        kept += ms
                        quiet = if (loud) 0 else quiet + ms
                        if (quiet >= quietMs) break
                    }
                }
            } catch (e: IllegalStateException) {
                broken = true
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }

            when {
                speaking -> Result.Audio(wav(speech.toByteArray()))
                broken -> Result.Failed
                else -> Result.Silence
            }
        }

    private fun wav(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size)
        fun int32(v: Int) {
            out.write(v and 0xff); out.write((v shr 8) and 0xff); out.write((v shr 16) and 0xff); out.write((v shr 24) and 0xff)
        }
        fun int16(v: Int) {
            out.write(v and 0xff); out.write((v shr 8) and 0xff)
        }
        out.write("RIFF".toByteArray(Charsets.US_ASCII)); int32(36 + pcm.size)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII)); int32(16)
        int16(1) // PCM
        int16(1) // mono
        int32(RATE)
        int32(RATE * 2) // bytes a second
        int16(2) // bytes a frame
        int16(16) // bits a sample
        out.write("data".toByteArray(Charsets.US_ASCII)); int32(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }
}

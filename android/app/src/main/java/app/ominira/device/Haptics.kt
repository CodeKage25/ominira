package app.ominira.device

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(context: Context) {

    private val vibrator: Vibrator? = findVibrator(context.applicationContext)

    private fun findVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val usable: Vibrator?
        get() = vibrator?.takeIf { it.hasVibrator() }

    // Every beat is 80 ms: felt in the hand, barely heard.
    fun count() = beat(BEAT_MS, MEDIUM)

    // Zero is the landmark to count from: a strong double beat.
    fun zero() {
        val v = usable ?: return
        val timings = longArrayOf(0, BEAT_MS, 70, BEAT_MS)
        if (v.hasAmplitudeControl()) {
            v.vibrate(VibrationEffect.createWaveform(timings, intArrayOf(0, FULL, 0, FULL), -1))
        } else {
            v.vibrate(VibrationEffect.createWaveform(timings, -1))
        }
    }

    fun lock() = pattern(0, 110, 160, 110)

    fun done() = pattern(0, 80, 120, 80, 120, 260)

    fun stop() = pattern(0, 400, 200, 400)

    fun tick() = beat(35, VibrationEffect.DEFAULT_AMPLITUDE)

    private fun beat(ms: Long, amplitude: Int) {
        val v = usable ?: return
        val amp = if (amplitude == VibrationEffect.DEFAULT_AMPLITUDE || !v.hasAmplitudeControl()) VibrationEffect.DEFAULT_AMPLITUDE else amplitude
        v.vibrate(VibrationEffect.createOneShot(ms, amp))
    }

    private fun pattern(vararg timings: Long) {
        val v = usable ?: return
        v.vibrate(VibrationEffect.createWaveform(timings, -1))
    }

    private companion object {
        const val BEAT_MS = 80L
        const val MEDIUM = 120
        const val FULL = 255
    }
}

package app.ominira.device

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object Signals {

    // From the audio mode, so no phone-state permission is needed.
    fun inCall(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
    }

    fun screenReaderOn(context: Context): Boolean =
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true

    // Wired or USB only: a Bluetooth speaker would play the PIN count aloud.
    fun wiredEarphones(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        val ear = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in ear }
    }

    fun reduceMotion(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

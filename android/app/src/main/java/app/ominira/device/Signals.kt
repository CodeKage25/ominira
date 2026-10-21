package app.ominira.device

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** Small things the phone can tell us. None needs a runtime permission. */
object Signals {

    /**
     * A phone or VoIP call is in progress: one of the scam-pause signals (`call_active`).
     * Read from the audio mode, so no phone-state permission is needed.
     */
    fun inCall(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
    }

    /** TalkBack (or another touch-exploration reader) is on. */
    fun screenReaderOn(context: Context): Boolean =
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true

    /**
     * Wired or USB earphones are plugged in. Pulse PIN then counts with quiet ticks in the ears and
     * the phone stays still. Bluetooth is left out on purpose: the phone cannot tell earbuds from a
     * room speaker, and a count played on a speaker would give the PIN away.
     */
    fun wiredEarphones(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        val ear = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in ear }
    }

    /** "Remove animations" is on: the animator duration scale is 0. */
    fun reduceMotion(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

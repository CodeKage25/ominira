package app.ominira.core

import java.security.MessageDigest

// Name of a bundled speech clip. gateway/scripts/make_speech_clips.py makes the same key.
object ClipKey {
    fun of(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray(Charsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it) }
}

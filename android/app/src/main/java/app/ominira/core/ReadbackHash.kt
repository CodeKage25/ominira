package app.ominira.core

import java.security.MessageDigest

// Fake bank only (the app echoes the Gateway's hash); must match json.dumps.
object ReadbackHash {

    fun canonical(kind: DraftKind, name: String, bank: String, last4: String, amountKobo: Long?): String {
        // Keys already in sorted order.
        return "{" +
            "\"account_last4\":" + quote(last4) + "," +
            "\"amount_kobo\":" + (amountKobo?.toString() ?: "null") + "," +
            "\"bank\":" + quote(bank) + "," +
            "\"kind\":" + quote(kind.wire) + "," +
            "\"name\":" + quote(name) +
            "}"
    }

    fun of(kind: DraftKind, name: String, bank: String, last4: String, amountKobo: Long?): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(canonical(kind, name, bank, last4, amountKobo).toByteArray(Charsets.UTF_8))
        return "sha256:" + bytes.joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }
    }

    fun of(readback: Readback): String =
        of(readback.kind, readback.name, readback.bank, readback.last4, readback.amountKobo)

    private fun quote(s: String): String {
        val out = StringBuilder("\"")
        for (ch in s) {
            when {
                ch == '"' -> out.append("\\\"")
                ch == '\\' -> out.append("\\\\")
                ch == '\n' -> out.append("\\n")
                ch == '\r' -> out.append("\\r")
                ch == '\t' -> out.append("\\t")
                ch == '\b' -> out.append("\\b")
                ch == '\u000c' -> out.append("\\f")
                ch.code < 0x20 || ch.code > 0x7f -> out.append("\\u").append("%04x".format(ch.code))
                else -> out.append(ch)
            }
        }
        return out.append('"').toString()
    }
}

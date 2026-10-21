package app.ominira.core

/**
 * Turns the four Pulse PIN digits into the PIN block the Gateway forwards to Zenith.
 *
 * HACKATHON PLACEHOLDER, as docs/api.md defines it: "demo-" followed by each digit replaced by
 * 9 minus that digit, so PIN 2 0 7 5 travels as "demo-7924". The pilot replaces this with an
 * ISO 9564 PIN block encrypted on the device with Zenith's PIN key.
 *
 * Never log what goes in or what comes out.
 */
object PinBlock {
    private const val PREFIX = "demo-"

    fun seal(digits: List<Int>): String {
        require(digits.size == 4 && digits.all { it in 0..9 }) { "A Pulse PIN is four digits" }
        return PREFIX + digits.joinToString("") { (9 - it).toString() }
    }

    /** Only the fake bank opens a block; the real one never leaves Zenith. Null if malformed. */
    fun open(block: String): List<Int>? {
        if (!block.startsWith(PREFIX)) return null
        val body = block.removePrefix(PREFIX)
        if (body.length != 4 || !body.all { it in '0'..'9' }) return null
        return body.map { 9 - (it - '0') }
    }
}

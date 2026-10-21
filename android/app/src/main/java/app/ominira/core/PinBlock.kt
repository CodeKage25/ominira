package app.ominira.core

// Hackathon placeholder (see README). Never log the digits or the block.
object PinBlock {
    private const val PREFIX = "demo-"

    fun seal(digits: List<Int>): String {
        require(digits.size == 4 && digits.all { it in 0..9 }) { "A Pulse PIN is four digits" }
        return PREFIX + digits.joinToString("") { (9 - it).toString() }
    }

    // Fake bank only: a real PIN block is never opened outside Zenith.
    fun open(block: String): List<Int>? {
        if (!block.startsWith(PREFIX)) return null
        val body = block.removePrefix(PREFIX)
        if (body.length != 4 || !body.all { it in '0'..'9' }) return null
        return body.map { 9 - (it - '0') }
    }
}

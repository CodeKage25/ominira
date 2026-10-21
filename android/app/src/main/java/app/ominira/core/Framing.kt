package app.ominira.core

import kotlin.math.abs

// What the camera sees, as the customer would see it: 0..1 across and down the frame,
// mirrored for the front camera. size is the face's or card's width over the frame's.
data class Seen(val x: Float, val y: Float, val size: Float, val turnDeg: Float = 0f)

// One direction at a time, most important first: find it, distance, left and right, up and down, turn.
enum class Aim { Search, Dark, Closer, Farther, Left, Right, Up, Down, Straight, Hold }

object Framing {
    private const val OFF_CENTRE = 0.13f
    private const val TOO_DARK = 45

    // Directions name the phone: an off-centre face or card comes back by moving the phone towards it.
    fun aim(seen: Seen?, luma: Int, minSize: Float, maxSize: Float): Aim = when {
        luma < TOO_DARK -> Aim.Dark
        seen == null -> Aim.Search
        seen.size < minSize -> Aim.Closer
        seen.size > maxSize -> Aim.Farther
        seen.x < 0.5f - OFF_CENTRE -> Aim.Left
        seen.x > 0.5f + OFF_CENTRE -> Aim.Right
        seen.y < 0.5f - OFF_CENTRE -> Aim.Up
        seen.y > 0.5f + OFF_CENTRE -> Aim.Down
        abs(seen.turnDeg) > 15f -> Aim.Straight
        else -> Aim.Hold
    }

    fun face(seen: Seen?, luma: Int): Aim = aim(seen, luma, minSize = 0.28f, maxSize = 0.7f)

    fun card(seen: Seen?, luma: Int): Aim = aim(seen, luma, minSize = 0.5f, maxSize = 0.95f)

    // 0 lost, 1 right there: drives the sonar's pitch.
    fun closeness(seen: Seen?, minSize: Float, maxSize: Float): Float {
        if (seen == null) return 0f
        val centre = 1f - ((abs(seen.x - 0.5f) + abs(seen.y - 0.5f)) * 1.6f).coerceIn(0f, 1f)
        val fit = when {
            seen.size < minSize -> seen.size / minSize
            seen.size > maxSize -> maxSize / seen.size
            else -> 1f
        }
        return (centre * fit).coerceIn(0f, 1f)
    }
}

// Speaks a direction once it has held for a moment, again every repeatAfterMs so the customer is never
// left in silence, and locks once Hold has held long enough.
class Steady(private val sayAfterMs: Long = 900, private val lockAfterMs: Long = 1_200, private val repeatAfterMs: Long = 6_000) {
    private var current: Aim? = null
    private var since = 0L
    private var said: Aim? = null
    private var saidAt = 0L

    // The direction to say now, or null to stay quiet.
    fun feed(aim: Aim, nowMs: Long): Aim? {
        if (aim != current) {
            current = aim
            since = nowMs
            return null
        }
        if (nowMs - since < sayAfterMs) return null
        if (aim == said && nowMs - saidAt < repeatAfterMs) return null
        said = aim
        saidAt = nowMs
        return aim
    }

    fun locked(nowMs: Long): Boolean = current == Aim.Hold && nowMs - since >= lockAfterMs

    fun reset() {
        current = null
        said = null
    }
}

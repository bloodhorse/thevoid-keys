package com.dessalines.thumbkey.utils

import kotlin.math.atan2

// how a key's circle is cut into directions. angles are Thumb-Key's: degrees, 0 = down, 90 = right,
// 180 = up, 270 = left. an eight-way key gets 60° straights and 30° diagonals, and a diagonal that
// holds nothing on that key gives its half to each straight beside it (Unexpected Keyboard's
// nearest-defined-direction trick, one step wide). four- and two-way keys keep upstream's equal cuts.
object Slices {
    const val STRAIGHT_DEG = 60.0
    const val DIAGONAL_DEG = 90.0 - STRAIGHT_DEG

    // an arc runs from its start to the next arc's start, going up in angle and wrapping at 360
    class Arc(
        val dir: SwipeDirection,
        val start: Double,
    )

    class Place(
        val dir: SwipeDirection,
        // degrees to the nearer edge of the arc, and the direction on the other side of it
        val edgeDist: Double,
        val across: SwipeDirection,
    )

    private val ring =
        listOf(
            SwipeDirection.BOTTOM,
            SwipeDirection.BOTTOM_RIGHT,
            SwipeDirection.RIGHT,
            SwipeDirection.TOP_RIGHT,
            SwipeDirection.TOP,
            SwipeDirection.TOP_LEFT,
            SwipeDirection.LEFT,
            SwipeDirection.BOTTOM_LEFT,
        )

    fun centre(dir: SwipeDirection) = ring.indexOf(dir) * 45.0

    fun angleOf(
        dx: Float,
        dy: Float,
    ): Double = norm(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())))

    fun arcs(
        type: SwipeNWay,
        holds: (SwipeDirection) -> Boolean,
    ): List<Arc> =
        when (type) {
            SwipeNWay.EIGHT_WAY -> {
                eightWay(holds)
            }

            SwipeNWay.FOUR_WAY_CROSS -> {
                listOf(
                    Arc(SwipeDirection.BOTTOM, 315.0),
                    Arc(SwipeDirection.RIGHT, 45.0),
                    Arc(SwipeDirection.TOP, 135.0),
                    Arc(SwipeDirection.LEFT, 225.0),
                )
            }

            SwipeNWay.FOUR_WAY_DIAGONAL -> {
                listOf(
                    Arc(SwipeDirection.BOTTOM_RIGHT, 0.0),
                    Arc(SwipeDirection.TOP_RIGHT, 90.0),
                    Arc(SwipeDirection.TOP_LEFT, 180.0),
                    Arc(SwipeDirection.BOTTOM_LEFT, 270.0),
                )
            }

            SwipeNWay.TWO_WAY_HORIZONTAL -> {
                listOf(Arc(SwipeDirection.RIGHT, 0.0), Arc(SwipeDirection.LEFT, 180.0))
            }

            SwipeNWay.TWO_WAY_VERTICAL -> {
                listOf(Arc(SwipeDirection.BOTTOM, 270.0), Arc(SwipeDirection.TOP, 90.0))
            }
        }.sortedBy { it.start }

    private fun eightWay(holds: (SwipeDirection) -> Boolean): List<Arc> {
        val out = ArrayList<Arc>()
        for (i in ring.indices step 2) {
            val straight = ring[i]
            val before = ring[(i + 7) % 8]
            val after = ring[i + 1]
            val c = i * 45.0
            // a straight starts where the diagonal before it ends; an empty diagonal ends at its own centre
            out += Arc(straight, norm(c - if (holds(before)) STRAIGHT_DEG / 2 else 45.0))
            if (holds(after)) out += Arc(after, norm(c + STRAIGHT_DEG / 2))
        }
        return out
    }

    fun place(
        arcs: List<Arc>,
        angle: Double,
    ): Place {
        val a = norm(angle)
        var k = arcs.indexOfLast { it.start <= a }
        if (k < 0) k = arcs.size - 1
        val arc = arcs[k]
        val prev = arcs[(k + arcs.size - 1) % arcs.size]
        val next = arcs[(k + 1) % arcs.size]
        val fromStart = norm(a - arc.start)
        val toEnd = norm(next.start - a)
        return if (fromStart <= toEnd) Place(arc.dir, fromStart, prev.dir) else Place(arc.dir, toEnd, next.dir)
    }

    fun read(
        arcs: List<Arc>,
        angle: Double,
    ) = place(arcs, angle).dir

    private fun norm(a: Double) = ((a % 360) + 360) % 360
}

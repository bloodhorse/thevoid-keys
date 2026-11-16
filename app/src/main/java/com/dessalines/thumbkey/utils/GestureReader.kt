package com.dessalines.thumbkey.utils

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Wurstfinger's gesture reader (github.com/cl445/wurstfinger, MIT), ported from
// Runtime/Gesture/GesturePreprocessor.swift, GestureFeatureCalculations.swift and
// KeyGestureRecognizer.swift. Thumb-Key reads a flick off the straight line from touch-down to
// lift-off; this cleans the whole path and reads the direction at the point of furthest travel,
// so a thumb's arc and a sloppy release don't move it. Points are px relative to touch-down;
// Wurstfinger's pt are read as dp. Square keys, so no aspect-ratio step.
object GestureReader {
    const val JITTER_DP = 3.0
    const val MAX_JUMP_DP = 50.0
    const val DEFAULT_SMOOTHING_WINDOW = 5

    const val MIN_CIRCULARITY = 0.3
    const val MIN_ANGULAR_SPAN = PI * 1.5
    const val MIN_TURN_CONSISTENCY = 0.8
    const val MIN_ORIENTED_COMPACTNESS = 0.4
    private const val TURN_THRESHOLD = 0.15

    private const val PLAUSIBILITY_CEILING_FACTOR = 3.0
    private const val SUSTAINED_RUN_MINIMUM_LENGTH = 3
    private const val VELOCITY_TOLERANCE_FACTOR = 3.0
    private val MIN_DIRECTION_COSINE = cos(PI / 4)

    data class Pt(
        val x: Double,
        val y: Double,
    ) {
        fun dist(o: Pt) = hypot(o.x - x, o.y - y)
    }

    class Config(
        val jitter: Double,
        val maxJump: Double,
        val window: Int,
    ) {
        companion object {
            fun of(
                density: Float,
                window: Int = DEFAULT_SMOOTHING_WINDOW,
            ): Config {
                val w = window.coerceIn(3, 11)
                return Config(JITTER_DP * density, MAX_JUMP_DP * density, if (w % 2 == 0) w + 1 else w)
            }
        }
    }

    class Reading(
        val maxDisplacement: Double,
        // the furthest point from touch-down: the flick's direction
        val dx: Float,
        val dy: Float,
        val pathLength: Double,
        val angularSpan: Double,
        val circularity: Double,
        val turnConsistency: Double,
        val orientedCompactness: Double,
        val minSwipeLength: Double,
    ) {
        val isTap get() = maxDisplacement < minSwipeLength

        val isCircular
            get() =
                pathLength > minSwipeLength * 2 &&
                    circularity > MIN_CIRCULARITY &&
                    abs(angularSpan) > MIN_ANGULAR_SPAN &&
                    turnConsistency > MIN_TURN_CONSISTENCY &&
                    orientedCompactness > MIN_ORIENTED_COMPACTNESS

        // screen y runs down, so a positive span is clockwise as seen
        val isClockwise get() = angularSpan > 0
    }

    fun read(
        raw: List<Pt>,
        config: Config,
        minSwipeLength: Double,
    ): Reading {
        val finite = raw.filter { it.x.isFinite() && it.y.isFinite() }
        return features(preprocess(finite, config), minSwipeLength)
    }

    fun preprocess(
        points: List<Pt>,
        config: Config,
    ): List<Pt> {
        if (points.size < 2) return points
        return smooth(filterOutliers(filterJitter(points, config), config), config.window)
    }

    fun filterJitter(
        points: List<Pt>,
        config: Config,
    ): List<Pt> {
        if (points.size < 2) return points
        val out = arrayListOf(points[0])
        for (i in 1 until points.size) {
            if (points[i].dist(out.last()) >= config.jitter) out += points[i]
        }
        if (points.last().dist(out.last()) >= 0.1) out += points.last()
        return out
    }

    // a point is dropped only when it's far from the accepted path and from both raw neighbours,
    // unless it continues the movement the finger was already making (a fling faster than
    // maxJump per sample). Wurstfinger's long comments explain each clause; the logic is theirs.
    fun filterOutliers(
        points: List<Pt>,
        config: Config,
    ): List<Pt> {
        if (points.size < 2) return points
        val out = arrayListOf(points[0])
        var acceptedStep: Pt? = null
        for (i in 1 until points.size) {
            val last = out.last()
            val cur = points[i]
            val step = Pt(cur.x - last.x, cur.y - last.y)
            val toAccepted = cur.dist(last)
            val nearAccepted = toAccepted <= config.maxJump
            val nearPrev = cur.dist(points[i - 1]) <= config.maxJump
            val nearNext = i + 1 < points.size && cur.dist(points[i + 1]) <= config.maxJump
            val plausible =
                toAccepted <= config.maxJump * PLAUSIBILITY_CEILING_FACTOR ||
                    consistentRunLength(points, i, config) >= SUSTAINED_RUN_MINIMUM_LENGTH
            if (nearAccepted || ((nearPrev || nearNext) && plausible) ||
                continuesMotion(step, acceptedStep, points, i, config)
            ) {
                out += cur
                acceptedStep = step
            }
        }
        return out
    }

    private fun consistentRunLength(
        points: List<Pt>,
        i: Int,
        config: Config,
    ): Int {
        var length = 1
        var b = i
        while (b > 0 && points[b].dist(points[b - 1]) <= config.maxJump) {
            length++
            b--
        }
        var f = i
        while (f + 1 < points.size && points[f].dist(points[f + 1]) <= config.maxJump) {
            length++
            f++
        }
        return length
    }

    private fun continuesMotion(
        step: Pt,
        previous: Pt?,
        points: List<Pt>,
        i: Int,
        config: Config,
    ): Boolean {
        val following = if (i + 1 < points.size) Pt(points[i + 1].x - points[i].x, points[i + 1].y - points[i].y) else null
        val reference =
            when {
                previous == null -> following
                following == null -> previous
                hypot(previous.x, previous.y) >= hypot(following.x, following.y) -> previous
                else -> following
            } ?: return false
        val refLen = hypot(reference.x, reference.y)
        val stepLen = hypot(step.x, step.y)
        val budget = min(refLen, config.maxJump)
        if (budget <= 0 || stepLen > budget * VELOCITY_TOLERANCE_FACTOR) return false
        return reference.x * step.x + reference.y * step.y >= MIN_DIRECTION_COSINE * refLen * stepLen
    }

    fun smooth(
        points: List<Pt>,
        window: Int,
    ): List<Pt> {
        if (points.size < window) return points
        val c = savitzkyGolay(window)
        val half = window / 2
        return List(points.size) { i ->
            var sx = 0.0
            var sy = 0.0
            for (j in 0 until window) {
                val p = points[(i - half + j).coerceIn(0, points.size - 1)]
                sx += c[j] * p.x
                sy += c[j] * p.y
            }
            Pt(sx, sy)
        }
    }

    // quadratic fits; Wurstfinger has no table for 3 and falls back to a moving average, so do we
    private fun savitzkyGolay(window: Int): DoubleArray =
        when (window) {
            5 -> doubleArrayOf(-3.0, 12.0, 17.0, 12.0, -3.0).map { it / 35 }
            7 -> doubleArrayOf(-2.0, 3.0, 6.0, 7.0, 6.0, 3.0, -2.0).map { it / 21 }
            9 -> doubleArrayOf(-21.0, 14.0, 39.0, 54.0, 59.0, 54.0, 39.0, 14.0, -21.0).map { it / 231 }
            11 -> doubleArrayOf(-36.0, 9.0, 44.0, 69.0, 84.0, 89.0, 84.0, 69.0, 44.0, 9.0, -36.0).map { it / 429 }
            else -> List(window) { 1.0 / window }
        }.toDoubleArray()

    fun features(
        points: List<Pt>,
        minSwipeLength: Double,
    ): Reading {
        if (points.size < 2) return Reading(0.0, 0f, 0f, 0.0, 0.0, 0.0, 1.0, 0.0, minSwipeLength)
        val start = points[0]
        var maxDist = 0.0
        var maxPt = start
        for (p in points) {
            val d = start.dist(p)
            if (d > maxDist) {
                maxDist = d
                maxPt = p
            }
        }
        val c = centroid(points)
        val angle = atan2(maxPt.y - start.y, maxPt.x - start.x)
        return Reading(
            maxDisplacement = maxDist,
            dx = (maxPt.x - start.x).toFloat(),
            dy = (maxPt.y - start.y).toFloat(),
            pathLength = pathLength(points),
            angularSpan = angularSpan(points, c),
            circularity = circularity(points, c),
            turnConsistency = turnConsistency(points),
            orientedCompactness = orientedCompactness(points, angle),
            minSwipeLength = minSwipeLength,
        )
    }

    fun pathLength(points: List<Pt>): Double {
        var l = 0.0
        for (i in 1 until points.size) l += points[i - 1].dist(points[i])
        return l
    }

    fun centroid(points: List<Pt>): Pt =
        if (points.isEmpty()) {
            Pt(0.0, 0.0)
        } else {
            Pt(points.sumOf { it.x } / points.size, points.sumOf { it.y } / points.size)
        }

    private fun turn(
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
    ) = atan2(ax * by - ay * bx, ax * bx + ay * by)

    fun angularSpan(
        points: List<Pt>,
        c: Pt,
    ): Double {
        var total = 0.0
        for (i in 1 until points.size) {
            total += turn(points[i - 1].x - c.x, points[i - 1].y - c.y, points[i].x - c.x, points[i].y - c.y)
        }
        return total
    }

    fun circularity(
        points: List<Pt>,
        c: Pt,
    ): Double {
        if (points.isEmpty()) return 0.0
        val radii = points.map { it.dist(c) }
        val mean = radii.average()
        if (mean <= 0) return 0.0
        val sd = sqrt(radii.sumOf { (it - mean) * (it - mean) } / radii.size)
        return max(0.0, min(1.0, 1 - sd / mean))
    }

    fun turnConsistency(points: List<Pt>): Double {
        val pts = ArrayList<Pt>()
        for (p in points) if (pts.lastOrNull() != p) pts += p
        if (pts.size < 3) return 1.0
        var cw = 0
        var ccw = 0
        for (i in 1 until pts.size - 1) {
            val ax = pts[i].x - pts[i - 1].x
            val ay = pts[i].y - pts[i - 1].y
            val bx = pts[i + 1].x - pts[i].x
            val by = pts[i + 1].y - pts[i].y
            if (ax * ax + ay * ay == 0.0 || bx * bx + by * by == 0.0) continue
            val t = turn(ax, ay, bx, by)
            if (t > TURN_THRESHOLD) ccw++ else if (t < -TURN_THRESHOLD) cw++
        }
        val total = cw + ccw
        return if (total == 0) 1.0 else max(cw, ccw).toDouble() / total
    }

    fun orientedCompactness(
        points: List<Pt>,
        principalAngle: Double,
    ): Double {
        val start = points.firstOrNull() ?: return 0.0
        val ca = cos(principalAngle)
        val sa = sin(principalAngle)
        var minPara = 0.0
        var maxPara = 0.0
        var minPerp = 0.0
        var maxPerp = 0.0
        for (p in points) {
            val dx = p.x - start.x
            val dy = p.y - start.y
            val para = dx * ca + dy * sa
            val perp = -dx * sa + dy * ca
            minPara = min(minPara, para)
            maxPara = max(maxPara, para)
            minPerp = min(minPerp, perp)
            maxPerp = max(maxPerp, perp)
        }
        val length = maxPara - minPara
        return if (length > 0) (maxPerp - minPerp) / length else 0.0
    }
}

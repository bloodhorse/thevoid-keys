package com.dessalines.thumbkey.utils

import com.dessalines.thumbkey.utils.GestureReader.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// wurstfinger's pins (KeyGestureClassificationTests, GesturePreprocessorRobustnessTests,
// GestureCalculationsTests) at density 1 and min swipe 20, where px are its pt; then arcs at his
// phone's numbers, 2.625 density and 60 px.
class GestureReaderTest {
    private val wf = GestureReader.Config.of(1f)

    private fun read(
        points: List<Pt>,
        config: GestureReader.Config = wf,
        min: Double = 20.0,
    ) = GestureReader.read(points, config, min)

    // wurstfinger's sectors, atan2(y, x) with y down: 0 right, 90 down
    private fun sector(r: GestureReader.Reading): String {
        if (r.isTap) return "tap"
        var d = atan2(r.dy.toDouble(), r.dx.toDouble()) * 180 / PI
        if (d < 0) d += 360
        return when {
            d >= 337.5 || d < 22.5 -> "right"
            d < 67.5 -> "downRight"
            d < 112.5 -> "down"
            d < 157.5 -> "downLeft"
            d < 202.5 -> "left"
            d < 247.5 -> "upLeft"
            d < 292.5 -> "up"
            else -> "upRight"
        }
    }

    private fun p(
        x: Number,
        y: Number,
    ) = Pt(x.toDouble(), y.toDouble())

    private fun circle(
        radius: Double,
        steps: Int,
        turns: Double = 1.0,
        clockwise: Boolean = true,
    ): List<Pt> {
        // starts at the touch-down point on the rim, centre to its right
        val s = if (clockwise) 1 else -1
        return (0..steps).map { i ->
            val a = PI + s * 2 * PI * turns * i / steps
            p(radius + radius * cos(a), radius * sin(a))
        }
    }

    @Test fun emptyAndSinglePointAreTaps() {
        assertEquals("tap", sector(read(emptyList())))
        assertEquals("tap", sector(read(listOf(p(7, 7)))))
    }

    @Test fun repeatedIdenticalPointsAreATap() {
        assertEquals("tap", sector(read(List(80) { p(12, 34) })))
    }

    @Test fun nonFiniteAndHugeJumpsDoNotCrash() {
        read(listOf(p(0, 0), p(Double.NaN, 10), p(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY), p(20, Double.NaN)))
        read(listOf(p(0, 0), p(1_000_000, -1_000_000), p(-5_000_000, 3_000_000), p(0, 0)))
    }

    @Test fun fastSwipeWithADroppedFrameStaysASwipe() {
        assertEquals("right", sector(read(listOf(p(0, 0), p(10, 0), p(20, 0), p(80, 0), p(95, 0)))))
    }

    @Test fun flickFasterThanTheJumpThresholdStaysASwipe() {
        assertEquals("down", sector(read((0..4).map { p(0, it * 53) })))
        assertEquals("right", sector(read((0..4).map { p(it * 53, 0) })))
    }

    @Test fun longPathStaysClassifiable() {
        assertEquals("right", sector(read((0..500).map { p(it, 0) })))
    }

    @Test fun cleanUpSwipe() {
        assertEquals("up", sector(read((0..15).map { p(0, it * -4) })))
    }

    @Test fun tinyMoveIsATap() {
        assertEquals("tap", sector(read(listOf(p(0, 0), p(2, 1)))))
    }

    @Test fun circlesReadAsCircles() {
        val cw = read(circle(30.0, 40))
        assertTrue(cw.isCircular)
        assertTrue(cw.isClockwise)
        val ccw = read(circle(30.0, 40, clockwise = false))
        assertTrue(ccw.isCircular)
        assertFalse(ccw.isClockwise)
    }

    @Test fun aHalfCircleIsNotACircle() {
        assertFalse(read(circle(30.0, 40, turns = 0.5)).isCircular)
    }

    @Test fun aStraightFlickIsNotACircle() {
        assertFalse(read((0..20).map { p(it * 5, 0) }).isCircular)
    }

    @Test fun anOutAndBackIsNotACircle() {
        val out = (0..10).map { p(it * 8, 0) }
        assertFalse(read(out + out.reversed().drop(1)).isCircular)
    }

    @Test fun calculations() {
        assertEquals(30.0, GestureReader.pathLength(listOf(p(0, 0), p(10, 0), p(20, 0), p(30, 0))), 1e-9)
        assertEquals(5.0, GestureReader.pathLength(listOf(p(0, 0), p(3, 4))), 1e-9)
        assertEquals(Pt(15.0, 10.0), GestureReader.centroid(listOf(p(0, 0), p(30, 0), p(15, 30))))
        val ring = (0 until 36).map { p(50 * cos(it * PI / 18), 50 * sin(it * PI / 18)) }
        assertTrue(GestureReader.circularity(ring, p(0, 0)) > 0.95)
        assertTrue(abs(GestureReader.angularSpan(ring, p(0, 0))) > PI * 1.8)
        assertTrue(GestureReader.turnConsistency(ring) > 0.9)
        val zig = (0..10).map { p(it * 10, if (it % 2 == 0) 0 else 10) }
        assertTrue(GestureReader.turnConsistency(zig) < 0.7)
        val square = listOf(p(0, 0), p(10, 0), p(10, 10), p(0, 10), p(0, 0))
        assertTrue(GestureReader.orientedCompactness(square, 0.0) > 0.8)
        val line = listOf(p(0, 0), p(50, 1), p(100, 0))
        assertTrue(GestureReader.orientedCompactness(line, 0.0) < 0.2)
    }

    // his phone: 2.625 px per dp, 60 px to flick

    private val his = GestureReader.Config.of(2.625f)

    @Test fun anUpFlickThatHooksRightAtTheEndReadsUp() {
        // lift-off at (30,-70) is top-right on Thumb-Key's line; the furthest point is straight up
        val path = (0..9).map { p(0, -it * 10) } + listOf(p(10, -88), p(20, -80), p(30, -70))
        assertEquals("up", sector(read(path, his, 60.0)))
    }

    @Test fun aFlickThatSpringsBackShortOfTheLineStillReads() {
        // released at 40 px, under the 60 px line, after reaching 75
        val path = (0..15).map { p(0, -it * 5) } + (1..7).map { p(0, -75 + it * 5) }
        assertEquals("up", sector(read(path, his, 60.0)))
    }

    @Test fun aRightThumbArcReadsByItsFurthestPoint() {
        // up-left, then the thumb rolls on lift-off and the release lands in "up"
        val path = (0..10).map { p(-7 * it, -7.5 * it) } + listOf(p(-55, -85), p(-35, -90))
        assertEquals("upLeft", sector(read(path, his, 60.0)))
    }

    @Test fun anHonestTapWithAWobbleStaysATap() {
        val path = listOf(p(0, 0), p(4, 3), p(9, 5), p(14, 6), p(18, 4))
        assertEquals("tap", sector(read(path, his, 60.0)))
    }

    @Test fun aThumbCircleOnTheSpaceBarReads() {
        val r = read(circle(70.0, 30), his, 60.0)
        assertTrue(r.isCircular)
    }

    @Test fun smoothingWindowIsOddAndClamped() {
        assertEquals(5, GestureReader.Config.of(1f, 4).window)
        assertEquals(11, GestureReader.Config.of(1f, 99).window)
        assertEquals(3, GestureReader.Config.of(1f, -5).window)
    }
}

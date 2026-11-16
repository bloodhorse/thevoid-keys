package com.dessalines.thumbkey.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// his phone's numbers: 2.625 density, min_swipe_length 32 px; his layout, plain English MessagEase.
// angles are Thumb-Key's: 0 down, 90 right, 180 up, 270 left.
class WordPickTest {
    private val density = 2.625f
    private val min = 32.0
    private val model = CharModel(File("src/main/assets/charlm.bin").readBytes())

    // a, o and e as keyboards/ENMessagEase.kt has them (the layout itself won't load off Android)
    private val a = KeyItemC(center = KeyC("a"), swipeType = SwipeNWay.FOUR_WAY_DIAGONAL, bottomRight = KeyC("v"))
    private val o =
        KeyItemC(
            center = KeyC("o"),
            topLeft = KeyC("q"),
            top = KeyC("u"),
            topRight = KeyC("p"),
            right = KeyC("b"),
            bottomRight = KeyC("j"),
            bottom = KeyC("d"),
            bottomLeft = KeyC("g"),
            left = KeyC("c"),
        )
    private val e =
        KeyItemC(
            center = KeyC("e"),
            top = KeyC("w"),
            topRight = KeyC("'"),
            right = KeyC("z"),
            bottomRight = KeyC(":"),
            bottom = KeyC("."),
            bottomLeft = KeyC(","),
        )

    private fun arcs(k: KeyItemC) = Slices.arcs(k.swipeType) { k.getSwipe(it) != null }

    private fun typed(action: KeyAction) = (action as KeyAction.CommitText).text

    private fun pick(
        k: KeyItemC,
        angle: Double,
        context: String?,
        travelDp: Double = 40.0,
        m: WordPick.Model = model,
    ) = WordPick.decide(
        travel = travelDp * density,
        angle = angle,
        minSwipe = min,
        density = density,
        arcs = arcs(k),
        resolve = { d -> k.getSwipe(d)?.action ?: k.center.action },
        model = { m },
        context = { context },
    )

    private fun dir(
        k: KeyItemC,
        angle: Double,
    ) = Slices.read(arcs(k), angle)

    @Test
    fun eightWayKeysGetWideStraightsAndNarrowDiagonals() {
        // o holds all eight: right is 60..120, up-right 120..150
        assertEquals(SwipeDirection.BOTTOM_RIGHT, dir(o, 59.0))
        assertEquals(SwipeDirection.RIGHT, dir(o, 61.0))
        assertEquals(SwipeDirection.RIGHT, dir(o, 119.0))
        assertEquals(SwipeDirection.TOP_RIGHT, dir(o, 121.0))
        assertEquals(SwipeDirection.TOP_RIGHT, dir(o, 149.0))
        assertEquals(SwipeDirection.TOP, dir(o, 151.0))
        assertEquals(SwipeDirection.BOTTOM, dir(o, 329.0 + 2))
        assertEquals(SwipeDirection.BOTTOM_LEFT, dir(o, 329.0))
    }

    @Test
    fun theOldSlicesAndTheNewReadTheseAnglesDifferently() {
        // 25° and 64° were down-right under eight 45° slices; now they're straights
        assertEquals(SwipeDirection.BOTTOM_RIGHT, swipeDirection(dx(25.0), dy(25.0), 0))
        assertEquals(SwipeDirection.BOTTOM, dir(o, 25.0))
        assertEquals(SwipeDirection.BOTTOM_RIGHT, swipeDirection(dx(64.0), dy(64.0), 0))
        assertEquals(SwipeDirection.RIGHT, dir(o, 64.0))
    }

    private fun dx(deg: Double) = (100 * Math.sin(Math.toRadians(deg))).toFloat()

    private fun dy(deg: Double) = (100 * Math.cos(Math.toRadians(deg))).toFloat()

    @Test
    fun anEmptyDiagonalFoldsIntoItsStraights() {
        // e: up-left holds nothing, so up (w) runs to 225 and left (nothing) starts there
        assertEquals(SwipeDirection.TOP, dir(e, 224.0))
        assertEquals(SwipeDirection.LEFT, dir(e, 226.0))
        // up-right holds an apostrophe, so it keeps its 30°
        assertEquals(SwipeDirection.TOP_RIGHT, dir(e, 125.0))
    }

    @Test
    fun fourWayKeysStayFourWay() {
        assertEquals(SwipeDirection.BOTTOM_RIGHT, dir(a, 89.0))
        assertEquals(SwipeDirection.TOP_RIGHT, dir(a, 91.0))
        assertEquals(SwipeDirection.BOTTOM_RIGHT, dir(a, 1.0))
    }

    @Test
    fun theAngleCodeAgreesWithUpstream() {
        for (deg in listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)) {
            assertEquals(swipeDirection(dx(deg), dy(deg), 0), dir(o, Slices.angleOf(dx(deg), dy(deg))))
        }
    }

    @Test
    fun anEdgeFlickBetweenTwoLettersGoesWithTheWordBothWays() {
        // half a degree into p's side of the b|p edge on o
        assertEquals("p", typed(pick(o, 120.5, "").geo))
        assertEquals("p", typed(pick(o, 120.5, "jum").action))
        assertEquals("b", typed(pick(o, 120.5, "num").action))
        // half a degree into b's side
        assertEquals("b", typed(pick(o, 119.5, "num").action))
        assertEquals("p", typed(pick(o, 119.5, "jum").action))
    }

    @Test
    fun nearTheBandsRimGeometryMostlyKeepsIt() {
        // 9° into b's side: geometry weighs 0.95, so p needs to be 19 times likelier
        val r = pick(o, 111.0, "jum")
        assertEquals("b", typed(r.action))
        assertEquals(0.95, r.steps.single().w, 1e-9)
    }

    @Test
    fun vAgainstA() {
        // a flick toward v that went flat lands in a's empty up-right quadrant and types a
        assertEquals("a", typed(pick(a, 93.0, "").geo))
        assertEquals("v", typed(pick(a, 93.0, "ha").action))
        assertEquals("a", typed(pick(a, 93.0, "h").action))
    }

    @Test
    fun aShortFlickAgainstTheTap() {
        // his c case: a left flick on o 4 dp past the line (12.2 dp)
        val geo = pick(o, 270.0, "", travelDp = 16.0)
        assertEquals("c", typed(geo.geo))
        assertEquals("c", typed(pick(o, 270.0, "ba", travelDp = 16.0).action))
        assertEquals("o", typed(pick(o, 270.0, "sh", travelDp = 13.0).action))
        // a drag just under the line that the word says was a c
        assertEquals("o", typed(pick(o, 270.0, "", travelDp = 11.5).geo))
        assertEquals("c", typed(pick(o, 270.0, "ba", travelDp = 11.5).action))
    }

    @Test
    fun aClearFlickIsNeverMoved() {
        for (ctx in listOf("", "jum", "num", "q", "ba", "sh")) {
            for (angle in listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)) {
                val r = pick(o, angle, ctx, travelDp = 30.0)
                assertTrue(r.steps.isEmpty())
                assertEquals(r.geo, r.action)
            }
        }
        // a tap well short of the line is never asked either
        assertTrue(pick(o, 120.0, "jum", travelDp = 3.0).steps.isEmpty())
    }

    @Test
    fun aWordWithNoOpinionNeverMovesAnything() {
        val flat = WordPick.Model { _, _ -> 0.1 }
        var angle = 0.0
        while (angle < 360) {
            for (t in listOf(5.0, 8.0, 11.0, 12.0, 13.0, 16.0, 19.0)) {
                val r = pick(o, angle, "x", travelDp = t, m = flat)
                assertEquals("$angle $t", r.geo, r.action)
            }
            angle += 0.25
        }
    }

    @Test
    fun aPasswordFieldNeverAsksTheModel() {
        var asked = false
        val r =
            WordPick.decide(
                travel = 40.0 * density,
                angle = 120.5,
                minSwipe = min,
                density = density,
                arcs = arcs(o),
                resolve = { d -> o.getSwipe(d)?.action ?: o.center.action },
                model = {
                    asked = true
                    model
                },
                context = { null },
            )
        assertFalse(asked)
        assertTrue(r.steps.isEmpty())
        assertEquals("p", typed(r.action))
    }

    @Test
    fun symbolsNeverCompete() {
        // e: right is z, down-right a colon; the z|: edge sits at 60
        assertEquals(":", typed(pick(e, 59.0, "ja").action))
        assertTrue(pick(e, 59.0, "ja").steps.isEmpty())
        assertEquals("z", typed(pick(e, 61.0, "").action))
        // down (.) against down-left (,)
        assertTrue(pick(e, 331.0, "the").steps.isEmpty())
    }

    @Test
    fun contextIsTheWordSoFar() {
        assertEquals("", CharModel.contextOf("hello "))
        assertEquals("llo", CharModel.contextOf("hello"))
        assertEquals("he", CharModel.contextOf("say He"))
        assertEquals("t", CharModel.contextOf("don't"))
        assertEquals("", CharModel.contextOf(""))
    }
}

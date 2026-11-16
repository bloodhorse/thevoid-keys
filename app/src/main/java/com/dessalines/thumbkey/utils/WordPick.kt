package com.dessalines.thumbkey.utils

import kotlin.math.abs
import kotlin.math.pow

// a flick that lands near a line between two letters asks the word which one it was. two lines: the
// edge between two slices (an angle), and min_swipe_length between a flick and the tap (a distance).
// inside a band around either, each side's weight runs from 0.5 on the line to 1 at the band's rim
// for the side the thumb is on, and that weight times P(letter | the word so far) picks. on the line
// the word decides alone; near the rim geometry keeps it unless the word is that many times surer.
// outside both bands nothing is asked. only a letter competes with a letter: a symbol, space or edit
// action is never pushed toward or away from. the angle is settled first, then flick against tap,
// each as a pair, so a word that has no opinion never moves anything.
object WordPick {
    const val ANGLE_BAND_DEG = 10.0
    const val TRAVEL_BAND_DP = 8.0

    // how hard the word pulls: P(letter)^this. 1 is plain Bayes with geometry as the likelihood
    const val WORD_WEIGHT = 1.0

    fun interface Model {
        fun prob(
            context: String,
            c: Char,
        ): Double
    }

    class Step(
        // "angle" or "travel"
        val kind: String,
        val geo: KeyAction,
        val alt: KeyAction,
        // geometry's weight for its own side; the other side has 1 - this
        val w: Double,
        val pGeo: Double,
        val pAlt: Double,
        val pick: KeyAction,
    ) {
        val sGeo get() = w * pGeo.pow(WORD_WEIGHT)
        val sAlt get() = (1 - w) * pAlt.pow(WORD_WEIGHT)
    }

    class Result(
        val action: KeyAction,
        // what geometry alone would have typed
        val geo: KeyAction,
        val steps: List<Step>,
    )

    fun letterOf(action: KeyAction): Char? {
        val t = (action as? KeyAction.CommitText)?.text ?: return null
        return t.singleOrNull()?.takeIf { it.lowercaseChar() in 'a'..'z' }
    }

    /**
     * travel and minSwipe in px; angle in Thumb-Key's degrees; resolve maps a direction (null = tap)
     * to what the key types. context is asked only when a borderline pair of letters turns up, and
     * null from it (a password field, no connection) means geometry alone.
     */
    fun decide(
        travel: Double,
        angle: Double,
        minSwipe: Double,
        density: Float,
        arcs: List<Slices.Arc>,
        resolve: (SwipeDirection?) -> KeyAction,
        model: () -> Model?,
        context: () -> String?,
    ): Result {
        val place = Slices.place(arcs, angle)
        val isTap = travel < minSwipe
        val geo = resolve(if (isTap) null else place.dir)
        val band = TRAVEL_BAND_DP * density
        val offLine = travel - minSwipe
        // a tap well short of the line has no flick to compete with
        if (offLine <= -band) return Result(geo, geo, emptyList())

        val steps = ArrayList<Step>(2)
        var ctx: String? = null
        var m: Model? = null
        var asked = false

        fun ask(): Boolean {
            if (!asked) {
                asked = true
                ctx = context()
                m = if (ctx != null) model() else null
            }
            return m != null
        }

        fun duel(
            kind: String,
            mine: KeyAction,
            theirs: KeyAction,
            w: Double,
        ): KeyAction {
            val a = letterOf(mine) ?: return mine
            val b = letterOf(theirs) ?: return mine
            if (a.lowercaseChar() == b.lowercaseChar() || !ask()) return mine
            val pGeo = m!!.prob(ctx!!, a)
            val pAlt = m!!.prob(ctx!!, b)
            val pick = if ((1 - w) * pAlt.pow(WORD_WEIGHT) > w * pGeo.pow(WORD_WEIGHT)) theirs else mine
            steps += Step(kind, mine, theirs, w, pGeo, pAlt, pick)
            return pick
        }

        var flick = resolve(place.dir)
        if (place.edgeDist < ANGLE_BAND_DEG) {
            flick = duel("angle", flick, resolve(place.across), 0.5 + 0.5 * place.edgeDist / ANGLE_BAND_DEG)
        }
        val action =
            if (abs(offLine) < band) {
                val tap = resolve(null)
                val w = 0.5 + 0.5 * abs(offLine) / band
                if (isTap) duel("travel", tap, flick, w) else duel("travel", flick, tap, w)
            } else {
                flick
            }
        return Result(action, geo, steps)
    }
}

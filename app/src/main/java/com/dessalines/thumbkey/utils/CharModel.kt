package com.dessalines.thumbkey.utils

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import kotlin.math.pow

// P(next letter | the last three letters of the word so far), English, built by tools/charlm.py from
// Norvig's word counts; the file format is in that script. it never looks past the current word.
class CharModel(
    bytes: ByteArray,
) : WordPick.Model {
    private val tri: ByteArray
    private val ids: ShortArray
    private val rows: ByteArray

    init {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size > 4 && String(bytes, 0, 4) == "CLM1") { "not a charlm.bin" }
        b.position(4)
        tri = ByteArray(A * A * 26).also { b.get(it) }
        val n = b.int
        ids = ShortArray(n).also { b.asShortBuffer().get(it) }
        b.position(b.position() + 2 * n)
        rows = ByteArray(n * 26).also { b.get(it) }
    }

    override fun prob(
        context: String,
        c: Char,
    ): Double {
        val x = c.lowercaseChar() - 'a'
        if (x !in 0..25) return 0.0
        val s = IntArray(3)
        val tail = context.takeLast(3)
        for ((i, ch) in tail.withIndex()) s[3 - tail.length + i] = sym(ch)
        val id = s[0] * A * A + s[1] * A + s[2]
        val k = Arrays.binarySearch(ids, id.toShort())
        val q =
            if (id < 32768 && k >= 0) {
                rows[k * 26 + x]
            } else {
                tri[(s[1] * A + s[2]) * 26 + x]
            }.toInt() and 0xff
        return 2.0.pow(-q / 8.0)
    }

    companion object {
        private const val A = 27

        private fun sym(ch: Char): Int {
            val x = ch.lowercaseChar() - 'a'
            return if (x in 0..25) x + 1 else 0
        }

        @Volatile private var loaded: CharModel? = null

        fun get(ctx: Context): CharModel? =
            loaded ?: synchronized(this) {
                loaded ?: runCatching { CharModel(ctx.assets.open("charlm.bin").use { it.readBytes() }) }
                    .getOrNull()
                    .also { loaded = it }
            }

        // the letters of the word the cursor sits after, lowercased, at most three; "" at a word's start
        fun contextOf(before: CharSequence): String {
            var i = before.length
            while (i > 0 && before.length - i < 3 && before[i - 1].lowercaseChar() in 'a'..'z') i--
            return before.subSequence(i, before.length).toString().lowercase()
        }
    }
}

package com.dessalines.thumbkey.utils

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import java.io.File
import java.util.concurrent.Executors

// every touch on a key, its whole path and what the keyboard made of it, one json line each in
// files/flicks.jsonl. read on the mac by tools/flicks.py. password fields are never written.
object Flicklog {
    private const val MAX_BYTES = 8L * 1024 * 1024
    private val io = Executors.newSingleThreadExecutor()

    class Trail {
        var downAt = 0L
        var downX = 0f
        var downY = 0f
        val points = ArrayList<FloatArray>(64)
        var open = false
        var written = true
    }

    fun Modifier.watch(trail: Trail): Modifier =
        pointerInput(trail) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    when (event.type) {
                        PointerEventType.Press -> {
                            trail.downAt = change.uptimeMillis
                            trail.downX = change.position.x
                            trail.downY = change.position.y
                            trail.points.clear()
                            trail.points += floatArrayOf(0f, 0f, 0f)
                            trail.open = true
                            trail.written = false
                        }

                        PointerEventType.Move, PointerEventType.Release -> {
                            if (trail.open) {
                                trail.points +=
                                    floatArrayOf(
                                        (change.uptimeMillis - trail.downAt).toFloat(),
                                        change.position.x - trail.downX,
                                        change.position.y - trail.downY,
                                    )
                                if (event.type == PointerEventType.Release) trail.open = false
                            }
                        }

                        else -> {}
                    }
                }
            }
        }

    fun write(
        ctx: Context,
        trail: Trail,
        key: KeyItemC,
        action: KeyAction,
        path: String,
        density: Float,
        keyW: Float,
        keyH: Float,
        minSwipeLength: Int,
        password: Boolean,
    ) {
        if (password || trail.written || trail.points.isEmpty()) return
        trail.written = true
        val pts = trail.points.toList()
        val now = System.currentTimeMillis()
        val line =
            buildString {
                append("{\"t\":").append(now)
                append(",\"key\":").append(q(name(key.center.action)))
                append(",\"out\":").append(q(name(action)))
                append(",\"path\":").append(q(path))
                append(",\"dp\":").append(density)
                append(",\"kw\":").append(keyW)
                append(",\"kh\":").append(keyH)
                append(",\"min\":").append(minSwipeLength)
                append(",\"pts\":[")
                pts.forEachIndexed { i, p ->
                    if (i > 0) append(',')
                    append('[').append(p[0].toInt()).append(',')
                    append(Math.round(p[1] * 10) / 10f).append(',')
                    append(Math.round(p[2] * 10) / 10f).append(']')
                }
                append("]}\n")
            }
        val file = File(ctx.filesDir, "flicks.jsonl")
        io.execute {
            runCatching {
                if (file.length() > MAX_BYTES) file.renameTo(File(ctx.filesDir, "flicks.old.jsonl"))
                file.appendText(line)
            }
        }
    }

    private fun name(action: KeyAction): String =
        when (action) {
            is KeyAction.CommitText -> action.text
            is KeyAction.SendEvent -> KeyEvent.keyCodeToString(action.event.keyCode)
            else -> action::class.simpleName ?: "?"
        }

    private fun q(s: String): String {
        val b = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> b.append("\\\"")
                c == '\\' -> b.append("\\\\")
                c < ' ' -> b.append(String.format("\\u%04x", c.code))
                else -> b.append(c)
            }
        }
        return b.append('"').toString()
    }
}

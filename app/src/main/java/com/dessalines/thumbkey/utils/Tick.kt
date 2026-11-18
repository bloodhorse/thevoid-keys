package com.dessalines.thumbkey.utils

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// the light tick for anything that moves under the thumb (a slice crossed, a character eaten or put back, the
// cursor stepping). View.performHapticFeedback with TEXT_HANDLE_MOVE or SEGMENT_FREQUENT_TICK never reaches the
// motor on his Nothing Phone (1): dumpsys vibrator_manager only ever shows KEYBOARD_TAP from the keyboard. a
// prebaked TICK straight to the vibrator does play, the way thevoid's own ticks do. touch usage, so android's
// touch-haptics switch still rules it
object Tick {
    private var vibrator: Vibrator? = null
    private val effect by lazy {
        if (Build.VERSION.SDK_INT >= 29) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else {
            VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    fun play(c: Context) {
        val v =
            vibrator ?: (
                if (Build.VERSION.SDK_INT >= 31) {
                    c.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            )?.also { vibrator = it } ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            v.vibrate(effect)
        }
    }
}

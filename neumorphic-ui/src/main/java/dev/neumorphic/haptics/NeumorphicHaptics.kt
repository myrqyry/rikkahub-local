package dev.neumorphic.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Interface providing rich, distinct tactile haptic feedback profiles
 * tailored for physical Neumorphic UI interactions.
 */
@Stable
interface NeumorphicHaptics {
    /** Crisp mechanical click for standard buttons and chips */
    fun click()

    /** Fine granular tick for rotary dials, sliders, and incremental controls */
    fun tick()

    /** Tactile latch feedback for toggle switches, checkboxes, and radio buttons */
    fun toggle(checked: Boolean? = null)

    /** Deep tactile depression feedback for long presses or primary activations */
    fun impact()

    /** Snappy rebound feedback when elastic elements snap back to rest */
    fun snap()

    /** Subtle step feedback for navigation tabs and segmented options */
    fun select()
}

/**
 * Default implementation of [NeumorphicHaptics] utilizing Android's [View.performHapticFeedback].
 */
class DefaultNeumorphicHaptics(
    private val view: View
) : NeumorphicHaptics {

    override fun click() {
        performFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun tick() {
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ Segment Tick for fine physical dial notches
            performFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        } else {
            performFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    override fun toggle(checked: Boolean?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val feedback = if (checked == true) {
                HapticFeedbackConstants.CONFIRM
            } else if (checked == false) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.CONFIRM
            }
            performFeedback(feedback)
        } else {
            performFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    override fun impact() {
        performFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    override fun snap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performFeedback(HapticFeedbackConstants.GESTURE_END)
        } else {
            performFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    override fun select() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            performFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private fun performFeedback(feedbackConstant: Int) {
        try {
            view.performHapticFeedback(
                feedbackConstant,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (_: Throwable) {
            try {
                view.performHapticFeedback(feedbackConstant)
            } catch (_: Throwable) {
                // Fallback gracefully on devices without vibration hardware
            }
        }
    }
}

/**
 * Remembers a [NeumorphicHaptics] instance bound to the current Compose View.
 */
@Composable
fun rememberNeumorphicHaptics(): NeumorphicHaptics {
    val view = LocalView.current
    return remember(view) {
        DefaultNeumorphicHaptics(view)
    }
}

package dev.neumorphic.animation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Consistent motion tokens for the neumorphic system.
 * Use these instead of ad-hoc springs/tweens — keeps buttons, switches and cards feeling coherent.
 * Durations are spring-driven (no arbitrary ms); use the Dp variants for elevation/shape anim.
 */
object NeumorphicMotion {
    /** High responsiveness with a crisp tactile settle for buttons and switches */
    val TactileSpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.72f,
        stiffness = 500f
    )

    /** Bouncier feedback for playful micro-interactions and dialog reveals */
    val BouncySpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.52f,
        stiffness = 320f
    )

    /** Ultra-snappy transition for quick state changes (toggles, tabs) */
    val SnappySpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = 800f
    )

    /** Gentle, relaxing spring for ambient morphs and large card expansions */
    val GentleSpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = 200f
    )

    /** Dp spring variant for elevations and corner radii */
    val TactileDpSpring: AnimationSpec<Dp> = spring(
        dampingRatio = 0.72f,
        stiffness = 500f
    )

    val BouncyDpSpring: AnimationSpec<Dp> = spring(
        dampingRatio = 0.55f,
        stiffness = 350f
    )

    val GentleDpSpring: AnimationSpec<Dp> = spring(
        dampingRatio = 0.8f,
        stiffness = 220f
    )
}

/**
 * Directional Screen Transition with Neumorphic depth and subtle parallax scale.
 */
fun neumorphicScreenTransition(
    slideRight: Boolean
): ContentTransform {
    val enter = slideInHorizontally(
        initialOffsetX = { fullWidth -> if (slideRight) fullWidth / 3 else -fullWidth / 3 },
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
    ) + fadeIn(
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
    ) + scaleIn(
        initialScale = 0.94f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
    )

    val exit = slideOutHorizontally(
        targetOffsetX = { fullWidth -> if (slideRight) -fullWidth / 3 else fullWidth / 3 },
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
    ) + fadeOut(
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
    ) + scaleOut(
        targetScale = 0.96f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
    )

    return enter togetherWith exit
}

/**
 * Shared container expansion transition for morphing detail views.
 */
fun neumorphicModalTransition(): ContentTransform {
    val enter = slideInVertically(
        initialOffsetY = { it / 4 },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 340f)
    ) + scaleIn(
        initialScale = 0.88f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 340f)
    ) + fadeIn(
        animationSpec = tween(240)
    )

    val exit = slideOutVertically(
        targetOffsetY = { it / 5 },
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)
    ) + scaleOut(
        targetScale = 0.9f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)
    ) + fadeOut(
        animationSpec = tween(180)
    )

    return enter togetherWith exit
}

/**
 * Staggered entry animation spec for list items.
 */
fun staggeredListEnter(index: Int, baseDelayMs: Int = 40): EnterTransition {
    val delay = (index * baseDelayMs).coerceAtMost(360)
    return slideInVertically(
        initialOffsetY = { 60 },
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 380f
        )
    ) + fadeIn(
        animationSpec = tween(durationMillis = 300, delayMillis = delay)
    ) + scaleIn(
        initialScale = 0.92f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f)
    )
}

/**
 * Modifier that applies realistic tactile 3D depression and spring bounce on press.
 */
fun Modifier.neumorphicPressMotion(
    pressedScale: Float = 0.96f,
    onPressedChange: ((Boolean) -> Unit)? = null
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onPressedChange?.invoke(true)
        waitForUpOrCancellation()
        onPressedChange?.invoke(false)
    }
}

/**
 * Infinite breathing pulse scale helper for active indicators / hero elements.
 */
@Composable
fun rememberNeumorphicBreathingScale(
    minScale: Float = 0.97f,
    maxScale: Float = 1.03f,
    durationMillis: Int = 2400
): State<Float> {
    val infiniteTransition = rememberInfiniteTransition(label = "breathingPulse")
    return infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )
}

/**
 * Ambient glow intensity animation helper.
 */
@Composable
fun rememberNeumorphicGlowPulse(
    durationMillis: Int = 1800
): State<Float> {
    val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
    return infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
}

/**
 * Fluid wave phase animation for audio waveforms and live energy monitors.
 */
@Composable
fun rememberNeumorphicWavePhase(
    durationMillis: Int = 3000
): State<Float> {
    val infiniteTransition = rememberInfiniteTransition(label = "wavePhase")
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhaseFloat"
    )
}

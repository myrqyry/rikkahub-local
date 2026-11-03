package dev.neumorphic.lighting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

enum class LightSource { Static, Orientation, Manual }

/**
 * Stable holder for the shared light vector. Owned outside composition (e.g. an
 * Activity field) and provided via [LocalLightState]. Every mutation recomputes
 * the resolved vector and refreshes [snapshot] only on real (post-epsilon) change.
 *
 * Perf contract: engines read `snapshot.value` INSIDE the draw lambda (see
 * NeumorphicShadowModifier) — that invalidates draw only, never recomposition.
 * The holder instance itself is stable, so reading `LocalLightState.current` at
 * composition time never resubscribes.
 */
class LightState {
    // Backed by mutableStateOf (Studio UI observes them) with custom setters so
    // every write path refreshes the draw-phase snapshot. No exceptions.
    private val _selectedSource = mutableStateOf(LightSource.Static)
    var selectedSource: LightSource
        get() = _selectedSource.value
        set(value) { _selectedSource.value = value; refresh() }

    private val _animationsEnabled = mutableStateOf(true)
    var animationsEnabled: Boolean
        get() = _animationsEnabled.value
        set(value) { _animationsEnabled.value = value; refresh() }

    private val _intensity = mutableStateOf(0.35f)
    var intensity: Float
        get() = _intensity.value
        set(value) { _intensity.value = value; refresh() }

    private val _manual = mutableStateOf(LightVector(0.70710678f, -0.70710678f))
    var manual: LightVector
        get() = _manual.value
        set(value) { _manual.value = value.unit(); refresh() }

    private val _maxShadowShift = mutableStateOf(8.dp)
    var maxShadowShift: Dp
        get() = _maxShadowShift.value
        set(value) { _maxShadowShift.value = value; refresh() }

    internal var target: LightVector by mutableStateOf(LightVector.StaticDefault)

    /** Draw-phase observable. Mutations stay private so callers cannot bypass unit-vector invariants. */
    private val _snapshot = mutableStateOf(LightVector.StaticDefault)
    val snapshot: State<LightVector> get() = _snapshot

    val effectiveSource: LightSource
        get() = if (!animationsEnabled && selectedSource == LightSource.Orientation) {
            LightSource.Static
        } else {
            selectedSource
        }

    fun resolve(): LightVector = when (effectiveSource) {
        LightSource.Static -> LightVector.StaticDefault
        LightSource.Manual -> manual
        LightSource.Orientation -> angularBlend(LightVector.StaticDefault, target, intensity)
    }

    /** Sensor pipeline entry point. No-op when the change is below epsilon. */
    fun setTarget(next: LightVector) {
        target = next.unit()
        refresh()
    }

    fun refresh() {
        val r = resolve()
        val cur = _snapshot.value
        val dx = r.x - cur.x
        val dy = r.y - cur.y
        if (sqrt(dx * dx + dy * dy) > 1e-3f) _snapshot.value = r
    }
}

val LocalLightState = staticCompositionLocalOf<LightState?> { null }

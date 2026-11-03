package dev.neumorphic.lighting

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Orientation producer for the shared light vector. Activity-owned: call
 * start() in onResume, stop() in onPause. Game rotation vector preferred (no
 * magnetometer interference), rotation vector fallback, absent sensors -> stays
 * Static. Emits on [targets] only on real (post-epsilon) change; the Activity
 * collects it into [LightState.setTarget].
 */
class LightController(context: Context, private val state: LightState) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private var sensor: Sensor? = null

    private val _targets = MutableStateFlow(LightVector.StaticDefault)
    val targets: StateFlow<LightVector> = _targets.asStateFlow()

    private var smoothPitch = 0f
    private var smoothRoll = 0f
    private var lastTimestampNs = 0L
    private var lastEmitted = LightVector.StaticDefault

    // Sensor callbacks can arrive at game-rate frequency. Reuse all scratch arrays so
    // orientation tracking does not create several FloatArrays on every event.
    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    fun start() {
        val sm = sensorManager ?: return
        sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        lastTimestampNs = 0L
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        // Display-rotation compensation: remap so portrait/landscape share one sun.
        @Suppress("DEPRECATION") // minSdk 24 has no Context.display
        val rotation = windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        // Pass the sensor vector through at its native length: Android handles both
        // 3-component vectors (deriving w) and vectors that include w/heading accuracy.
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        when (rotation) {
            Surface.ROTATION_0 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, remappedMatrix)
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remappedMatrix)
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remappedMatrix)
            else -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remappedMatrix)
        }
        SensorManager.getOrientation(remappedMatrix, orientation) // azimuth, pitch, roll
        val pitch = orientation[1]
        val roll = orientation[2]

        // Timestamp-based exponential smoothing: identical feel at 30Hz vs 120Hz.
        val tau = 0.1 // seconds
        val alpha = if (lastTimestampNs == 0L) 1f else {
            val dt = ((event.timestamp - lastTimestampNs) / 1e9).toFloat().coerceIn(0f, 0.5f)
            (1f - exp(-dt / tau)).toFloat()
        }
        lastTimestampNs = event.timestamp
        smoothPitch += (pitch - smoothPitch) * alpha
        smoothRoll += (roll - smoothRoll) * alpha

        val next = tiltTarget(smoothPitch, smoothRoll)
        // Epsilon gate AFTER smoothing: float sensor noise must not emit.
        val dx = next.x - lastEmitted.x
        val dy = next.y - lastEmitted.y
        if (sqrt(dx * dx + dy * dy) < 1e-3f) return
        lastEmitted = next
        _targets.value = next
    }
}

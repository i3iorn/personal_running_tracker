package dev.schrammel.personal_running_tracker.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import dev.schrammel.personal_running_tracker.domain.sensor.SensorObservation
import dev.schrammel.personal_running_tracker.domain.sensor.StepDetector
import dev.schrammel.personal_running_tracker.domain.sensor.StrideEstimator

/**
 * Android SensorManager adapter that converts hardware SensorEvents into
 * domain SensorObservations and feeds the step detector and stride estimator.
 */
class SensorAdapter(
    private val context: Context,
    private val callback: Callback
) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val handler = Handler(Looper.getMainLooper())
    private val stepDetector = StepDetector()

    private var stepSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var rotationSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null

    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                val stepCount = event.values[0].toLong()
                callback.onStepCount(stepCount)
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val magnitude = Math.sqrt(
                event.values[0].toDouble() * event.values[0].toDouble() +
                event.values[1].toDouble() * event.values[1].toDouble() +
                event.values[2].toDouble() * event.values[2].toDouble()
            )
            callback.onAccelerometerMagnitude(magnitude)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            callback.onGyroscope(event.values[0], event.values[1], event.values[2])
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val rotationListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val azimuth = Math.toDegrees(Math.atan2(event.values[1].toDouble(), event.values[0].toDouble())).toFloat()
            callback.onHeading(azimuth)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val magnetometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Magnetometer used for compass heading; low priority accuracy.
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    interface Callback {
        fun onStepCount(totalSteps: Long)
        fun onAccelerometerMagnitude(magnitude: Double)
        fun onGyroscope(x: Float, y: Float, z: Float)
        fun onHeading(azimuthDegrees: Float)
    }

    fun start() {
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        stepSensor?.let { sensorManager.registerListener(stepListener, it, SensorManager.SENSOR_DELAY_NORMAL, handler) }
        accelSensor?.let { sensorManager.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_NORMAL, handler) }
        gyroSensor?.let { sensorManager.registerListener(gyroListener, it, SensorManager.SENSOR_DELAY_NORMAL, handler) }
        rotationSensor?.let { sensorManager.registerListener(rotationListener, it, SensorManager.SENSOR_DELAY_NORMAL, handler) }
        magnetometerSensor?.let { sensorManager.registerListener(magnetometerListener, it, SensorManager.SENSOR_DELAY_NORMAL, handler) }
    }

    fun stop() {
        sensorManager.unregisterListener(stepListener)
        sensorManager.unregisterListener(accelListener)
        sensorManager.unregisterListener(gyroListener)
        sensorManager.unregisterListener(rotationListener)
        sensorManager.unregisterListener(magnetometerListener)
    }
}

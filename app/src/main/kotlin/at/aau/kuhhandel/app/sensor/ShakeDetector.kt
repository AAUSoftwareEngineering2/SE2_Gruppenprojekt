package at.aau.kuhhandel.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(
    context: Context,
    private val onShakeTriggered: () -> Unit,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastTimestamp: Long = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    companion object {
        private const val FORCE_THRESHOLD = 13.0f // Force threshold to register a shake
    }

    fun startListening() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val currentTimestamp = System.currentTimeMillis()

        // Check forces every 100ms to allow movement history to build up
        if ((currentTimestamp - lastTimestamp) > 100) {
            val deltaX = event.values[0] - lastX
            val deltaY = event.values[1] - lastY
            val deltaZ = event.values[2] - lastZ

            lastTimestamp = currentTimestamp
            lastX = event.values[0]
            lastY = event.values[1]
            lastZ = event.values[2]

            // Combine all movement forces
            val physicalForce = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)

            if (physicalForce > FORCE_THRESHOLD) {
                onShakeTriggered()
            }
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) {}
}

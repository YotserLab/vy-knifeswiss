package com.example.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class SensorUtils(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun getGravityData(): Flow<Triple<Float, Float, Boolean>> = callbackFlow {
        val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) // DEV-R08, DEV-R13
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    // x and y axis
                    trySend(Triple(it.values[0], it.values[1], false))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        if (gravitySensor != null) {
            sensorManager.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            // Send fallback data if sensor is unavailable
            launch {
                while(true) {
                    val fakeX = (java.lang.Math.sin(System.currentTimeMillis() / 1000.0) * 2.0).toFloat()
                    val fakeY = (java.lang.Math.cos(System.currentTimeMillis() / 1200.0) * 1.5).toFloat()
                    trySend(Triple(fakeX, fakeY, true))
                    delay(100)
                }
            }
        }
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    fun getCompassData(): Flow<Pair<Float, Boolean>> = callbackFlow {
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, it.values)
                    val orientationAngles = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                    if (azimuth < 0) azimuth += 360f
                    trySend(Pair(azimuth, false))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            // Emulate compass azimuth for devices without a physical hardware compass
            launch {
                var currentAzimuth = 0f
                while(true) {
                    currentAzimuth += (java.lang.Math.random() * 4 - 2).toFloat()
                    if (currentAzimuth > 360f) currentAzimuth -= 360f
                    if (currentAzimuth < 0f) currentAzimuth += 360f
                    trySend(Pair(currentAzimuth, true))
                    delay(100)
                }
            }
        }
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}

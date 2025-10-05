package com.example.syona.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.syona.MainActivity
import com.example.syona.R
import com.example.syona.SyonaApp
import com.example.syona.data.model.StepSensorType

class StepCounterService : Service() {

    private var sensorManager: SensorManager? = null
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var isListenerRegistered = false
    private var activeSensorType: Int? = null

    private val stepSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    val total = event.values.firstOrNull() ?: return
                    (application as? SyonaApp)?.repository?.applyStepCounterReading(total)
                    updateNotification()
                }
                Sensor.TYPE_STEP_DETECTOR -> {
                    val count = event.values.size.takeIf { it > 0 } ?: 1
                    (application as? SyonaApp)?.repository?.incrementStepDetector(count)
                    updateNotification()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // no-op
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification(0, 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = (application as? SyonaApp)?.repository
        val prefs = repository?.observeStepCounter()?.value

        if (prefs != null && prefs.enabled) {
            registerStepListener(prefs.sensorType)
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    private fun registerStepListener(sensorType: StepSensorType) {
        val sensor = when (sensorType) {
            StepSensorType.AUTO -> stepCounterSensor ?: stepDetectorSensor
            StepSensorType.COUNTER -> stepCounterSensor ?: stepDetectorSensor
            StepSensorType.DETECTOR -> stepDetectorSensor ?: stepCounterSensor
        }

        if (sensor == null) return

        if (isListenerRegistered && activeSensorType == sensor.type) return

        unregisterStepListener()

        val registered = sensorManager?.registerListener(
            stepSensorListener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        ) ?: false

        if (registered) {
            isListenerRegistered = true
            activeSensorType = sensor.type
        }
    }

    private fun unregisterStepListener() {
        if (!isListenerRegistered) return

        sensorManager?.unregisterListener(stepSensorListener)
        isListenerRegistered = false
        activeSensorType = null
    }

    private fun updateNotification() {
        val repository = (application as? SyonaApp)?.repository
        val prefs = repository?.observeStepCounter()?.value

        if (prefs != null) {
            val notification = createNotification(prefs.stepsToday, prefs.dailyGoal)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Step Counter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks your steps in the background"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(steps: Int, goal: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (goal > 0) {
            "$steps / $goal steps today"
        } else {
            "$steps steps today"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Step Counter Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_trending_up)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterStepListener()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "step_counter_channel"

        fun start(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            context.stopService(intent)
        }
    }
}

package com.example.syona.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
        // A `health` foreground service requires ACTIVITY_RECOGNITION on Android 14+;
        // without it startForeground() throws SecurityException (e.g. after a reboot or
        // a sticky restart when the user has revoked the permission).
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // FOREGROUND_SERVICE_TYPE_HEALTH exists from API 34
                startForeground(NOTIFICATION_ID, createNotification(0, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, createNotification(0, 0))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not enter foreground; stopping step counter", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = (application as? SyonaApp)?.repository
        val prefs = repository?.observeStepCounter()?.value

        if (prefs != null && prefs.enabled && hasActivityRecognitionPermission(this)) {
            try {
                registerStepListener(prefs.sensorType)
                // Update notification immediately with current stats
                updateNotification()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register step sensor", e)
            }
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

        // Allow the sensor hub to batch events for up to MAX_REPORT_LATENCY_US. Each event
        // persists the whole app state, so batching cuts wake-ups and disk writes while
        // walking. Devices without a FIFO simply ignore the latency hint.
        val registered = sensorManager?.registerListener(
            stepSensorListener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            MAX_REPORT_LATENCY_US
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
        // Must differ from HydrationReminderWorker.NOTIFICATION_ID (1001), otherwise each step
        // update replaces the hydration reminder and vice versa.
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "StepCounterService"
        private const val MAX_REPORT_LATENCY_US = 10_000_000 // 10 s

        fun hasActivityRecognitionPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        private const val CHANNEL_ID = "step_counter_channel"

        fun start(context: Context) {
            if (!hasActivityRecognitionPermission(context)) return
            val intent = Intent(context, StepCounterService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (API 31+) when started from background
                Log.w(TAG, "Step counter service start not allowed", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            context.stopService(intent)
        }
    }
}

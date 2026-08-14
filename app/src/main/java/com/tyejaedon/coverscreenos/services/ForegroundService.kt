package com.tyejaedon.coverscreenos.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "foreground_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.tyejaedon.coverscreenos.action.START"
        const val ACTION_STOP = "com.tyejaedon.coverscreenos.action.STOP"

        // Creates an explicit intent used to start this foreground service.
        fun createStartIntent(context: Context): Intent {
            return Intent(context, ForegroundService::class.java).apply {
                action = ACTION_START
            }
        }

        // Creates an explicit intent used to stop this foreground service.
        fun createStopIntent(context: Context): Intent {
            return Intent(context, ForegroundService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    // Creates the Android O+ notification channel required for foreground notifications.
    private fun createNotificationChannel() {

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Used for maintaining persistent background services"
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    // Builds the persistent notification shown while the service is in foreground mode.
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cover Screen OS Running")
            .setContentText("Monitoring system state in the background...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // Initializes one-time service dependencies.
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    // Handles start/stop commands and keeps the service alive after process recreation.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }

        return START_STICKY
    }

    // Returns null because this is a started service, not a bound service.
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // Cleans up foreground state when the service is destroyed.
    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
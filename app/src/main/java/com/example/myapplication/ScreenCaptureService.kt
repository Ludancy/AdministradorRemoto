package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val notification: Notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = createNotificationChannel()
                notification = NotificationCompat.Builder(this, channelId)
                    .setContentTitle("Screen Capture")
                    .setContentText("Capturing screen...")
                    .setSmallIcon(R.drawable.ic_notification) // Ensure you have this icon in your project
                    .build()

                // Start the service as a foreground service with the MEDIA_PROJECTION type
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(1, notification)
                }
            } else {
                notification = NotificationCompat.Builder(this)
                    .setContentTitle("Screen Capture")
                    .setContentText("Capturing screen...")
                    .setSmallIcon(R.drawable.ic_notification) // Ensure you have this icon in your project
                    .build()
                startForeground(1, notification)
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "screen_capture"
            val channelName = "Screen Capture"
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            channel.description = "Channel for screen capture notification"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            return channelId
        }
        return ""
    }
}

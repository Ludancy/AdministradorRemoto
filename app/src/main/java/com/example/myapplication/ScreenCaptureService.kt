package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = createNotificationChannel()
            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Captura de Pantalla")
                .setContentText("Capturando pantalla...")
                .setSmallIcon(R.drawable.ic_notification) // Asegúrate de tener este ícono en tu proyecto
                .build()

            startForeground(1, notification)
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "screen_capture"
            val channelName = "Captura de Pantalla"
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            channel.description = "Canal para la notificación de captura de pantalla"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            return channelId
        }
        return ""
    }
}

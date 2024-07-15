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
            val notificacion: Notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val idCanal = crearCanalNotificacion()
                notificacion = NotificationCompat.Builder(this, idCanal)
                    .setContentTitle("Captura de Pantalla")
                    .setContentText("Capturando pantalla...")
                    .setSmallIcon(R.drawable.ic_notification) // Asegúrate de tener este icono en tu proyecto
                    .build()

                // Inicia el servicio como un servicio en primer plano con el tipo MEDIA_PROJECTION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1, notificacion, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(1, notificacion)
                }
            } else {
                notificacion = NotificationCompat.Builder(this)
                    .setContentTitle("Captura de Pantalla")
                    .setContentText("Capturando pantalla...")
                    .setSmallIcon(R.drawable.ic_notification) // Asegúrate de tener este icono en tu proyecto
                    .build()
                startForeground(1, notificacion)
            }
        }
        return START_NOT_STICKY
    }

    private fun crearCanalNotificacion(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val idCanal = "captura_pantalla"
            val nombreCanal = "Captura de Pantalla"
            val canal = NotificationChannel(idCanal, nombreCanal, NotificationManager.IMPORTANCE_LOW)
            canal.description = "Canal para la notificación de captura de pantalla"
            val administradorNotificaciones = getSystemService(NotificationManager::class.java)
            administradorNotificaciones.createNotificationChannel(canal)
            return idCanal
        }
        return ""
    }
}

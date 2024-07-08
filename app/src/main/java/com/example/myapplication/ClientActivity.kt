package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Socket

class ClientActivity : AppCompatActivity() {

    private val serverIp = "192.168.0.100" // Cambia esta IP a la IP del servidor
    private val port = 5000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client) // Crea este layout

        connectToServer()
    }

    private fun connectToServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket(serverIp, port)
                val inputStream = socket.getInputStream()
                val outputStream = socket.getOutputStream()

                // Enviar un mensaje al servidor
                outputStream.write("Hello, server!".toByteArray())

                // Leer la respuesta del servidor
                val buffer = ByteArray(1024)
                val bytesRead = inputStream.read(buffer)
                val receivedMessage = String(buffer, 0, bytesRead)
                Log.d("ClientActivity", "Received message: $receivedMessage")

                socket.close()
            } catch (e: IOException) {
                Log.e("ClientActivity", "Error connecting to server", e)
            }
        }
    }
}

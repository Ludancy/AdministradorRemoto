package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityServerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class ServerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerBinding
    private val serverPort = 5000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        displayServerInfo()
        startServer()
    }

    private fun displayServerInfo() {
        val ipAddress = getLocalIpAddress()
        if (ipAddress != null) {
            val serverInfo = "Server IP: $ipAddress\nPort: $serverPort"
            binding.serverInfoTextView.text = serverInfo
        } else {
            binding.serverInfoTextView.text = "Failed to get IP address"
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val inetAddress = addresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("ServerActivity", "Error getting IP address", ex)
        }
        return null
    }

    private fun startServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ServerSocket(serverPort).use { serverSocket ->
                    Log.d("ServerActivity", "Server started, waiting for connections...")
                    while (true) {
                        val clientSocket = serverSocket.accept()
                        handleClient(clientSocket)
                    }
                }
            } catch (e: IOException) {
                Log.e("ServerActivity", "Error starting server", e)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = socket.getInputStream()
                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                val byteArray = outputStream.toByteArray()
                val message = String(byteArray)

                runOnUiThread {
                    if (message == "HOOOOOOOOOLAAAAAAAAAAA") {
                        binding.serverInfoTextView.text = "Message from client: $message"
                    } else {
                        // Aquí puedes procesar otros tipos de datos recibidos desde el cliente
                    }
                }
            } catch (e: IOException) {
                Log.e("ServerActivity", "Error handling client", e)
            } finally {
                socket.close()
            }
        }
    }
}

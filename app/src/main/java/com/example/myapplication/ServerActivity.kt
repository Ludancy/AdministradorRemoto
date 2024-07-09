package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
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
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class ServerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerBinding
    private val serverPort = 5000
    private var serverSocket: ServerSocket? = null
    private lateinit var serverThread: Thread
    private lateinit var jmdns: JmDNS
    private val viewModel: Screen2ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            displayServerInfo()
            startServer()
            advertiseService()
        } else {
            // Restaurar el estado de la UI
            binding.serverInfoTextView.text = savedInstanceState.getString("serverInfo")
            val bitmap = savedInstanceState.getParcelable<Bitmap>("receivedImage")
            binding.receivedImageView.setImageBitmap(bitmap)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("serverInfo", binding.serverInfoTextView.text.toString())
        val bitmap = (binding.receivedImageView.drawable as BitmapDrawable?)?.bitmap
        if (bitmap != null) {
            outState.putParcelable("receivedImage", bitmap)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
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
        } catch (e: Exception) {
            Log.e("ServerActivity", "Failed to get local IP address", e)
        }
        return null
    }

    private fun startServer() {
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(serverPort)
                while (!serverThread.isInterrupted) {
                    val clientSocket = serverSocket?.accept()
                    handleClient(clientSocket)
                }
            } catch (e: IOException) {
                Log.e("ServerActivity", "Server error", e)
            }
        }
        serverThread.start()
    }

    private fun stopServer() {
        try {
            serverSocket?.close()
            serverThread.interrupt()
        } catch (e: IOException) {
            Log.e("ServerActivity", "Error closing server", e)
        }
    }

    private fun handleClient(socket: Socket?) {
        CoroutineScope(Dispatchers.IO).launch {
            socket?.use {
                try {
                    it.getInputStream().use { inputStream ->
                        // Leer la IP del cliente
                        val clientIpBuffer = ByteArray(1024)
                        val clientIpLength = inputStream.read(clientIpBuffer)
                        val clientIp = String(clientIpBuffer, 0, clientIpLength).trim()

                        // Leer los datos de la imagen
                        val byteArrayOutputStream = ByteArrayOutputStream()
                        val buffer = ByteArray(1024)
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            byteArrayOutputStream.write(buffer, 0, bytesRead)
                        }

                        val byteArray = byteArrayOutputStream.toByteArray()
                        val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

                        // Mostrar la imagen en la interfaz de usuario
                        if (bitmap != null) {
                            runOnUiThread {
                                binding.receivedImageView.setImageBitmap(bitmap)
                            }
                        }

                        // Actualizar el ViewModel con la IP del cliente
                        viewModel.clientData.postValue("Conectado a IP del cliente: $clientIp")
                    }
                } catch (e: IOException) {
                    Log.e("ServerActivity", "Client handling error", e)
                }
            }
        }
    }

    private fun advertiseService() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serviceType = "_http._tcp.local."
                val serviceName = "ScreenCaptureServer"
                val serviceInfo = ServiceInfo.create(serviceType, serviceName, serverPort, "Screen Capture Server")
                jmdns = JmDNS.create()
                jmdns.registerService(serviceInfo)
                Log.d("ServerActivity", "Service advertised: $serviceName")
            } catch (e: IOException) {
                Log.e("ServerActivity", "Failed to advertise service", e)
            }
        }
    }
}

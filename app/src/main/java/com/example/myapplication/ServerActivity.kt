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
            // Restore UI state
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
                    Log.d("ServerActivity", "Client connected: ${clientSocket?.inetAddress}")
                    CoroutineScope(Dispatchers.IO).launch {
                        handleClient(clientSocket)
                    }
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
        socket?.use {
            try {
                it.getInputStream().use { inputStream ->
                    // Read client IP
                    val clientIpBuffer = ByteArray(1024)
                    val clientIpLength = inputStream.read(clientIpBuffer)
                    val clientIp = String(clientIpBuffer, 0, clientIpLength).trim()

                    // Read image data
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        byteArrayOutputStream.write(buffer, 0, bytesRead)
                    }

                    val byteArray = byteArrayOutputStream.toByteArray()
                    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

                    // Display image on UI
                    if (bitmap != null) {
                        runOnUiThread {
                            binding.receivedImageView.setImageBitmap(bitmap)
                        }
                    }

                    // Update ViewModel with client IP
                    viewModel.clientData.postValue("Connected to client IP: $clientIp")
                }
            } catch (e: IOException) {
                Log.e("ServerActivity", "Client handling error", e)
            }
        }
    }

    private fun advertiseService() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                jmdns = JmDNS.create()
                val serviceInfo = ServiceInfo.create("_http._tcp.local.", "ScreenServer", serverPort, "Screen Sharing Server")
                jmdns.registerService(serviceInfo)
                Log.d("ServerActivity", "Service advertised: ScreenServer")
            } catch (e: IOException) {
                Log.e("ServerActivity", "Error advertising service", e)
            }
        }
    }
}

package com.example.myapplication

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityCloneBinding
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class CloneActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCloneBinding
    private val serverPort = 5000
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private lateinit var jmdns: JmDNS
    private val viewModel: Screen2ViewModel by viewModels()

    private val clientImages = mutableMapOf<String, Bitmap>()
    private val clientIps = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            displayServerInfo()
            CoroutineScope(Dispatchers.Main).launch {
                startServer()
            }
            advertiseService()
        } else {
            restoreImages(savedInstanceState)
        }

        binding.receivedImageView1.setOnClickListener {
            clientIps.getOrNull(0)?.let { clientIp ->
                openCloneActivity(clientIp)
            }
        }

        binding.receivedImageView2.setOnClickListener {
            clientIps.getOrNull(1)?.let { clientIp ->
                openCloneActivity(clientIp)
            }
        }
    }

    private fun restoreImages(savedInstanceState: Bundle) {
        savedInstanceState.getString("client1ImagePath")?.let { path ->
            openFileInput(path).use {
                val bitmap = BitmapFactory.decodeStream(it)
                binding.receivedImageView1.setImageBitmap(bitmap)
            }
        }
        savedInstanceState.getString("client2ImagePath")?.let { path ->
            openFileInput(path).use {
                val bitmap = BitmapFactory.decodeStream(it)
                binding.receivedImageView2.setImageBitmap(bitmap)
            }
        }
    }

    private fun openCloneActivity(clientIp: String) {
        stopServer()
        val intent = Intent(this, ActivityImageDetail::class.java)
        intent.putExtra("CLIENT_IP", clientIp)
        startActivity(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("serverInfo", binding.serverInfoTextView.text.toString())

        (binding.receivedImageView1.drawable as BitmapDrawable?)?.bitmap?.let { bitmap ->
            val filename = "client1Image.png"
            openFileOutput(filename, MODE_PRIVATE).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            outState.putString("client1ImagePath", filename)
        }

        (binding.receivedImageView2.drawable as BitmapDrawable?)?.bitmap?.let { bitmap ->
            val filename = "client2Image.png"
            openFileOutput(filename, MODE_PRIVATE).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            outState.putString("client2ImagePath", filename)
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
            Log.e("CloneActivity", "Failed to get local IP address", e)
        }
        return null
    }

    private suspend fun isPortInUse(port: Int): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val socket = Socket("localhost", port)
            socket.close()
            true
        } catch (e: IOException) {
            false
        }
    }

    private suspend fun startServer() {
        withContext(Dispatchers.IO) {
            stopServer()
        }

        if (isPortInUse(serverPort)) {
            connectToExistingServer()
            return
        }

        withContext(Dispatchers.IO) {
            serverThread = Thread {
                try {
                    serverSocket = ServerSocket(serverPort)
                    while (!serverThread!!.isInterrupted) {
                        val clientSocket = serverSocket?.accept()
                        CoroutineScope(Dispatchers.IO).launch {
                            handleClient(clientSocket)
                        }
                    }
                } catch (e: IOException) {
                    Log.e("CloneActivity", "Server error", e)
                }
            }
            serverThread?.start()
        }
    }

    private fun stopServer() {
        try {
            serverSocket?.close()
            serverThread?.interrupt()
            serverSocket = null
            serverThread = null
        } catch (e: IOException) {
            Log.e("CloneActivity", "Error closing server", e)
        }
    }

    private fun connectToExistingServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket("localhost", serverPort)
                handleClient(socket)
            } catch (e: IOException) {
                Log.e("CloneActivity", "Error connecting to existing server", e)
            }
        }
    }

    private fun handleClient(socket: Socket?) {
        socket?.use {
            try {
                it.getInputStream().use { inputStream ->
                    val clientIpBuffer = ByteArray(1024)
                    val clientIpLength = inputStream.read(clientIpBuffer)
                    val clientIp = String(clientIpBuffer, 0, clientIpLength).trim()

                    Log.d("CloneActivity", "Received connection from client IP: $clientIp")

                    val byteArrayOutputStream = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        byteArrayOutputStream.write(buffer, 0, bytesRead)
                    }

                    val byteArray = byteArrayOutputStream.toByteArray()
                    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

                    if (bitmap != null) {
                        clientImages[clientIp] = bitmap
                        if (!clientIps.contains(clientIp)) {
                            clientIps.add(clientIp)
                        }
                        runOnUiThread {
                            updateImageViews()
                        }
                    }

                    viewModel.clientData.postValue("Connected to client IP: $clientIp")
                }
            } catch (e: IOException) {
                Log.e("CloneActivity", "Client handling error", e)
            }
        }
    }

    private fun updateImageViews() {
        if (clientIps.size > 0) {
            binding.receivedImageView1.setImageBitmap(clientImages[clientIps[0]])
        }
        if (clientIps.size > 1) {
            binding.receivedImageView2.setImageBitmap(clientImages[clientIps[1]])
        }
    }

    private fun advertiseService() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                jmdns = JmDNS.create()
                val serviceInfo = ServiceInfo.create("_http._tcp.local.", "ScreenServer", serverPort, "Screen Sharing Server")
                jmdns.registerService(serviceInfo)
            } catch (e: IOException) {
                Log.e("CloneActivity", "Error advertising service", e)
            }
        }
    }
}

package com.example.myapplication

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityServerBinding
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
    private lateinit var serverThread: Thread
    private lateinit var jmdns: JmDNS
    private val viewModel: Screen2ViewModel by viewModels()

    private val clientImages = mutableMapOf<String, Bitmap>()

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
            Log.d("ServerActivity", "NUUUUUUUUUUUUUUUUUUUL SAVEINSTANCE NULL ENTRASTE")
        } else {
            Log.d("ServerActivity", "EEEEEEEEEEEEEEEEEEEEEEEELSEEEEEE SAVEINSTANCE  ELSE ENTRASTE")
            restoreImages(savedInstanceState)
        }

        binding.receivedImageView1.setOnClickListener {
            openCloneActivity()
        }

        binding.receivedImageView2.setOnClickListener {
            openCloneActivity()
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

    private fun openCloneActivity() {
        stopServer()
        val intent = Intent(this, ActivityImageDetail::class.java)
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
            Log.e("ServerActivity", "Failed to get local IP address", e)
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
            stopServer()  // Cierra el servidor existente si está corriendo
        }

        if (isPortInUse(serverPort)) {
            Log.d("ServerActivity", "El puerto $serverPort ya está en uso. Usando el servidor existente.")
            // Conectar al servidor existente si es necesario
            connectToExistingServer()
            return
        }

        withContext(Dispatchers.IO) {
            serverThread = Thread {
                try {
                    serverSocket = ServerSocket(serverPort)
                    Log.d("ServerActivity", "Servidor iniciado en el puerto: $serverPort")

                    while (!serverThread.isInterrupted) {
                        val clientSocket = serverSocket?.accept()
                        Log.d("ServerActivity", "Cliente conectado: ${clientSocket?.inetAddress}")
                        CoroutineScope(Dispatchers.IO).launch {
                            handleClient(clientSocket)
                        }
                    }
                } catch (e: IOException) {
                    Log.e("ServerActivity", "Error del servidor", e)
                }
            }
            serverThread.start()
        }
    }

    private fun stopServer() {
        try {
            serverSocket?.close()
            serverThread.interrupt()
        } catch (e: IOException) {
            Log.e("ServerActivity", "Error al cerrar el servidor", e)
        } catch (e: UninitializedPropertyAccessException) {
            Log.e("ServerActivity", "Server thread was not initialized", e)
        }
    }

    private fun connectToExistingServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket("localhost", serverPort)
                Log.d("ServerActivity", "Conectado al servidor existente en el puerto: $serverPort")
                handleClient(socket)
            } catch (e: IOException) {
                Log.e("ServerActivity", "Error al conectar al servidor existente", e)
            }
        }
    }

    private fun handleClient(socket: Socket?) {
        socket?.use {
            try {
                it.getInputStream().use { inputStream ->
                    val clientIpBuffer = ByteArray(1024)
                    Log.d("clientIpBuffer", "clientIpBuffer) $clientIpBuffer")
                    val clientIpLength = inputStream.read(clientIpBuffer)
                    Log.d("clientIpBuffer", "clientIpLength ) $clientIpLength")

                    if (clientIpLength > 0) {
                        val clientIp = String(clientIpBuffer, 0, clientIpLength).trim()
                        Log.d("ServerActivity", "Received connection from client IP: $clientIp")

                        val byteArrayOutputStream = ByteArrayOutputStream()
                        val buffer = ByteArray(1024)
                        var bytesRead: Int

                        while (true) {
                            bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1) break
                            byteArrayOutputStream.write(buffer, 0, bytesRead)
                        }

                        val byteArray = byteArrayOutputStream.toByteArray()
                        val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

                        if (bitmap != null) {
                            clientImages[clientIp] = bitmap
                            runOnUiThread {
                                updateImageViews()
                            }
                        }

                        viewModel.clientData.postValue("Connected to client IP: $clientIp")
                    } else {
                        Log.e("ServerActivity", "Failed to read client IP")
                    }
                }
            } catch (e: IOException) {
                Log.e("ServerActivity", "Client handling error", e)
            }
        }
    }

    private fun updateImageViews() {
        val clientIps = clientImages.keys.toList()
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
                Log.d("ServerActivity", "Service advertised: ScreenServer")
            } catch (e: IOException) {
                Log.e("ServerActivity", "Error advertising service", e)
            }
        }
    }
}

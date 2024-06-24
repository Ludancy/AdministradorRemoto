package com.example.myapplication

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityScreen2Binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer

class Screen2Activity : AppCompatActivity() {

    private lateinit var binding: ActivityScreen2Binding
    private var localIp: String? = null
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var imageReader: ImageReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreen2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Start the server to receive the screen stream
        startServer { bitmap ->
            runOnUiThread {
                binding.receivedImage.setImageBitmap(bitmap)
            }
        }

        binding.sendButton.setOnClickListener {
            val targetIp = "192.168.232.2"
            val targetPort = 8080
            startScreenCapture(targetIp, targetPort)
        }
    }

    private fun startScreenCapture(ip: String, port: Int) {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(captureIntent, 1000)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1000 && resultCode == RESULT_OK) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, null
            )

            val targetIp = "192.168.232.2"
            val targetPort = 8080
            CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    mediaProjection?.let {
                        val screenshot = captureScreen()
                        val byteArrayOutputStream = ByteArrayOutputStream()
                        screenshot.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
                        val byteArray = byteArrayOutputStream.toByteArray()

                        val status = sendBytesToIp(targetIp, targetPort, byteArray)
                        withContext(Dispatchers.Main) {
                            binding.connectionStatus.text = status
                        }
                    }
                    delay(100) // Delay to control the frame rate
                }
            }
        }
    }

    private fun captureScreen(): Bitmap {
        val image = imageReader.acquireLatestImage()
        if (image != null) {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val width = image.width
            val height = image.height
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            return bitmap
        } else {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    private suspend fun sendBytesToIp(ip: String, port: Int, byteArray: ByteArray): String {
        return withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                Log.d("NetworkNotification", "Intentando conectar a $ip:$port")
                socket = Socket(ip, port)
                val output: OutputStream = socket.getOutputStream()
                output.write(byteArray)
                output.flush()
                "Frame enviado correctamente a $ip"
            } catch (e: Exception) {
                Log.e("NetworkNotification", "Error al enviar el frame a $ip", e)
                "Error al enviar el frame a $ip: ${e.message}"
            } finally {
                socket?.close()
            }
        }
    }

    private fun startServer(onBitmapReceived: (Bitmap) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var serverSocket: ServerSocket? = null
            try {
                Log.d("NetworkNotification", "Servidor iniciado, esperando conexiones...")
                serverSocket = ServerSocket(8080)

                while (true) {
                    var clientSocket: Socket? = null
                    try {
                        clientSocket = serverSocket.accept()
                        Log.d("NetworkNotification", "Conexión aceptada de ${clientSocket.inetAddress.hostAddress}")
                        val input: InputStream = clientSocket.getInputStream()
                        val byteArray = input.readBytes()
                        val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                        withContext(Dispatchers.Main) {
                            onBitmapReceived(bitmap)
                            Log.d("NetworkNotification", "Frame recibido")
                        }
                    } catch (e: Exception) {
                        Log.e("NetworkNotification", "Error en el servidor", e)
                    } finally {
                        clientSocket?.close()
                    }
                }
            } catch (e: Exception) {
                Log.e("NetworkNotification", "Error en el servidor", e)
            } finally {
                serverSocket?.close()
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val inetAddresses = networkInterface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (ex: SocketException) {
            Log.e("NetworkUtils", "Error getting local IP address", ex)
        }
        return null
    }
}

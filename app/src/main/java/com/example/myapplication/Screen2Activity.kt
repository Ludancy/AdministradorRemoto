package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityScreen2Binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer

class Screen2Activity : AppCompatActivity() {

    private lateinit var binding: ActivityScreen2Binding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var imageReader: ImageReader
    private val viewModel: Screen2ViewModel by viewModels()

    companion object {
        private const val REQUEST_CODE_CAPTURE = 1001
        private const val TAG = "Screen2Activity"
        private const val SERVER_IP = "192.168.136.131"
        private const val SERVER_PORT = 5000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreen2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        binding.startServerButton.setOnClickListener {
            startServer()
        }

        binding.startClientButton.setOnClickListener {
            startClient()
        }

        binding.sendMessageButton.setOnClickListener {
            sendMessageToServer("HOOOOOOOOOLAAAAAAAAAAA")
        }

        // Recuperar datos del ViewModel
        viewModel.clientData.observe(this) { data ->
            // Actualizar la UI con los datos del cliente
            binding.clientDataTextView.text = data
        }
    }

    private fun startServer() {
        val intent = Intent(this, ServerActivity::class.java)
        startActivity(intent)
    }

    private fun startClient() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()

        // Inicia el servicio ScreenCaptureService como un servicio en primer plano
        val serviceIntent = Intent(this, ScreenCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        startActivityForResult(captureIntent, REQUEST_CODE_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CAPTURE && resultCode == RESULT_OK) {
            data?.let {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, it)
                setupVirtualDisplay()
            }
        }
    }

    private fun setupVirtualDisplay() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                mediaProjection?.let {
                    val screenshot = captureScreen()
                    if (screenshot != null) {
                        // Comprime el bitmap en un array de bytes
                        val byteArrayOutputStream = ByteArrayOutputStream()
                        screenshot.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
                        val byteArray = byteArrayOutputStream.toByteArray()

                        // Enviar datos al servidor
                        sendBytesToIp(SERVER_IP, SERVER_PORT, byteArray)

                        // Actualizar la interfaz de usuario con información del servidor
                        updateUiWithServerInfo(SERVER_IP, SERVER_PORT)
                    }
                }
                delay(100) // Ajusta el delay según sea necesario para controlar la frecuencia de envío
            }
        }
    }

    private fun captureScreen(): Bitmap? {
        val image = imageReader.acquireLatestImage() ?: return null
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
    }

    private fun sendBytesToIp(ip: String, port: Int, data: ByteArray) {
        try {
            Socket(ip, port).use { socket ->
                socket.getOutputStream().use { outputStream ->
                    // Obtener la IP local del cliente
                    val clientIp = getLocalIpAddress()
                    if (clientIp != null) {
                        // Enviar la IP del cliente primero
                        outputStream.write((clientIp + "\n").toByteArray())
                    }
                    // Enviar los datos de la imagen
                    outputStream.write(data)
                    outputStream.flush()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send bytes to IP $ip on port $port", e)
        }
    }

    @SuppressLint("ServiceCast")
    private fun getLocalIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }

    private fun sendMessageToServer(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Socket(SERVER_IP, SERVER_PORT).use { socket ->
                    socket.getOutputStream().use { outputStream ->
                        outputStream.write(message.toByteArray())
                        outputStream.flush()
                    }
                }
                // Solo actualizar la UI si el envío fue exitoso
                updateUiWithServerInfo(SERVER_IP, SERVER_PORT)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to send message to server", e)
                // Manejar el error si falla el envío
            }
        }
    }

    private fun updateUiWithServerInfo(ip: String, port: Int) {
        runOnUiThread {
            binding.serverInfoTextView.text = "Enviando a IP: $ip, Puerto: $port"
            Log.d(TAG, "Actualizando UI con información del servidor - IP: $ip, Puerto: $port")
        }
    }
}

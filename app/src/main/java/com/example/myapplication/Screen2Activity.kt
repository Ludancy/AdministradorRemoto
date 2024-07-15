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
        private const val SERVER_IP = "192.168.24.68"
        private const val SERVER_PORT = 5000
    }

    private lateinit var metrics: DisplayMetrics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreen2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        binding.startServerButton.setOnClickListener {
            startServer()
        }

        binding.startClientButton.setOnClickListener {
            startClient()
        }

        // Observe ViewModel data
        viewModel.clientData.observe(this) { data ->
            binding.clientDataTextView.text = data
        }
    }
    private fun startServer() {
        val intent = Intent(this, CloneActivity::class.java)
        openCloneActivity()
    }

    private fun startClient() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()

        // Start ScreenCaptureService as a foreground service
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
    private fun openCloneActivity() {
        val intent = Intent(this, CloneActivity::class.java)
        startActivity(intent)
    }
    private fun setupVirtualDisplay() {
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
                        // Compress bitmap into byte array
                        val byteArrayOutputStream = ByteArrayOutputStream()
                        screenshot.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
                        val byteArray = byteArrayOutputStream.toByteArray()

                        // Send data to server
                        sendBytesToIp(SERVER_IP, SERVER_PORT, byteArray)

                        // Update UI with server info
                        updateUiWithServerInfo(SERVER_IP, SERVER_PORT)
                    }
                }
                delay(100) // Adjust delay as needed to control sending frequency
            }
        }
    }

    private fun captureScreen(): Bitmap? {
        val image = imageReader.acquireLatestImage()
        if (image != null) {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * metrics.widthPixels

            val bitmap = Bitmap.createBitmap(
                metrics.widthPixels + rowPadding / pixelStride,
                metrics.heightPixels, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            return bitmap
        }
        return null
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

    private fun sendBytesToIp(ip: String, port: Int, data: ByteArray) {
        try {
            Socket(ip, port).use { socket ->
                socket.getOutputStream().use { outputStream ->
                    // Get client's local IP
                    val clientIp = getLocalIpAddress()
                    if (clientIp != null) {
                        // Send client's IP first
                        outputStream.write((clientIp + "\n").toByteArray())
                    }
                    // Send image data
                    outputStream.write(data)
                    outputStream.flush()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send bytes to IP $ip on port $port", e)
        }
    }

    private fun updateUiWithServerInfo(ip: String, port: Int) {
        runOnUiThread {
            binding.serverInfoTextView.text = "Sending data to server IP: $ip, Port: $port"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.stop()
        virtualDisplay?.release()
        imageReader.close()
    }
}

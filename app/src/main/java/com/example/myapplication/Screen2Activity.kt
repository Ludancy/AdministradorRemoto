package com.example.myapplication

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.example.myapplication.databinding.ActivityScreen2Binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer

class Screen2Activity : AppCompatActivity() {

    private lateinit var binding: ActivityScreen2Binding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var imageReader: ImageReader

    companion object {
        private const val REQUEST_CODE_CAPTURE = 1001
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreen2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        binding.sendButton.setOnClickListener {
            startScreenCapture()
        }
    }

    private fun startScreenCapture() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()

        // Start the ScreenCaptureService before starting the screen capture
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
                        updateScreen(screenshot)
                        val byteArrayOutputStream = ByteArrayOutputStream()
                        screenshot.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
                        val byteArray = byteArrayOutputStream.toByteArray()

                        sendBytesToIp("192.168.0.105", 8080, byteArray) // Replace with your IP and port
                    }
                }
                delay(100) // Adjust delay as needed to control frame rate
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

    private fun updateScreen(bitmap: Bitmap) {
        runOnUiThread {
            binding.receivedImage.setImageBitmap(bitmap)
        }
    }

    private fun sendBytesToIp(ip: String, port: Int, byteArray: ByteArray) {
        try {
            val socket = Socket(ip, port)
            val output: OutputStream = socket.getOutputStream()
            output.write(byteArray)
            output.flush()
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun testConnection() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket("192.168.0.105", 8080)
                socket.close()
                println("Connection successful")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        }
}

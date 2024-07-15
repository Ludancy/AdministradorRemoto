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

class Screen2Activity : AppCompatActivity() {

    private lateinit var binding: ActivityScreen2Binding
    private lateinit var administradorProyeccionMedia: MediaProjectionManager
    private var proyeccionMedia: MediaProjection? = null
    private var pantallaVirtual: VirtualDisplay? = null
    private lateinit var lectorImagen: ImageReader
    private val viewModel: Screen2ViewModel by viewModels()

    companion object {
        private const val CODIGO_SOLICITUD_CAPTURA = 1001
        private const val TAG = "ActividadPantalla2"
        private const val IP_SERVIDOR = "192.168.24.68"
        private const val PUERTO_SERVIDOR = 5000
    }

    private lateinit var metricas: DisplayMetrics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreen2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        administradorProyeccionMedia = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        metricas = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metricas)

        binding.botonIniciarServidor.setOnClickListener {
            iniciarServidor()
        }

        binding.botonIniciarCliente.setOnClickListener {
            iniciarCliente()
        }

        // Observar datos del ViewModel
        viewModel.datosCliente.observe(this) { datos ->
            binding.textoDatosCliente.text = datos
        }
    }

    private fun iniciarServidor() {
        val intent = Intent(this, CloneActivity::class.java)
        abrirActividadClon()
    }

    private fun iniciarCliente() {
        // Crear un intent para capturar la pantalla
        val intentCaptura = administradorProyeccionMedia.createScreenCaptureIntent()

        // Crear un intent para iniciar el servicio de captura de pantalla
        val intentServicio = Intent(this, ScreenCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Iniciar el servicio en primer plano si el SDK es O (Oreo) o superior
            startForegroundService(intentServicio)
        } else {
            // Iniciar el servicio en segundo plano si el SDK es menor que O
            startService(intentServicio)
        }

        // Iniciar la actividad para la captura de pantalla y esperar un resultado
        startActivityForResult(intentCaptura, CODIGO_SOLICITUD_CAPTURA)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CODIGO_SOLICITUD_CAPTURA && resultCode == RESULT_OK) {
            // Verificar que el resultado sea exitoso y que los datos no sean nulos
            data?.let {
                // Obtener la proyección de medios usando el resultado de la actividad
                proyeccionMedia = administradorProyeccionMedia.getMediaProjection(resultCode, it)
                // Configurar la pantalla virtual para la captura de pantalla
                configurarPantallaVirtual()
            }
        }
    }

    private fun abrirActividadClon() {
        val intent = Intent(this, CloneActivity::class.java)
        startActivity(intent)
    }

    private fun configurarPantallaVirtual() {
        // Crear un lector de imágenes para capturar la pantalla
        lectorImagen = ImageReader.newInstance(metricas.widthPixels, metricas.heightPixels, PixelFormat.RGBA_8888, 2)

        // Crear una pantalla virtual para la captura de pantalla
        pantallaVirtual = proyeccionMedia?.createVirtualDisplay(
            "CapturaPantalla",  // Nombre de la pantalla virtual
            metricas.widthPixels,  // Ancho de la pantalla
            metricas.heightPixels,  // Alto de la pantalla
            metricas.densityDpi,  // Densidad de la pantalla
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,  // Bandera para el modo espejo
            lectorImagen.surface,  // Superficie del lector de imágenes
            null,  // Callback (no se usa aquí)
            null   // Handler (no se usa aquí)
        )

        // Usar una corrutina para capturar y enviar la pantalla periódicamente
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                proyeccionMedia?.let {
                    // Capturar la pantalla y obtener un bitmap
                    val capturaPantalla = capturarPantalla()
                    if (capturaPantalla != null) {
                        // Comprimir el bitmap en un array de bytes
                        val outputStreamArrayBytes = ByteArrayOutputStream()
                        capturaPantalla.compress(Bitmap.CompressFormat.JPEG, 50, outputStreamArrayBytes)
                        val arrayBytes = outputStreamArrayBytes.toByteArray()

                        // Enviar los datos de imagen al servidor
                        enviarBytesAIP(IP_SERVIDOR, PUERTO_SERVIDOR, arrayBytes)

                        // Actualizar la interfaz de usuario con la información del servidor
                        actualizarUIConInfoServidor(IP_SERVIDOR, PUERTO_SERVIDOR)
                    }
                }
                // Esperar 100 ms antes de la próxima captura
                delay(100)
            }
        }
    }

    private fun capturarPantalla(): Bitmap? {
        // Adquirir la última imagen capturada por el lector de imágenes
        val imagen = lectorImagen.acquireLatestImage()
        if (imagen != null) {
            val planos = imagen.planes
            val buffer = planos[0].buffer
            val pixelStride = planos[0].pixelStride
            val rowStride = planos[0].rowStride
            val rowPadding = rowStride - pixelStride * metricas.widthPixels

            // Crear un bitmap a partir del buffer de la imagen
            val bitmap = Bitmap.createBitmap(
                metricas.widthPixels + rowPadding / pixelStride,  // Ancho ajustado
                metricas.heightPixels,  // Altura
                Bitmap.Config.ARGB_8888  // Configuración de pixel
            )
            bitmap.copyPixelsFromBuffer(buffer)  // Copiar los píxeles del buffer al bitmap
            imagen.close()  // Cerrar la imagen para liberar recursos
            return bitmap  // Devolver el bitmap creado
        }
        return null  // Devolver null si no se pudo adquirir la imagen
    }

    @SuppressLint("ServiceCast")
    private fun obtenerDireccionIpLocal(): String {
        // Obtener el servicio de WiFi del sistema
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        // Obtener la dirección IP del dispositivo
        val direccionIp = wifiManager.connectionInfo.ipAddress
        // Convertir la dirección IP a formato de string
        return String.format(
            "%d.%d.%d.%d",
            direccionIp and 0xff,
            direccionIp shr 8 and 0xff,
            direccionIp shr 16 and 0xff,
            direccionIp shr 24 and 0xff
        )
    }

    private fun enviarBytesAIP(ip: String, puerto: Int, datos: ByteArray) {
        try {
            // Crear un socket para conectarse al servidor
            Socket(ip, puerto).use { socket ->
                socket.getOutputStream().use { outputStream ->
                    // Obtener la IP local del cliente
                    val ipCliente = obtenerDireccionIpLocal()
                    if (ipCliente != null) {
                        // Enviar la IP del cliente primero
                        outputStream.write((ipCliente + "\n").toByteArray())
                    }
                    // Enviar los datos de la imagen
                    outputStream.write(datos)
                    outputStream.flush()  // Asegurar que todos los datos se envíen
                }
            }
        } catch (e: IOException) {
            // Registrar un error si la conexión falla
            Log.e(TAG, "No se pudo enviar bytes a IP $ip en el puerto $puerto", e)
        }
    }

    private fun actualizarUIConInfoServidor(ip: String, puerto: Int) {
        // Actualizar la interfaz de usuario con información sobre el servidor
        runOnUiThread {
            binding.textoInfoServidor.text = "Enviando datos al servidor IP: $ip, Puerto: $puerto"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        proyeccionMedia?.stop()
        pantallaVirtual?.release()
        lectorImagen.close()
    }
}

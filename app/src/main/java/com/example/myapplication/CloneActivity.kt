package com.example.myapplication

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
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
    private val puertoServidor = 5000
    private var socketServidor: ServerSocket? = null
    private var hiloServidor: Thread? = null
    private lateinit var jmdns: JmDNS
    private val viewModel: Screen2ViewModel by viewModels()

    private val imagenesClientes = mutableMapOf<String, Bitmap>()
    private val ipsClientes = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Si no hay un estado guardado previo, iniciar el servidor y anunciar el servicio
        if (savedInstanceState == null) {
            mostrarInfoServidor()
            CoroutineScope(Dispatchers.Main).launch {
                iniciarServidor()
            }
            anunciarServicio()
        } else {
            restaurarImagenes(savedInstanceState)
        }

        // Configurar acciones de clic para las imágenes recibidas
        binding.receivedImageView1.setOnClickListener {
            ipsClientes.getOrNull(0)?.let {
                val layoutParams = binding.receivedImageView1.layoutParams
                layoutParams.height = 1200
                layoutParams.width = 800
                binding.receivedImageView1.layoutParams = layoutParams
                binding.receivedImageView2.visibility = View.GONE

                val moverAnimacion = ObjectAnimator.ofFloat(
                    binding.receivedImageView1,
                    "translationY",
                    -0f
                )
                moverAnimacion.duration = 500
                moverAnimacion.interpolator = AccelerateInterpolator()
                moverAnimacion.start()
            }
        }

        binding.receivedImageView2.setOnClickListener {
            ipsClientes.getOrNull(0)?.let {
                val layoutParams = binding.receivedImageView2.layoutParams
                layoutParams.height = 1200
                layoutParams.width = 800
                binding.receivedImageView2.layoutParams = layoutParams
                binding.receivedImageView1.visibility = View.GONE

                val moverAnimacion = ObjectAnimator.ofFloat(
                    binding.receivedImageView2,
                    "translationY",
                    0f
                )
                moverAnimacion.duration = 500
                moverAnimacion.interpolator = AccelerateInterpolator()
                moverAnimacion.start()
            }
        }
    }

    private fun restaurarImagenes(savedInstanceState: Bundle) {
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
        detenerServidor()
    }

    private fun mostrarInfoServidor() {
        val direccionIp = obtenerDireccionIpLocal()
        if (direccionIp != null) {
            val infoServidor = "Server IP: $direccionIp\nPort: $puertoServidor"
            binding.serverInfoTextView.text = infoServidor
        } else {
            binding.serverInfoTextView.text = "Failed to get IP address"
        }
    }

    private fun obtenerDireccionIpLocal(): String? {
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

    private suspend fun isPuertoEnUso(puerto: Int): Boolean = withContext(Dispatchers.IO) {
        // Intenta conectarse al puerto especificado para verificar si está en uso
        return@withContext try {
            val socket = Socket("localhost", puerto)
            socket.close()  // Cierra el socket si la conexión fue exitosa
            true  // Devuelve true si el puerto está en uso
        } catch (e: IOException) {
            false  // Devuelve false si el puerto no está en uso
        }
    }

    private suspend fun iniciarServidor() {
        withContext(Dispatchers.IO) {
            detenerServidor()  // Detiene el servidor si está en funcionamiento
        }

        if (isPuertoEnUso(puertoServidor)) {
            conectarAServidorExistente()  // Conecta a un servidor existente si el puerto está en uso
            return
        }

        withContext(Dispatchers.IO) {
            // Inicia un hilo para manejar las conexiones del servidor
            hiloServidor = Thread {
                try {
                    // Crea un ServerSocket para escuchar conexiones en el puerto especificado
                    socketServidor = ServerSocket(puertoServidor)
                    while (!hiloServidor!!.isInterrupted) {
                        // Acepta una conexión de cliente
                        val socketCliente = socketServidor?.accept()
                        // Maneja la conexión del cliente en una nueva coroutine
                        CoroutineScope(Dispatchers.IO).launch {
                            manejarCliente(socketCliente)
                        }
                    }
                } catch (e: IOException) {
                    Log.e("CloneActivity", "Server error", e)  // Registra cualquier error del servidor
                }
            }
            hiloServidor?.start()  // Inicia el hilo del servidor
        }
    }

    private fun detenerServidor() {
        try {
            // Cierra el socket del servidor y detiene el hilo del servidor
            socketServidor?.close()
            hiloServidor?.interrupt()
            socketServidor = null
            hiloServidor = null
        } catch (e: IOException) {
            Log.e("CloneActivity", "Error closing server", e)  // Registra cualquier error al cerrar el servidor
        }
    }

    private fun conectarAServidorExistente() {
        // Intenta conectar a un servidor existente en una nueva coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket("localhost", puertoServidor)  // Crea un socket para conectarse al servidor existente
                manejarCliente(socket)  // Maneja la conexión del cliente
            } catch (e: IOException) {
                Log.e("CloneActivity", "Error connecting to existing server", e)  // Registra cualquier error al conectar al servidor existente
            }
        }
    }

    private fun manejarCliente(socket: Socket?) {
        // Usa el socket para manejar la conexión del cliente
        socket?.use {
            try {
                it.getInputStream().use { inputStream ->
                    // Lee la IP del cliente desde el flujo de entrada
                    val bufferIpCliente = ByteArray(1024)
                    val longitudIpCliente = inputStream.read(bufferIpCliente)
                    val ipCliente = String(bufferIpCliente, 0, longitudIpCliente).trim()

                    Log.d("CloneActivity", "Received connection from client IP: $ipCliente")  // Registra la IP del cliente conectado

                    // Lee los datos de imagen desde el flujo de entrada
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        byteArrayOutputStream.write(buffer, 0, bytesRead)
                    }

                    // Convierte los datos de imagen en un bitmap
                    val byteArray = byteArrayOutputStream.toByteArray()
                    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

                    // Si se pudo decodificar el bitmap, lo guarda y actualiza la interfaz de usuario
                    if (bitmap != null) {
                        imagenesClientes[ipCliente] = bitmap
                        if (!ipsClientes.contains(ipCliente)) {
                            ipsClientes.add(ipCliente)
                        }
                        runOnUiThread {
                            actualizarVistasImagenes()
                        }
                    }

                    // Actualiza el ViewModel con la IP del cliente conectado
                    viewModel.datosCliente.postValue("Connected to client IP: $ipCliente")
                }
            } catch (e: IOException) {
                Log.e("CloneActivity", "Client handling error", e)  // Registra cualquier error al manejar el cliente
            }
        }
    }
    private fun actualizarVistasImagenes() {
        if (ipsClientes.size > 0) {
            binding.receivedImageView1.setImageBitmap(imagenesClientes[ipsClientes[0]])
        }
        if (ipsClientes.size > 1) {
            binding.receivedImageView2.setImageBitmap(imagenesClientes[ipsClientes[1]])
        }
    }

    private fun anunciarServicio() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                jmdns = JmDNS.create()
                val serviceInfo = ServiceInfo.create("_http._tcp.local.", "ScreenServer", puertoServidor, "Screen Sharing Server")
                jmdns.registerService(serviceInfo)
            } catch (e: IOException) {
                Log.e("CloneActivity", "Error advertising service", e)
            }
        }
    }
}

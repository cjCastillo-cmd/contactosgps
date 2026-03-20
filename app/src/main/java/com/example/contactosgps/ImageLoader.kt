package com.example.contactosgps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.URL
import java.util.concurrent.Executors

/**
 * Carga imagenes desde archivos locales o URLs remotas con cache en memoria.
 * - Usa LruCache para no consumir demasiada RAM
 * - Redimensiona fotos grandes para evitar errores de memoria (OOM)
 * - Carga URLs remotas en hilos separados sin bloquear la UI
 */
object ImageLoader {

    // Cache que usa 1/8 de la memoria disponible de la app
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxMemory / 8) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(4)

    // Tamano maximo al redimensionar fotos locales
    private const val MAX_ANCHO = 400
    private const val MAX_ALTO = 400

    /**
     * Carga una imagen en un ImageView.
     * @param path ruta local o URL remota (null = mostrar placeholder)
     * @param imageView donde se mostrara la imagen
     * @param placeholder recurso drawable por defecto si no hay imagen
     */
    fun cargar(path: String?, imageView: ImageView, placeholder: Int) {
        // Sin ruta -> mostrar imagen por defecto
        if (path.isNullOrEmpty()) {
            imageView.setImageResource(placeholder)
            return
        }

        // Si ya esta en cache -> mostrar directo (rapido)
        cache.get(path)?.let {
            imageView.setImageBitmap(it)
            return
        }

        // Archivo local -> decodificar con redimensionamiento
        if (!path.startsWith("http")) {
            val file = File(path)
            if (file.exists()) {
                val bitmap = decodificarReducido(path)
                if (bitmap != null) {
                    cache.put(path, bitmap)
                    imageView.setImageBitmap(bitmap)
                    return
                }
            }
            imageView.setImageResource(placeholder)
            return
        }

        // URL remota -> descargar en hilo aparte
        imageView.setImageResource(placeholder)
        imageView.tag = path  // Para verificar que el ImageView no fue reciclado

        executor.execute {
            try {
                val connection = URL(path).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val bitmap = BitmapFactory.decodeStream(connection.getInputStream())

                if (bitmap != null) {
                    cache.put(path, bitmap)
                    mainHandler.post {
                        // Verificar que el ImageView sigue esperando ESTA imagen
                        if (imageView.tag == path) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (_: Exception) {
                // Si falla la descarga, mantener el placeholder
            }
        }
    }

    /**
     * Decodifica una imagen local reduciendo su tamano.
     * Primero lee solo las dimensiones (sin cargar en memoria),
     * calcula cuanto reducir, y luego carga la version reducida.
     */
    private fun decodificarReducido(path: String): Bitmap? {
        // Paso 1: leer solo dimensiones (no carga el bitmap completo)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)

        // Paso 2: calcular factor de reduccion y decodificar
        options.inSampleSize = calcularReduccion(options.outWidth, options.outHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    /**
     * Calcula el factor de reduccion (potencia de 2).
     * Ejemplo: imagen de 4000x3000 con target 400x400 -> inSampleSize = 4
     * (reduce a 1000x750, que es suficiente para una miniatura)
     */
    private fun calcularReduccion(ancho: Int, alto: Int): Int {
        var factor = 1
        while ((ancho / (factor * 2)) >= MAX_ANCHO && (alto / (factor * 2)) >= MAX_ALTO) {
            factor *= 2
        }
        return factor
    }
}
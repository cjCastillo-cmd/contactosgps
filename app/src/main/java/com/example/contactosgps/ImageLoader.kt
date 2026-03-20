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

object ImageLoader {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private val cache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(4)

    private const val TARGET_WIDTH = 400
    private const val TARGET_HEIGHT = 400

    fun cargar(path: String?, imageView: ImageView, placeholder: Int) {
        if (path.isNullOrEmpty()) {
            imageView.setImageResource(placeholder)
            return
        }

        // Verificar cache
        cache.get(path)?.let {
            imageView.setImageBitmap(it)
            return
        }

        // Es archivo local
        if (!path.startsWith("http")) {
            val file = File(path)
            if (file.exists()) {
                val bitmap = decodificarConMuestreo(path)
                if (bitmap != null) {
                    cache.put(path, bitmap)
                    imageView.setImageBitmap(bitmap)
                    return
                }
            }
            imageView.setImageResource(placeholder)
            return
        }

        // Es URL remota - cargar en hilo
        imageView.setImageResource(placeholder)
        imageView.tag = path

        executor.execute {
            try {
                val connection = URL(path).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val input = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(input)
                input.close()

                if (bitmap != null) {
                    cache.put(path, bitmap)
                    mainHandler.post {
                        if (imageView.tag == path) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (_: Exception) {
                // Mantener placeholder
            }
        }
    }

    private fun decodificarConMuestreo(path: String): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        options.inSampleSize = calcularInSampleSize(options, TARGET_WIDTH, TARGET_HEIGHT)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calcularInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
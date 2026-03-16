package com.example.contactosgps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object ImageLoader {

    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun cargar(path: String?, imageView: ImageView, placeholder: Int) {
        if (path.isNullOrEmpty()) {
            imageView.setImageResource(placeholder)
            return
        }

        // Verificar cache
        cache[path]?.let {
            imageView.setImageBitmap(it)
            return
        }

        // Es archivo local
        if (!path.startsWith("http")) {
            val file = File(path)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    cache[path] = bitmap
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

        Thread {
            try {
                val connection = URL(path).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val input = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(input)
                input.close()

                if (bitmap != null) {
                    cache[path] = bitmap
                    mainHandler.post {
                        if (imageView.tag == path) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (_: Exception) {
                // Mantener placeholder
            }
        }.start()
    }
}

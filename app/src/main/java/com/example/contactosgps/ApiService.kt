package com.example.contactosgps

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

object ApiService {

    // Cambiar a la IP de tu PC si pruebas en dispositivo físico
    private const val BASE_URL = "http://10.0.2.2" +
            "" +
            "/contactosgps/api.php"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(3)

    interface ApiCallback<T> {
        fun onSuccess(result: T)
        fun onError(message: String)
    }

    fun listar(callback: ApiCallback<List<Contacto>>) {
        ejecutarEnHilo {
            try {
                val url = URL("$BASE_URL?action=listar")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val response = leerRespuesta(conn)
                conn.disconnect()

                val json = JSONObject(response)
                if (json.getBoolean("success")) {
                    val contactos = parsearContactos(json.getJSONArray("data"))
                    mainHandler.post { callback.onSuccess(contactos) }
                } else {
                    mainHandler.post { callback.onError(json.getString("message")) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError("Error de conexión: ${e.message}") }
            }
        }
    }

    fun insertar(contacto: Contacto, fotoFile: File?, callback: ApiCallback<Int>) {
        ejecutarEnHilo {
            try {
                val boundary = UUID.randomUUID().toString()
                val url = URL("$BASE_URL?action=insertar")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                val outputStream = conn.outputStream
                val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)

                escribirCampo(writer, boundary, "nombre", contacto.nombre)
                escribirCampo(writer, boundary, "telefono", contacto.telefono)
                escribirCampo(writer, boundary, "latitud", contacto.latitud.toString())
                escribirCampo(writer, boundary, "longitud", contacto.longitud.toString())

                if (fotoFile != null && fotoFile.exists()) {
                    escribirArchivo(writer, outputStream, boundary, fotoFile)
                }

                writer.append("--$boundary--\r\n")
                writer.flush()
                writer.close()

                val response = leerRespuesta(conn)
                conn.disconnect()

                val json = JSONObject(response)
                if (json.getBoolean("success")) {
                    val id = json.getInt("id")
                    mainHandler.post { callback.onSuccess(id) }
                } else {
                    mainHandler.post { callback.onError(json.getString("message")) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError("Error de conexión: ${e.message}") }
            }
        }
    }

    fun actualizar(contacto: Contacto, fotoFile: File?, callback: ApiCallback<Boolean>) {
        ejecutarEnHilo {
            try {
                val boundary = UUID.randomUUID().toString()
                val url = URL("$BASE_URL?action=actualizar")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                val outputStream = conn.outputStream
                val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)

                escribirCampo(writer, boundary, "id", contacto.id.toString())
                escribirCampo(writer, boundary, "nombre", contacto.nombre)
                escribirCampo(writer, boundary, "telefono", contacto.telefono)
                escribirCampo(writer, boundary, "latitud", contacto.latitud.toString())
                escribirCampo(writer, boundary, "longitud", contacto.longitud.toString())

                if (fotoFile != null && fotoFile.exists()) {
                    escribirArchivo(writer, outputStream, boundary, fotoFile)
                }

                writer.append("--$boundary--\r\n")
                writer.flush()
                writer.close()

                val response = leerRespuesta(conn)
                conn.disconnect()

                val json = JSONObject(response)
                if (json.getBoolean("success")) {
                    mainHandler.post { callback.onSuccess(true) }
                } else {
                    mainHandler.post { callback.onError(json.getString("message")) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError("Error de conexión: ${e.message}") }
            }
        }
    }

    fun eliminar(id: Int, callback: ApiCallback<Boolean>) {
        ejecutarEnHilo {
            try {
                val url = URL("$BASE_URL?action=eliminar")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val postData = "id=$id"
                conn.outputStream.write(postData.toByteArray())
                conn.outputStream.flush()

                val response = leerRespuesta(conn)
                conn.disconnect()

                val json = JSONObject(response)
                if (json.getBoolean("success")) {
                    mainHandler.post { callback.onSuccess(true) }
                } else {
                    mainHandler.post { callback.onError(json.getString("message")) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError("Error de conexión: ${e.message}") }
            }
        }
    }

    // ==================== UTILIDADES ====================

    private fun ejecutarEnHilo(task: () -> Unit) {
        executor.execute(task)
    }

    private fun leerRespuesta(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream.bufferedReader().use { it.readText() }
    }

    private fun parsearContactos(array: JSONArray): List<Contacto> {
        val lista = mutableListOf<Contacto>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            lista.add(
                Contacto(
                    id = obj.getInt("id"),
                    nombre = obj.getString("nombre"),
                    telefono = obj.getString("telefono"),
                    latitud = obj.getDouble("latitud"),
                    longitud = obj.getDouble("longitud"),
                    fotoPath = if (obj.isNull("foto")) null else obj.getString("foto")
                )
            )
        }
        return lista
    }

    private fun escribirCampo(writer: PrintWriter, boundary: String, name: String, value: String) {
        writer.append("--$boundary\r\n")
        writer.append("Content-Disposition: form-data; name=\"$name\"\r\n")
        writer.append("\r\n")
        writer.append("$value\r\n")
        writer.flush()
    }

    private fun escribirArchivo(writer: PrintWriter, outputStream: OutputStream, boundary: String, file: File) {
        writer.append("--$boundary\r\n")
        writer.append("Content-Disposition: form-data; name=\"foto\"; filename=\"${file.name}\"\r\n")
        writer.append("Content-Type: image/jpeg\r\n")
        writer.append("\r\n")
        writer.flush()

        FileInputStream(file).use { input ->
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
        }

        writer.append("\r\n")
        writer.flush()
    }
}

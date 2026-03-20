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

/**
 * Servicio que maneja la comunicacion HTTP con la API REST de PHP.
 * Usa un pool de hilos para no bloquear la UI y devuelve resultados
 * al hilo principal mediante callbacks.
 */
object ApiService {

    // IP 10.0.2.2 = localhost de la PC desde el emulador Android
    // Si pruebas en celular fisico, cambiar por la IP real de tu PC (ej: 192.168.1.100)
    private const val BASE_URL = "http://10.0.2.2/contactosgps/api.php"


    // Handler para ejecutar callbacks en el hilo principal (UI)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Pool de 3 hilos para ejecutar peticiones HTTP en segundo plano
    private val executor = Executors.newFixedThreadPool(3)

    // Interfaz callback para recibir resultados o errores de forma asincrona
    interface ApiCallback<T> {
        fun onSuccess(result: T)
        fun onError(message: String)
    }

    // ==================== OPERACIONES CRUD ====================

    /** Obtiene todos los contactos del servidor (GET) */
    fun listar(callback: ApiCallback<List<Contacto>>) {
        ejecutarEnHilo {
            try {
                val conn = crearConexion("$BASE_URL?action=listar", "GET")
                val json = JSONObject(leerRespuesta(conn))
                conn.disconnect()

                if (json.getBoolean("success")) {
                    val contactos = parsearContactos(json.getJSONArray("data"))
                    mainHandler.post { callback.onSuccess(contactos) }
                } else {
                    mainHandler.post { callback.onError(json.getString("message")) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError("Error de conexion: ${e.message}") }
            }
        }
    }

    /** Crea un nuevo contacto con foto opcional (POST multipart) */
    fun insertar(contacto: Contacto, fotoFile: File?, callback: ApiCallback<Int>) {
        ejecutarEnHilo {
            try {
                val json = enviarConFoto("$BASE_URL?action=insertar", contacto, fotoFile)

                if (json.getBoolean("success")) {
                    mainHandler.post { callback.onSuccess(json.getInt("id")) }
                } else {
                    mainHandler.post { callback.onError(json.getString("message")) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError("Error de conexion: ${e.message}") }
            }
        }
    }

    /** Actualiza un contacto existente con foto opcional (POST multipart) */
    fun actualizar(contacto: Contacto, fotoFile: File?, callback: ApiCallback<Boolean>) {
        ejecutarEnHilo {
            try {
                val json = enviarConFoto("$BASE_URL?action=actualizar", contacto, fotoFile)

                if (json.getBoolean("success")) {
                    mainHandler.post { callback.onSuccess(true) }
                } else {
                    mainHandler.post { callback.onError(json.getString("message")) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError("Error de conexion: ${e.message}") }
            }
        }
    }

    /** Elimina un contacto por ID (POST simple) */
    fun eliminar(id: Int, callback: ApiCallback<Boolean>) {
        ejecutarEnHilo {
            try {
                val conn = crearConexion("$BASE_URL?action=eliminar", "POST")
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                conn.outputStream.write("id=$id".toByteArray())
                conn.outputStream.flush()

                val json = JSONObject(leerRespuesta(conn))
                conn.disconnect()

                if (json.getBoolean("success")) {
                    mainHandler.post { callback.onSuccess(true) }
                } else {
                    mainHandler.post { callback.onError(json.getString("message")) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError("Error de conexion: ${e.message}") }
            }
        }
    }

    // ==================== METODOS AUXILIARES ====================

    /** Crea y configura una conexion HTTP con timeouts */
    private fun crearConexion(urlStr: String, metodo: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = metodo
        conn.connectTimeout = 10000  // 10 segundos maximo para conectar
        conn.readTimeout = 10000     // 10 segundos maximo para leer respuesta
        return conn
    }

    /** Envia un contacto con foto usando formato multipart/form-data */
    private fun enviarConFoto(urlStr: String, contacto: Contacto, fotoFile: File?): JSONObject {
        // Boundary = separador unico entre campos del formulario
        val boundary = UUID.randomUUID().toString()
        val conn = crearConexion(urlStr, "POST")
        conn.doOutput = true
        conn.readTimeout = 15000
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        val outputStream = conn.outputStream
        val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)

        // Enviar cada campo del formulario
        if (contacto.id > 0) escribirCampo(writer, boundary, "id", contacto.id.toString())
        escribirCampo(writer, boundary, "nombre", contacto.nombre)
        escribirCampo(writer, boundary, "telefono", contacto.telefono)
        escribirCampo(writer, boundary, "latitud", contacto.latitud.toString())
        escribirCampo(writer, boundary, "longitud", contacto.longitud.toString())

        // Enviar archivo de foto si existe
        if (fotoFile != null && fotoFile.exists()) {
            escribirArchivo(writer, outputStream, boundary, fotoFile)
        }

        // Cerrar el formulario multipart
        writer.append("--$boundary--\r\n")
        writer.flush()
        writer.close()

        val response = leerRespuesta(conn)
        conn.disconnect()
        return JSONObject(response)
    }

    /** Ejecuta una tarea en un hilo del pool (no bloquea la UI) */
    private fun ejecutarEnHilo(task: () -> Unit) {
        executor.execute(task)
    }

    /** Lee la respuesta HTTP como texto (maneja codigos de error) */
    private fun leerRespuesta(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream.bufferedReader().use { it.readText() }
    }

    /** Convierte un JSONArray de la API en una lista de objetos Contacto */
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

    /** Escribe un campo de texto en el formulario multipart */
    private fun escribirCampo(writer: PrintWriter, boundary: String, name: String, value: String) {
        writer.append("--$boundary\r\n")
        writer.append("Content-Disposition: form-data; name=\"$name\"\r\n")
        writer.append("\r\n")
        writer.append("$value\r\n")
        writer.flush()
    }

    /** Escribe un archivo (foto) en el formulario multipart */
    private fun escribirArchivo(writer: PrintWriter, outputStream: OutputStream, boundary: String, file: File) {
        writer.append("--$boundary\r\n")
        writer.append("Content-Disposition: form-data; name=\"foto\"; filename=\"${file.name}\"\r\n")
        writer.append("Content-Type: image/jpeg\r\n")
        writer.append("\r\n")
        writer.flush()

        // Leer el archivo y enviarlo en bloques de 4KB
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
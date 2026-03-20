package com.example.contactosgps

/**
 * Modelo de datos que representa un contacto.
 * Se usa tanto para enviar datos a la API como para mostrarlos en la UI.
 */
data class Contacto(
    val id: Int = 0,             // ID en la base de datos (0 = nuevo)
    val nombre: String = "",
    val telefono: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val fotoPath: String? = null // Ruta local o URL remota de la foto
)
package com.example.contactosgps

data class Contacto(
    val id: Int = 0,
    val nombre: String = "",
    val telefono: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val fotoPath: String? = null
)

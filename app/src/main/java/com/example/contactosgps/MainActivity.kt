package com.example.contactosgps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import de.hdodenhof.circleimageview.CircleImageView
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pantalla principal: formulario para CREAR o EDITAR un contacto.
 * - Toma foto con la camara del dispositivo
 * - Obtiene ubicacion GPS en tiempo real
 * - Envia los datos al servidor via ApiService
 */
class MainActivity : AppCompatActivity() {

    // ==================== VISTAS ====================
    private lateinit var toolbar: MaterialToolbar
    private lateinit var ivFoto: CircleImageView
    private lateinit var fabCamera: FloatingActionButton
    private lateinit var chipGps: Chip
    private lateinit var etNombre: TextInputEditText
    private lateinit var etTelefono: TextInputEditText
    private lateinit var etLatitud: TextInputEditText
    private lateinit var etLongitud: TextInputEditText
    private lateinit var tilLatitud: TextInputLayout
    private lateinit var tilLongitud: TextInputLayout
    private lateinit var fabSalvar: ExtendedFloatingActionButton
    private lateinit var btnVerContactos: MaterialButton

    // ==================== GPS ====================
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    // ==================== ESTADO ====================
    private var fotoPath: String? = null      // Ruta de la foto tomada
    private var ubicacionObtenida = false      // Ya se obtuvo el GPS?
    private var contactoEditId = -1            // -1 = crear nuevo, otro valor = editar

    // Callback que recibe el resultado de la camara
    private val cameraLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && fotoPath != null) {
                val bitmap = BitmapFactory.decodeFile(fotoPath)
                ivFoto.setImageBitmap(bitmap)
                ivFoto.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
            }
        }

    // Callback que recibe el resultado de solicitar permisos
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permisos ->
            if (permisos.values.all { it }) {
                obtenerUbicacion()
            } else {
                mostrarSnackbar("Se necesitan permisos para funcionar correctamente")
            }
        }

    // ==================== CICLO DE VIDA ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupToolbar()
        setupListeners()
        setupBackNavigation()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Determinar si estamos creando o editando
        if (intent.hasExtra("contacto_id")) {
            contactoEditId = intent.getIntExtra("contacto_id", -1)
            cargarContactoParaEditar()
        } else {
            solicitarPermisos()
            startPulseAnimation()
        }
    }

    override fun onResume() {
        super.onResume()
        // Solo buscar GPS si es contacto nuevo y aun no se obtuvo ubicacion
        if (!ubicacionObtenida && contactoEditId == -1) {
            obtenerUbicacion()
        }
    }

    override fun onPause() {
        super.onPause()
        // Dejar de escuchar GPS al salir de la pantalla
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    // ==================== INICIALIZACION ====================

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        ivFoto = findViewById(R.id.ivFoto)
        fabCamera = findViewById(R.id.fabCamera)
        chipGps = findViewById(R.id.chipGps)
        etNombre = findViewById(R.id.etNombre)
        etTelefono = findViewById(R.id.etTelefono)
        etLatitud = findViewById(R.id.etLatitud)
        etLongitud = findViewById(R.id.etLongitud)
        tilLatitud = findViewById(R.id.tilLatitud)
        tilLongitud = findViewById(R.id.tilLongitud)
        fabSalvar = findViewById(R.id.fabSalvar)
        btnVerContactos = findViewById(R.id.btnVerContactos)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                transicionCerrar(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        })
    }

    private fun setupListeners() {
        // Click en la foto o boton de camara -> tomar foto
        ivFoto.setOnClickListener { tomarFoto() }
        fabCamera.setOnClickListener { tomarFoto() }

        // Boton guardar
        fabSalvar.setOnClickListener { salvarContacto() }

        // Boton para ir a la lista de contactos
        btnVerContactos.setOnClickListener {
            startActivity(Intent(this, ContactosActivity::class.java))
            transicionAbrir(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    // ==================== MODO EDICION ====================

    /** Llena el formulario con los datos del contacto a editar */
    private fun cargarContactoParaEditar() {
        val nombre = intent.getStringExtra("contacto_nombre") ?: ""
        val telefono = intent.getStringExtra("contacto_telefono") ?: ""
        val latitud = intent.getDoubleExtra("contacto_latitud", 0.0)
        val longitud = intent.getDoubleExtra("contacto_longitud", 0.0)
        val foto = intent.getStringExtra("contacto_foto")

        etNombre.setText(nombre)
        etTelefono.setText(telefono)
        etLatitud.setText(String.format(Locale.US, "%.8f", latitud))
        etLongitud.setText(String.format(Locale.US, "%.8f", longitud))
        fotoPath = foto
        ubicacionObtenida = true

        // Cargar foto si existe
        if (!foto.isNullOrEmpty()) {
            ImageLoader.cargar(foto, ivFoto, R.drawable.ic_person)
        }

        // Cambiar titulo y boton a modo edicion
        toolbar.title = getString(R.string.titulo_editar_contacto)
        fabSalvar.text = getString(R.string.btn_actualizar)
        stopPulseAnimation()
        actualizarChipGps(true)
    }

    // ==================== PERMISOS ====================

    /** Solicita permisos de camara y ubicacion si no estan otorgados */
    private fun solicitarPermisos() {
        val permisos = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (permisos.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            obtenerUbicacion()
        } else {
            permissionLauncher.launch(permisos)
        }
    }

    // ==================== GPS ====================

    /** Obtiene la ubicacion GPS del dispositivo */
    private fun obtenerUbicacion() {
        // Verificar si el GPS esta encendido
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            actualizarChipGps(false)
            MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle("GPS Desactivado")
                .setMessage("El GPS no esta activo. Desea activarlo?")
                .setPositiveButton("Activar") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }

        actualizarChipGps(true)

        // Verificar permiso de ubicacion
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // Intentar obtener la ultima ubicacion conocida (rapido)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && !ubicacionObtenida) {
                setUbicacion(location.latitude, location.longitude)
            }
        }

        // Solicitar ubicacion en tiempo real con alta precision
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(1000)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    setUbicacion(location.latitude, location.longitude)
                    // Dejar de escuchar despues de obtener una ubicacion
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback!!, Looper.getMainLooper()
        )
    }

    /** Coloca las coordenadas en los campos del formulario */
    private fun setUbicacion(lat: Double, lng: Double) {
        etLatitud.setText(String.format(Locale.US, "%.8f", lat))
        etLongitud.setText(String.format(Locale.US, "%.8f", lng))
        ubicacionObtenida = true
        stopPulseAnimation()
    }

    /** Actualiza el indicador visual del GPS (verde = activo, rojo = inactivo) */
    private fun actualizarChipGps(activo: Boolean) {
        if (activo) {
            chipGps.text = getString(R.string.gps_activo)
            chipGps.setChipBackgroundColorResource(R.color.gps_active)
            chipGps.setChipIconResource(android.R.drawable.presence_online)
        } else {
            chipGps.text = getString(R.string.gps_inactivo)
            chipGps.setChipBackgroundColorResource(R.color.gps_inactive)
            chipGps.setChipIconResource(android.R.drawable.presence_busy)
        }
    }

    // ==================== CAMARA ====================

    /** Abre la camara para tomar una foto */
    private fun tomarFoto() {
        // Verificar permiso de camara
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            return
        }

        // Crear archivo temporal para guardar la foto
        val archivoFoto: File? = try {
            crearArchivoImagen()
        } catch (_: IOException) {
            mostrarSnackbar("Error al crear archivo de imagen")
            null
        }

        // Abrir la camara con la URI del archivo
        archivoFoto?.let {
            val fotoUri: Uri = FileProvider.getUriForFile(
                this, "${applicationContext.packageName}.fileprovider", it
            )
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, fotoUri)
            }
            cameraLauncher.launch(intent)
        }
    }

    /** Crea un archivo temporal con nombre unico para la foto */
    @Throws(IOException::class)
    private fun crearArchivoImagen(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val directorio = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imagen = File.createTempFile("CONTACTO_$timeStamp", ".jpg", directorio)
        fotoPath = imagen.absolutePath
        return imagen
    }

    // ==================== GUARDAR CONTACTO ====================

    /** Valida los datos del formulario y envia al servidor */
    private fun salvarContacto() {
        val nombre = etNombre.text.toString().trim()
        val telefono = etTelefono.text.toString().trim()
        val latStr = etLatitud.text.toString().trim()
        val lngStr = etLongitud.text.toString().trim()
        val esEdicion = contactoEditId != -1

        // --- Validaciones ---

        // Foto obligatoria solo al crear (al editar puede ser URL remota)
        val tieneFoto = fotoPath != null && (fotoPath!!.startsWith("http") || File(fotoPath!!).exists())
        if (!tieneFoto && !esEdicion) {
            mostrarDialogo("Foto requerida", "Debe tomar una foto del contacto antes de guardar.")
            return
        }

        if (nombre.isEmpty()) {
            etNombre.error = "El nombre es obligatorio"
            etNombre.requestFocus()
            return
        }

        if (telefono.isEmpty()) {
            etTelefono.error = "El telefono es obligatorio"
            etTelefono.requestFocus()
            return
        }

        if (!telefono.matches(Regex("^[+]?[0-9\\s()-]{7,15}$"))) {
            etTelefono.error = "Formato de telefono invalido"
            etTelefono.requestFocus()
            return
        }

        // GPS obligatorio solo al crear (al editar ya tiene coordenadas)
        if (!esEdicion) {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mostrarDialogo("GPS Desactivado", "Active el GPS e intente de nuevo.")
                return
            }
        }

        if (latStr.isEmpty() || lngStr.isEmpty()) {
            mostrarDialogo("Ubicacion no disponible", "Espere a que se obtenga la ubicacion GPS.")
            return
        }

        val latitud = latStr.toDoubleOrNull()
        val longitud = lngStr.toDoubleOrNull()
        if (latitud == null || longitud == null) {
            mostrarSnackbar("Coordenadas invalidas")
            return
        }

        // --- Enviar al servidor ---

        val contacto = Contacto(
            id = if (esEdicion) contactoEditId else 0,
            nombre = nombre,
            telefono = telefono,
            latitud = latitud,
            longitud = longitud,
            fotoPath = fotoPath
        )

        // Solo enviar archivo si es foto local (no URL remota)
        val fotoFile = if (fotoPath != null && !fotoPath!!.startsWith("http")) File(fotoPath!!) else null
        fabSalvar.isEnabled = false

        if (esEdicion) {
            ApiService.actualizar(contacto, fotoFile, object : ApiService.ApiCallback<Boolean> {
                override fun onSuccess(result: Boolean) {
                    fabSalvar.isEnabled = true
                    mostrarSnackbar("Contacto actualizado", R.color.save_green)
                    finish()
                    transicionCerrar(R.anim.slide_in_left, R.anim.slide_out_right)
                }
                override fun onError(message: String) {
                    fabSalvar.isEnabled = true
                    mostrarSnackbar("Error: $message", R.color.delete_red)
                }
            })
        } else {
            ApiService.insertar(contacto, fotoFile, object : ApiService.ApiCallback<Int> {
                override fun onSuccess(result: Int) {
                    fabSalvar.isEnabled = true
                    mostrarSnackbar("Contacto guardado exitosamente", R.color.save_green)
                    limpiarFormulario()
                }
                override fun onError(message: String) {
                    fabSalvar.isEnabled = true
                    mostrarSnackbar("Error: $message", R.color.delete_red)
                }
            })
        }
    }

    /** Limpia el formulario despues de guardar exitosamente */
    private fun limpiarFormulario() {
        etNombre.setText("")
        etTelefono.setText("")
        etLatitud.setText("")
        etLongitud.setText("")
        ivFoto.setImageResource(R.drawable.ic_person)
        fotoPath = null
        ubicacionObtenida = false
        startPulseAnimation()
        obtenerUbicacion()
    }

    // ==================== ANIMACIONES ====================

    /** Inicia animacion de pulso en los campos de coordenadas (indica que busca GPS) */
    private fun startPulseAnimation() {
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        tilLatitud.startAnimation(pulse)
        tilLongitud.startAnimation(pulse)
    }

    /** Detiene la animacion de pulso (ya se obtuvo la ubicacion) */
    private fun stopPulseAnimation() {
        tilLatitud.clearAnimation()
        tilLongitud.clearAnimation()
    }

    // ==================== UTILIDADES ====================

    /** Muestra un mensaje breve en la parte inferior de la pantalla */
    private fun mostrarSnackbar(mensaje: String, colorRes: Int? = null) {
        val snackbar = Snackbar.make(findViewById(R.id.coordinatorLayout), mensaje, Snackbar.LENGTH_SHORT)
        colorRes?.let { snackbar.setBackgroundTint(ContextCompat.getColor(this, it)) }
        snackbar.show()
    }

    /** Muestra un dialogo informativo con un solo boton */
    private fun mostrarDialogo(titulo: String, mensaje: String) {
        MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton("Entendido", null)
            .show()
    }
}
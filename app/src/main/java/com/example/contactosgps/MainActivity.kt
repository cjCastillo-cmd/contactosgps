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
import java.util.Locale

class MainActivity : AppCompatActivity() {

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

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private var fotoPath: String? = null
    private var ubicacionObtenida = false
    private var contactoEditId = -1

    private val cameraLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && fotoPath != null) {
                val bitmap = BitmapFactory.decodeFile(fotoPath)
                ivFoto.setImageBitmap(bitmap)
                ivFoto.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                obtenerUbicacion()
            } else {
                Snackbar.make(
                    findViewById(R.id.coordinatorLayout),
                    "Se necesitan permisos para funcionar correctamente",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupToolbar()
        setupListeners()
        setupBackNavigation()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (intent.hasExtra("contacto_id")) {
            contactoEditId = intent.getIntExtra("contacto_id", -1)
            cargarContactoParaEditar()
        } else {
            solicitarPermisos()
            startPulseAnimation()
        }
    }

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
        ivFoto.setOnClickListener { tomarFoto() }
        fabCamera.setOnClickListener { tomarFoto() }

        fabSalvar.setOnClickListener { salvarContacto() }

        btnVerContactos.setOnClickListener {
            val intent = Intent(this, ContactosActivity::class.java)
            startActivity(intent)
            transicionAbrir(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun startPulseAnimation() {
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        tilLatitud.startAnimation(pulse)
        tilLongitud.startAnimation(pulse)
    }

    private fun stopPulseAnimation() {
        tilLatitud.clearAnimation()
        tilLongitud.clearAnimation()
    }

    private fun cargarContactoParaEditar() {
        // Cargar datos pasados por intent
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

        if (!foto.isNullOrEmpty()) {
            ImageLoader.cargar(foto, ivFoto, R.drawable.ic_person)
        }

        toolbar.title = getString(R.string.titulo_editar_contacto)
        fabSalvar.text = getString(R.string.btn_actualizar)
        stopPulseAnimation()
        actualizarChipGps(true)
    }

    private fun solicitarPermisos() {
        val permisos = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val todosOtorgados = permisos.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!todosOtorgados) {
            permissionLauncher.launch(permisos)
        } else {
            obtenerUbicacion()
        }
    }

    private fun obtenerUbicacion() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            actualizarChipGps(false)
            MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle("GPS Desactivado")
                .setMessage("El GPS no está activo. ¿Desea activarlo?")
                .setPositiveButton("Activar") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }

        actualizarChipGps(true)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // Intentar obtener la última ubicación conocida primero
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && !ubicacionObtenida) {
                setUbicacion(location.latitude, location.longitude)
            }
        }

        // Solicitar actualizaciones en tiempo real
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(1000)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    setUbicacion(location.latitude, location.longitude)
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback!!, Looper.getMainLooper()
        )
    }

    private fun setUbicacion(lat: Double, lng: Double) {
        etLatitud.setText(String.format(Locale.US, "%.8f", lat))
        etLongitud.setText(String.format(Locale.US, "%.8f", lng))
        ubicacionObtenida = true
        stopPulseAnimation()
    }

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

    override fun onResume() {
        super.onResume()
        if (!ubicacionObtenida && contactoEditId == -1) {
            obtenerUbicacion()
        }
    }

    override fun onPause() {
        super.onPause()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    private fun tomarFoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            return
        }

        val archivoFoto: File? = try {
            crearArchivoImagen()
        } catch (_: IOException) {
            Snackbar.make(
                findViewById(R.id.coordinatorLayout),
                "Error al crear archivo de imagen",
                Snackbar.LENGTH_SHORT
            ).show()
            null
        }

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

    @Throws(IOException::class)
    private fun crearArchivoImagen(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val directorio = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imagen = File.createTempFile("CONTACTO_$timeStamp", ".jpg", directorio)
        fotoPath = imagen.absolutePath
        return imagen
    }

    private fun salvarContacto() {
        val nombre = etNombre.text.toString().trim()
        val telefono = etTelefono.text.toString().trim()
        val latStr = etLatitud.text.toString().trim()
        val lngStr = etLongitud.text.toString().trim()
        val coordinatorLayout = findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.coordinatorLayout)

        // Solo exigir foto local en creacion nueva (en edicion puede ser URL remota)
        val esEdicion = contactoEditId != -1
        val tieneFoto = fotoPath != null && (fotoPath!!.startsWith("http") || File(fotoPath!!).exists())
        if (!tieneFoto && !esEdicion) {
            MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle("Foto requerida")
                .setMessage("Debe tomar una foto del contacto antes de guardar.")
                .setPositiveButton("Entendido", null)
                .show()
            return
        }

        if (nombre.isEmpty()) {
            etNombre.error = "El nombre es obligatorio"
            etNombre.requestFocus()
            return
        }

        if (telefono.isEmpty()) {
            etTelefono.error = "El teléfono es obligatorio"
            etTelefono.requestFocus()
            return
        }

        if (!telefono.matches(Regex("^[+]?[0-9\\s()-]{7,15}$"))) {
            etTelefono.error = "Formato de teléfono inválido"
            etTelefono.requestFocus()
            return
        }

        // Solo exigir GPS en creacion nueva, en edicion ya tiene coordenadas
        if (!esEdicion) {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                    .setTitle("GPS Desactivado")
                    .setMessage("El GPS no está activo. Active el GPS e intente de nuevo.")
                    .setPositiveButton("Entendido", null)
                    .show()
                return
            }
        }

        if (latStr.isEmpty() || lngStr.isEmpty()) {
            MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle("Ubicación no disponible")
                .setMessage("Aún no se ha obtenido la ubicación GPS. Espere un momento e intente de nuevo.")
                .setPositiveButton("Entendido", null)
                .show()
            return
        }

        val latitud = latStr.toDoubleOrNull()
        val longitud = lngStr.toDoubleOrNull()
        if (latitud == null || longitud == null) {
            Snackbar.make(coordinatorLayout, "Coordenadas inválidas", Snackbar.LENGTH_SHORT).show()
            return
        }

        val contacto = Contacto(
            id = if (contactoEditId != -1) contactoEditId else 0,
            nombre = nombre,
            telefono = telefono,
            latitud = latitud,
            longitud = longitud,
            fotoPath = fotoPath
        )

        val fotoFile = if (fotoPath != null && !fotoPath!!.startsWith("http")) File(fotoPath!!) else null
        fabSalvar.isEnabled = false

        if (contactoEditId != -1) {
            ApiService.actualizar(contacto, fotoFile, object : ApiService.ApiCallback<Boolean> {
                override fun onSuccess(result: Boolean) {
                    fabSalvar.isEnabled = true
                    Snackbar.make(coordinatorLayout, "Contacto actualizado", Snackbar.LENGTH_SHORT)
                        .setBackgroundTint(ContextCompat.getColor(this@MainActivity, R.color.save_green))
                        .show()
                    finish()
                    transicionCerrar(R.anim.slide_in_left, R.anim.slide_out_right)
                }
                override fun onError(message: String) {
                    fabSalvar.isEnabled = true
                    Snackbar.make(coordinatorLayout, "Error: $message", Snackbar.LENGTH_LONG)
                        .setBackgroundTint(ContextCompat.getColor(this@MainActivity, R.color.delete_red))
                        .show()
                }
            })
        } else {
            ApiService.insertar(contacto, fotoFile, object : ApiService.ApiCallback<Int> {
                override fun onSuccess(result: Int) {
                    fabSalvar.isEnabled = true
                    Snackbar.make(coordinatorLayout, "Contacto guardado exitosamente", Snackbar.LENGTH_SHORT)
                        .setBackgroundTint(ContextCompat.getColor(this@MainActivity, R.color.save_green))
                        .show()
                    limpiarFormulario()
                }
                override fun onError(message: String) {
                    fabSalvar.isEnabled = true
                    Snackbar.make(coordinatorLayout, "Error: $message", Snackbar.LENGTH_LONG)
                        .setBackgroundTint(ContextCompat.getColor(this@MainActivity, R.color.delete_red))
                        .show()
                }
            })
        }
    }

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
}

package com.example.contactosgps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import de.hdodenhof.circleimageview.CircleImageView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Pantalla que muestra la ubicacion de un contacto en un mapa interactivo.
 * - Usa OSMDroid (OpenStreetMap) para mostrar el mapa
 * - Muestra un marcador en la ubicacion del contacto
 * - Permite centrar el mapa y realizar llamadas telefonicas
 */
class MapaActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var mapView: MapView
    private lateinit var ivFotoMapa: CircleImageView
    private lateinit var tvNombreMapa: TextView
    private lateinit var tvTelefonoMapa: TextView
    private lateinit var fabCentrar: FloatingActionButton
    private lateinit var fabLlamar: FloatingActionButton

    // Datos del contacto recibidos por Intent
    private var nombre = ""
    private var telefono = ""
    private var latitud = 0.0
    private var longitud = 0.0
    private var fotoPath: String? = null

    // Callback para solicitar permiso de llamada
    private val callPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) realizarLlamada()
        }

    // ==================== CICLO DE VIDA ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar OSMDroid ANTES de setContentView
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_mapa)

        // Recibir datos del contacto
        nombre = intent.getStringExtra("nombre") ?: ""
        telefono = intent.getStringExtra("telefono") ?: ""
        latitud = intent.getDoubleExtra("latitud", 0.0)
        longitud = intent.getDoubleExtra("longitud", 0.0)
        fotoPath = intent.getStringExtra("foto_path")

        initViews()
        setupToolbar()
        setupInfoCard()
        setupListeners()
        setupBackNavigation()
        setupMap()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    // ==================== INICIALIZACION ====================

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        mapView = findViewById(R.id.mapView)
        ivFotoMapa = findViewById(R.id.ivFotoMapa)
        tvNombreMapa = findViewById(R.id.tvNombreMapa)
        tvTelefonoMapa = findViewById(R.id.tvTelefonoMapa)
        fabCentrar = findViewById(R.id.fabCentrar)
        fabLlamar = findViewById(R.id.fabLlamar)
    }

    private fun setupToolbar() {
        toolbar.title = "Ubicacion de $nombre"
        toolbar.setNavigationOnClickListener {
            finish()
            transicionCerrar(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                transicionCerrar(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        })
    }

    /** Configura la tarjeta inferior con la info del contacto */
    private fun setupInfoCard() {
        tvNombreMapa.text = nombre
        tvTelefonoMapa.text = telefono
        ImageLoader.cargar(fotoPath, ivFotoMapa, R.drawable.ic_person)
    }

    private fun setupListeners() {
        // Boton centrar: regresa el mapa a la ubicacion del contacto
        fabCentrar.setOnClickListener {
            mapView.controller.animateTo(GeoPoint(latitud, longitud), 17.0, 1000L)
        }

        // Boton llamar: realiza llamada al contacto
        fabLlamar.setOnClickListener {
            if (telefono.isEmpty()) {
                Snackbar.make(findViewById(R.id.coordinatorLayout), "No hay numero de telefono", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                realizarLlamada()
            } else {
                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        }
    }

    // ==================== MAPA ====================

    /** Configura el mapa con tiles de OpenStreetMap y agrega el marcador */
    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.zoomController.setVisibility(
            org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT
        )

        val punto = GeoPoint(latitud, longitud)

        // Mostrar zoom lejano primero y luego animar acercandose
        mapView.controller.setZoom(5.0)
        mapView.controller.setCenter(punto)
        mapView.post {
            mapView.controller.animateTo(punto, 17.0, 2000L)
        }

        // Crear marcador en la ubicacion del contacto
        val marker = Marker(mapView).apply {
            position = punto
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = nombre
            snippet = telefono
        }

        // Icono personalizado del marcador
        ContextCompat.getDrawable(this, R.drawable.ic_map_marker)?.let {
            marker.icon = it
        }

        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    // ==================== LLAMADA ====================

    /** Inicia una llamada telefonica al numero del contacto */
    private fun realizarLlamada() {
        startActivity(Intent(Intent.ACTION_CALL, "tel:$telefono".toUri()))
    }
}
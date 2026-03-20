package com.example.contactosgps

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

/**
 * Pantalla que muestra la lista de contactos guardados.
 * - Busqueda en tiempo real por nombre o telefono
 * - Click en contacto: menu con opciones (mapa, editar, eliminar)
 * - Swipe izquierda: eliminar / Swipe derecha: editar
 * - FAB para agregar nuevo contacto
 */
class ContactosActivity : AppCompatActivity(), ContactoAdapter.OnContactoClickListener {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var searchView: SearchView
    private lateinit var rvContactos: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var fabAgregar: FloatingActionButton
    private lateinit var adapter: ContactoAdapter

    // ==================== CICLO DE VIDA ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contactos)

        initViews()
        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupListeners()
        setupBackNavigation()
    }

    override fun onResume() {
        super.onResume()
        // Recargar contactos cada vez que se vuelve a esta pantalla
        cargarContactos()
    }

    // ==================== INICIALIZACION ====================

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        searchView = findViewById(R.id.searchView)
        rvContactos = findViewById(R.id.rvContactos)
        emptyState = findViewById(R.id.emptyState)
        fabAgregar = findViewById(R.id.fabAgregar)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
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

    private fun setupRecyclerView() {
        adapter = ContactoAdapter(emptyList(), this)
        rvContactos.layoutManager = LinearLayoutManager(this)
        rvContactos.adapter = adapter

        // Configurar gestos de swipe (deslizar items)
        val swipeHelper = SwipeHelper(
            onSwipeLeft = { position ->
                if (position < adapter.itemCount) confirmarEliminar(adapter.getItem(position), position)
            },
            onSwipeRight = { position ->
                if (position < adapter.itemCount) editarContacto(adapter.getItem(position))
            }
        )
        ItemTouchHelper(swipeHelper).attachToRecyclerView(rvContactos)
    }

    /** Configura el buscador para filtrar contactos mientras se escribe */
    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                adapter.filter.filter(query)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter.filter(newText)
                return true
            }
        })
    }

    private fun setupListeners() {
        // FAB para crear nuevo contacto
        fabAgregar.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            transicionAbrir(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    // ==================== DATOS ====================

    /** Obtiene la lista de contactos desde el servidor */
    private fun cargarContactos() {
        ApiService.listar(object : ApiService.ApiCallback<List<Contacto>> {
            override fun onSuccess(result: List<Contacto>) {
                adapter.actualizarDatos(result)
                toolbar.subtitle = "${result.size} contacto${if (result.size != 1) "s" else ""}"

                // Mostrar empty state si no hay contactos
                if (result.isEmpty()) {
                    rvContactos.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                } else {
                    rvContactos.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                }
            }

            override fun onError(message: String) {
                Snackbar.make(
                    findViewById(R.id.coordinatorLayout),
                    "Error al cargar: $message",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        })
    }

    // ==================== ACCIONES ====================

    /** Click en un contacto -> muestra menu con opciones */
    override fun onItemClick(contacto: Contacto) {
        val opciones = arrayOf("Ver en mapa", "Editar", "Eliminar")
        MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
            .setTitle(contacto.nombre)
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> abrirMapa(contacto)
                    1 -> editarContacto(contacto)
                    2 -> confirmarEliminar(contacto, adapter.getItemPosition(contacto))
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Click en icono de mapa -> abrir mapa directo */
    override fun onMapClick(contacto: Contacto) {
        abrirMapa(contacto)
    }

    /** Abre la pantalla del mapa con la ubicacion del contacto */
    private fun abrirMapa(contacto: Contacto) {
        val intent = Intent(this, MapaActivity::class.java).apply {
            putExtra("nombre", contacto.nombre)
            putExtra("telefono", contacto.telefono)
            putExtra("latitud", contacto.latitud)
            putExtra("longitud", contacto.longitud)
            putExtra("foto_path", contacto.fotoPath)
        }
        startActivity(intent)
        transicionAbrir(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    /** Abre el formulario en modo edicion con los datos del contacto */
    private fun editarContacto(contacto: Contacto) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("contacto_id", contacto.id)
            putExtra("contacto_nombre", contacto.nombre)
            putExtra("contacto_telefono", contacto.telefono)
            putExtra("contacto_latitud", contacto.latitud)
            putExtra("contacto_longitud", contacto.longitud)
            putExtra("contacto_foto", contacto.fotoPath)
        }
        startActivity(intent)
        transicionAbrir(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    /** Muestra dialogo de confirmacion antes de eliminar */
    private fun confirmarEliminar(contacto: Contacto, position: Int) {
        MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
            .setTitle("Eliminar Contacto")
            .setMessage("Esta seguro de eliminar a ${contacto.nombre}?\n\nEsta accion no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                ApiService.eliminar(contacto.id, object : ApiService.ApiCallback<Boolean> {
                    override fun onSuccess(result: Boolean) {
                        cargarContactos()
                        Snackbar.make(
                            findViewById(R.id.coordinatorLayout),
                            "Contacto eliminado",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    override fun onError(message: String) {
                        adapter.notifyItemChanged(position)
                        Snackbar.make(
                            findViewById(R.id.coordinatorLayout),
                            "Error: $message",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                })
            }
            .setNegativeButton("Cancelar") { _, _ -> adapter.notifyItemChanged(position) }
            .setOnCancelListener { adapter.notifyItemChanged(position) }
            .show()
    }
}
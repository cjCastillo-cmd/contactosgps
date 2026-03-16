package com.example.contactosgps

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.hdodenhof.circleimageview.CircleImageView
import java.util.Locale

class ContactoAdapter(
    private var contactos: List<Contacto>,
    private val listener: OnContactoClickListener
) : RecyclerView.Adapter<ContactoAdapter.ViewHolder>(), Filterable {

    interface OnContactoClickListener {
        fun onItemClick(contacto: Contacto)
        fun onMapClick(contacto: Contacto)
    }

    private var contactosFiltrados: List<Contacto> = ArrayList(contactos)
    private var lastAnimatedPosition = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFoto: CircleImageView = view.findViewById(R.id.ivFotoItem)
        val tvInicial: TextView = view.findViewById(R.id.tvInicial)
        val tvNombre: TextView = view.findViewById(R.id.tvNombre)
        val tvTelefono: TextView = view.findViewById(R.id.tvTelefono)
        val tvCoordenadas: TextView = view.findViewById(R.id.tvCoordenadas)
        val ivMapa: ImageView = view.findViewById(R.id.ivMapa)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contacto, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contacto = contactosFiltrados[position]

        holder.tvNombre.text = contacto.nombre
        holder.tvTelefono.text = contacto.telefono
        holder.tvCoordenadas.text = String.format(Locale.US, "\uD83D\uDCCD %.6f, %.6f", contacto.latitud, contacto.longitud)

        // Foto o inicial
        if (!contacto.fotoPath.isNullOrEmpty()) {
            ImageLoader.cargar(contacto.fotoPath, holder.ivFoto, R.drawable.ic_person)
            holder.ivFoto.visibility = View.VISIBLE
            holder.tvInicial.visibility = View.GONE
        } else {
            holder.ivFoto.setImageResource(R.drawable.ic_person)
            holder.tvInicial.visibility = View.VISIBLE
            holder.tvInicial.text = contacto.nombre.firstOrNull()?.uppercase() ?: "?"
            val colors = intArrayOf(0xFF1565C0.toInt(), 0xFF00BCD4.toInt(), 0xFF4CAF50.toInt(),
                0xFFFF9800.toInt(), 0xFF9C27B0.toInt(), 0xFFE91E63.toInt())
            val colorIndex = (contacto.nombre.hashCode() and Int.MAX_VALUE) % colors.size
            holder.ivFoto.setCircleBackgroundColor(colors[colorIndex])
        }

        holder.itemView.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION) {
                listener.onItemClick(contactosFiltrados[adapterPos])
            }
        }
        holder.ivMapa.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION) {
                listener.onMapClick(contactosFiltrados[adapterPos])
            }
        }

        // Animación slide from bottom
        val currentPos = holder.bindingAdapterPosition
        if (currentPos > lastAnimatedPosition) {
            val animation = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.slide_up)
            animation.startOffset = (currentPos * 50).toLong()
            holder.itemView.startAnimation(animation)
            lastAnimatedPosition = currentPos
        }
    }

    override fun getItemCount(): Int = contactosFiltrados.size

    fun getItem(position: Int): Contacto = contactosFiltrados[position]

    @SuppressLint("NotifyDataSetChanged")
    fun actualizarDatos(nuevos: List<Contacto>) {
        contactos = nuevos
        contactosFiltrados = ArrayList(nuevos)
        lastAnimatedPosition = -1
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                if (constraint.isNullOrEmpty()) {
                    results.values = ArrayList(contactos)
                    results.count = contactos.size
                } else {
                    val filtro = constraint.toString().lowercase()
                    val filtrados = contactos.filter {
                        it.nombre.lowercase().contains(filtro) ||
                                it.telefono.lowercase().contains(filtro)
                    }
                    results.values = filtrados
                    results.count = filtrados.size
                }
                return results
            }

            @Suppress("UNCHECKED_CAST")
            @SuppressLint("NotifyDataSetChanged")
            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                contactosFiltrados = results.values as List<Contacto>
                notifyDataSetChanged()
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        holder.itemView.clearAnimation()
    }
}

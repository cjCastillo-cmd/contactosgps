package com.example.contactosgps

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Agrega gestos de deslizamiento (swipe) a los items del RecyclerView.
 * - Swipe izquierda: fondo rojo + icono eliminar
 * - Swipe derecha: fondo verde + icono editar
 */
class SwipeHelper(
    private val onSwipeLeft: (Int) -> Unit,
    private val onSwipeRight: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    // Paint reutilizable (no se crea nuevo en cada frame)
    private val paint = Paint()

    /** No soportamos drag & drop, solo swipe */
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    /** Se ejecuta cuando el usuario termina de deslizar un item */
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        when (direction) {
            ItemTouchHelper.LEFT -> onSwipeLeft(position)
            ItemTouchHelper.RIGHT -> onSwipeRight(position)
        }
    }

    /** Dibuja el fondo de color y el icono mientras el usuario desliza */
    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val cornerRadius = 16f * recyclerView.context.resources.displayMetrics.density

        if (dX < 0) {
            // Deslizando hacia la IZQUIERDA -> Eliminar (rojo)
            paint.color = ContextCompat.getColor(recyclerView.context, R.color.delete_red)
            val fondo = RectF(
                itemView.right + dX, itemView.top.toFloat(),
                itemView.right.toFloat(), itemView.bottom.toFloat()
            )
            c.drawRoundRect(fondo, cornerRadius, cornerRadius, paint)

            // Dibujar icono de eliminar centrado verticalmente
            ContextCompat.getDrawable(recyclerView.context, android.R.drawable.ic_menu_delete)?.let { icon ->
                val margin = (itemView.height - icon.intrinsicHeight) / 2
                icon.setBounds(
                    itemView.right - margin - icon.intrinsicWidth,
                    itemView.top + margin,
                    itemView.right - margin,
                    itemView.top + margin + icon.intrinsicHeight
                )
                icon.setTint(0xFFFFFFFF.toInt())
                icon.draw(c)
            }

        } else if (dX > 0) {
            // Deslizando hacia la DERECHA -> Editar (verde)
            paint.color = ContextCompat.getColor(recyclerView.context, R.color.edit_green)
            val fondo = RectF(
                itemView.left.toFloat(), itemView.top.toFloat(),
                itemView.left + dX, itemView.bottom.toFloat()
            )
            c.drawRoundRect(fondo, cornerRadius, cornerRadius, paint)

            // Dibujar icono de editar centrado verticalmente
            ContextCompat.getDrawable(recyclerView.context, android.R.drawable.ic_menu_edit)?.let { icon ->
                val margin = (itemView.height - icon.intrinsicHeight) / 2
                icon.setBounds(
                    itemView.left + margin,
                    itemView.top + margin,
                    itemView.left + margin + icon.intrinsicWidth,
                    itemView.top + margin + icon.intrinsicHeight
                )
                icon.setTint(0xFFFFFFFF.toInt())
                icon.draw(c)
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
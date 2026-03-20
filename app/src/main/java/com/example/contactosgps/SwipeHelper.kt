package com.example.contactosgps

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class SwipeHelper(
    private val onSwipeLeft: (Int) -> Unit,
    private val onSwipeRight: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val paint = Paint()

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        when (direction) {
            ItemTouchHelper.LEFT -> onSwipeLeft(position)
            ItemTouchHelper.RIGHT -> onSwipeRight(position)
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val cornerRadius = 16f * recyclerView.context.resources.displayMetrics.density

        if (dX < 0) {
            // Swipe izquierda - Eliminar (rojo)
            paint.color = ContextCompat.getColor(recyclerView.context, R.color.delete_red)
            val background = RectF(
                itemView.right + dX, itemView.top.toFloat(),
                itemView.right.toFloat(), itemView.bottom.toFloat()
            )
            c.drawRoundRect(background, cornerRadius, cornerRadius, paint)

            val icon: Drawable? = ContextCompat.getDrawable(recyclerView.context, android.R.drawable.ic_menu_delete)
            icon?.let {
                val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                val iconTop = itemView.top + iconMargin
                val iconLeft = itemView.right - iconMargin - it.intrinsicWidth
                val iconRight = itemView.right - iconMargin
                val iconBottom = iconTop + it.intrinsicHeight
                it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                it.setTint(0xFFFFFFFF.toInt())
                it.draw(c)
            }
        } else if (dX > 0) {
            // Swipe derecha - Editar (verde)
            paint.color = ContextCompat.getColor(recyclerView.context, R.color.edit_green)
            val background = RectF(
                itemView.left.toFloat(), itemView.top.toFloat(),
                itemView.left + dX, itemView.bottom.toFloat()
            )
            c.drawRoundRect(background, cornerRadius, cornerRadius, paint)

            val icon: Drawable? = ContextCompat.getDrawable(recyclerView.context, android.R.drawable.ic_menu_edit)
            icon?.let {
                val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                val iconTop = itemView.top + iconMargin
                val iconLeft = itemView.left + iconMargin
                val iconRight = iconLeft + it.intrinsicWidth
                val iconBottom = iconTop + it.intrinsicHeight
                it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                it.setTint(0xFFFFFFFF.toInt())
                it.draw(c)
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}

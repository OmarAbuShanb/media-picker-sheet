package dev.anonymous.media_picker_library.ui.adapter

import android.view.MotionEvent
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.widget.RecyclerView

class MediaItemDetailsLookup(
    private val recyclerView: RecyclerView
) : ItemDetailsLookup<Long>() {

    override fun getItemDetails(e: MotionEvent): ItemDetails<Long>? {
        val view = recyclerView.findChildViewUnder(e.x, e.y) ?: return null
        val viewHolder = recyclerView.getChildViewHolder(view)
        return when (viewHolder) {
            is MediaAdapter.ImageViewHolder -> {
                viewHolder.getItemDetails()
            }

            is MediaAdapter.VideoViewHolder -> {
                viewHolder.getItemDetails()
            }

            else -> null
        }
    }
}

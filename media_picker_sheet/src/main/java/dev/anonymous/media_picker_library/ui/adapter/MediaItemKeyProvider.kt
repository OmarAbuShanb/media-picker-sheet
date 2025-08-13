package dev.anonymous.media_picker_library.ui.adapter

import androidx.recyclerview.selection.ItemKeyProvider

class MediaItemKeyProvider(private val adapter: MediaAdapter) : ItemKeyProvider<Long>(SCOPE_MAPPED) {

    override fun getKey(position: Int): Long? {
        if (position in 0 until adapter.itemCount) {
            return adapter.currentList[position].id
        }
        return null
    }

    override fun getPosition(key: Long): Int {
        return adapter.currentList.indexOfFirst { it.id == key }
    }
}

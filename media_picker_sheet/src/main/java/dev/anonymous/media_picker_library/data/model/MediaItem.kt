package dev.anonymous.media_picker_library.data.model

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val duration: Long
)

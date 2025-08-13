package dev.anonymous.media_picker_library.data.model

import android.net.Uri
import dev.anonymous.media_picker_library.config.FolderType

data class MediaFolder(
    val name: String,
    val bucketId: Long? = null,
    val mediaCount: Int,
    val thumbnailUri: Uri,
    val type: FolderType
)

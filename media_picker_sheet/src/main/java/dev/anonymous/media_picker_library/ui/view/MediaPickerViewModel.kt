package dev.anonymous.media_picker_library.ui.view

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.anonymous.media_picker_library.data.model.MediaItem
import dev.anonymous.media_picker_library.data.source.MediaLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MediaPickerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

    private var currentBucketId: Long? = null
    private var currentMediaType: Int? = null

    private val _mediaList = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaList = _mediaList.asStateFlow()

    init {
        loadMedia(null, null)
    }

    fun loadMedia(bucketId: Long?, mediaType: Int?) {
        currentBucketId = bucketId
        currentMediaType = mediaType

        viewModelScope.launch {
            val mediaItems =
                MediaLoader.Companion.fetchAllMediaItems(appContext, currentBucketId, currentMediaType)
            _mediaList.value = mediaItems
        }
    }
}
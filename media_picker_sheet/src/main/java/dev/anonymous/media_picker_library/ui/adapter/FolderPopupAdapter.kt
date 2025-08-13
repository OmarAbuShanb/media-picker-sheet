package dev.anonymous.media_picker_library.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import dev.anonymous.media_picker_library.data.model.MediaFolder
import dev.anonymous.media_picker_library.data.source.MediaLoader
import dev.anonymous.media_picker_library.R
import dev.anonymous.media_picker_library.databinding.ItemFolderThumbnailBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderPopupAdapter(
    private val folders: List<MediaFolder>,
    private val imageLoader: ImageLoader,
    private val listener: (MediaFolder) -> Unit
) : RecyclerView.Adapter<FolderPopupAdapter.FolderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding =
            ItemFolderThumbnailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(folders[position])
    }

    override fun getItemCount() = folders.size

    override fun onViewRecycled(holder: FolderViewHolder) {
        holder.loadJob?.cancel()
    }

    inner class FolderViewHolder(val binding: ItemFolderThumbnailBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val context = binding.root.context
        var loadJob: Job? = null

        fun bind(folder: MediaFolder) {
            binding.root.setOnClickListener { listener(folder) }

            binding.folderName.text = folder.name
            binding.folderCount.text = folder.mediaCount.toString()

            loadJob?.cancel()
            loadJob = CoroutineScope(Dispatchers.IO).launch {
                val (thumbUri, bitmap) = MediaLoader.Companion.getVideoThumbnail(
                    context,
                    folder.thumbnailUri
                )
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        val requestBuilder = ImageRequest.Builder(context)
                            .data(bitmap)
                            .target(binding.folderThumbnail)
                            .placeholder(R.drawable.ic_image)
                            .error(R.drawable.ic_image)

                        val imageRequest = requestBuilder.build()
                        imageLoader.enqueue(imageRequest)
                    } else {
                        binding.folderThumbnail.load(thumbUri) {
                            placeholder(R.drawable.ic_image)
                            error(R.drawable.ic_image)
                            crossfade(true)
                        }
                    }
                }
            }
        }
    }
}

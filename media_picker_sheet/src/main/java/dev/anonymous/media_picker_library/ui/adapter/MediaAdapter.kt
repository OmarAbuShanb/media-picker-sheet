package dev.anonymous.media_picker_library.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.dispose
import coil.load
import coil.request.ImageRequest
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import dev.anonymous.media_picker_library.data.model.MediaItem
import dev.anonymous.media_picker_library.data.source.MediaLoader
import dev.anonymous.media_picker_library.R
import dev.anonymous.media_picker_library.databinding.ItemImageThumbnailBinding
import dev.anonymous.media_picker_library.databinding.ItemVideoThumbnailBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaAdapter(
    private val imageLoader: ImageLoader,
    private val listener: (MediaItem) -> Unit
) : ListAdapter<MediaItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private const val VIEW_TYPE_IMAGE = 0
        private const val VIEW_TYPE_VIDEO = 1

        private val roundedCornersPositions = mutableSetOf<Long>()

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MediaItem>() {
            override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
                return if (roundedCornersPositions.contains(oldItem.id)) {
                    roundedCornersPositions.remove(oldItem.id)
                    false
                } else if (roundedCornersPositions.contains(newItem.id)) {
                    roundedCornersPositions.remove(newItem.id)
                    false
                } else true
            }
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position)?.id ?: RecyclerView.NO_ID
    }

    var selectionTracker: SelectionTracker<Long>? = null

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position)?.isVideo == true) VIEW_TYPE_VIDEO else VIEW_TYPE_IMAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_VIDEO -> {
                val binding = ItemVideoThumbnailBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                VideoViewHolder(binding)
            }

            else -> {
                val binding = ItemImageThumbnailBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ImageViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val mediaItem = getItem(position) ?: return

        println("position $position")
        val isSelected = selectionTracker?.isSelected(mediaItem.id) == true

        when (holder) {
            is ImageViewHolder -> holder.bind(mediaItem, isSelected)
            is VideoViewHolder -> holder.bind(mediaItem, isSelected)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is ImageViewHolder -> {
                holder.binding.imageView.dispose()
            }

            is VideoViewHolder -> {
                holder.loadJob?.cancel()
            }
        }
    }

    inner class ImageViewHolder(val binding: ItemImageThumbnailBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val context = binding.root.context

        fun bind(mediaItem: MediaItem, isSelected: Boolean) {
            binding.checkmark.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.overlay.visibility = if (isSelected) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                listener(mediaItem)
            }

            val requestBuilder = ImageRequest.Builder(context)
                .data(mediaItem.uri)
                .target(binding.imageView)
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .size(300)
                .scale(Scale.FIT)

            val imageRequest = withRoundedCornersIfNeeded(
                context,
                requestBuilder,
                bindingAdapterPosition
            ).build()

            imageLoader.enqueue(imageRequest)
        }

        fun getItemDetails(): ItemDetailsLookup.ItemDetails<Long> {
            return object : ItemDetailsLookup.ItemDetails<Long>() {
                override fun getPosition(): Int = bindingAdapterPosition
                override fun getSelectionKey(): Long = getItem(bindingAdapterPosition)?.id ?: -1L
            }
        }
    }

    inner class VideoViewHolder(private val binding: ItemVideoThumbnailBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val context = binding.root.context
        var loadJob: Job? = null

        fun bind(mediaItem: MediaItem, isSelected: Boolean) {
            binding.checkmark.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.overlay.visibility = if (isSelected) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                listener(mediaItem)
            }

            loadJob?.cancel()
            loadJob = CoroutineScope(Dispatchers.IO).launch {
                val (thumbUri, bitmap) = MediaLoader.Companion.getVideoThumbnail(context, mediaItem.uri)
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        val requestBuilder = ImageRequest.Builder(context)
                            .data(bitmap)
                            .target(binding.imageView)
                            .placeholder(R.drawable.ic_image)
                            .error(R.drawable.ic_image)

                        val imageRequest = withRoundedCornersIfNeeded(
                            context,
                            requestBuilder,
                            bindingAdapterPosition
                        ).build()

                        imageLoader.enqueue(imageRequest)
                    } else {
                        binding.imageView.load(thumbUri) {
                            placeholder(R.drawable.ic_image)
                            error(R.drawable.ic_image)
                            crossfade(true)
                        }
                    }
                }
            }

            binding.videoDurationText.text = formatDuration(mediaItem.duration)
        }

        private fun formatDuration(durationMillis: Long): String {
            val totalSeconds = durationMillis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return if (hours > 0)
                "${hours}:${minutes.toString().padStart(2, '0')}:${
                    seconds.toString().padStart(2, '0')
                }"
            else
                "${minutes}:${seconds.toString().padStart(2, '0')}"
        }

        fun getItemDetails(): ItemDetailsLookup.ItemDetails<Long> {
            return object : ItemDetailsLookup.ItemDetails<Long>() {
                override fun getPosition(): Int = bindingAdapterPosition
                override fun getSelectionKey(): Long = getItem(bindingAdapterPosition)?.id ?: -1L
            }
        }
    }

    private fun withRoundedCornersIfNeeded(
        context: Context,
        builder: ImageRequest.Builder,
        position: Int
    ): ImageRequest.Builder {
        val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val itemId = getItem(position)?.id ?: return builder // Should not happen

        return when (position) {
            0 -> {
                roundedCornersPositions.add(itemId)
                builder.transformations(
                    RoundedCornersTransformation(
                        topLeft = if (isRtl) 0f else 30f,
                        topRight = if (isRtl) 30f else 0f,
                    )
                )
            }

            2 -> {
                roundedCornersPositions.add(itemId)
                builder.transformations(
                    RoundedCornersTransformation(
                        topRight = if (isRtl) 0f else 30f,
                        topLeft = if (isRtl) 30f else 0f,
                    )
                )
            }

            else -> {
                builder
            }
        }
    }
}



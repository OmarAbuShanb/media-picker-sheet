package dev.anonymous.media_picker_library.ui.view

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import dev.anonymous.media_picker_library.R
import dev.anonymous.media_picker_library.config.FolderType
import dev.anonymous.media_picker_library.data.model.MediaFolder
import dev.anonymous.media_picker_library.data.source.MediaLoader
import dev.anonymous.media_picker_library.databinding.BottomSheetMediaPickerBinding
import dev.anonymous.media_picker_library.databinding.PopupFolderListBinding
import dev.anonymous.media_picker_library.ui.adapter.FolderPopupAdapter
import dev.anonymous.media_picker_library.ui.adapter.MediaAdapter
import dev.anonymous.media_picker_library.ui.adapter.MediaItemDetailsLookup
import dev.anonymous.media_picker_library.ui.adapter.MediaItemKeyProvider
import dev.anonymous.media_picker_library.ui.util.GridSpacingItemDecoration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MediaPickerBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetMediaPickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MediaPickerViewModel
    private lateinit var adapter: MediaAdapter

    private var folderPopupWindow: PopupWindow? = null
    private val folderList = mutableListOf<MediaFolder>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetMediaPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        )[MediaPickerViewModel::class.java]

        initBottomSheet(view)
        setupRecyclerView()
        observeViewModel()
        setupClickListeners()
        checkPermissionsAndLoadFolders()
    }

    private fun initBottomSheet(viewContent: View) {
        val initialCornerRadius = resources.getDimension(R.dimen.bottom_sheet_corner_radius)

        val shapeBuilder = ShapeAppearanceModel.builder()
            .setTopLeftCornerSize(initialCornerRadius)
            .setTopRightCornerSize(initialCornerRadius)

        val materialShapeDrawable = MaterialShapeDrawable(shapeBuilder.build()).apply {
            fillColor = ColorStateList.valueOf(
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorSurface,
                    null
                )
            )
        }

        viewContent.background = materialShapeDrawable

        val bottomSheet = viewContent.parent as View
        bottomSheet.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT

        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset >= 0) {
                    val radius = initialCornerRadius * (1 - slideOffset)

                    shapeBuilder
                        .setTopLeftCornerSize(radius)
                        .setTopRightCornerSize(radius)

                    materialShapeDrawable.shapeAppearanceModel = shapeBuilder.build()
                }
            }
        })
    }

    private fun setupRecyclerView() {
        val imageLoader = ImageLoader.Builder(requireContext())
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(requireContext())
                    .maxSizePercent(0.45)
                    .weakReferencesEnabled(true)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .crossfade(true)
            .allowHardware(false)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .build()

        adapter = MediaAdapter(imageLoader) { mediaItem ->
            // Handle media item click if needed
        }

        val spanCount = 3
        val spacingInDp = 3
        val spacingInPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            spacingInDp.toFloat(),
            resources.displayMetrics
        ).toInt()

        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), spanCount)
            this.adapter = this@MediaPickerBottomSheet.adapter
            setHasFixedSize(true)
            setItemViewCacheSize(25)
            addItemDecoration(GridSpacingItemDecoration(spanCount, spacingInPx))
        }

        val mediaItemDetailsLookup = MediaItemDetailsLookup(binding.recyclerView)
        val selectionTracker = SelectionTracker.Builder(
            "media-selection",
            binding.recyclerView,
            MediaItemKeyProvider(adapter),
            mediaItemDetailsLookup,
            StorageStrategy.createLongStorage()
        ).withSelectionPredicate(SelectionPredicates.createSelectAnything()).build()

        adapter.selectionTracker = selectionTracker

        selectionTracker.addObserver(object : SelectionTracker.SelectionObserver<Long>() {})
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mediaList.collectLatest { it ->
                    adapter.submitList(it)
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.spinnerContainer.setOnClickListener { anchorView ->
            if (folderList.isNotEmpty()) {
                showFolderPopup(anchorView, folderList)
            }
        }
    }

    private fun checkPermissionsAndLoadFolders() {
        if (hasMediaPermissions(requireContext())) {
            loadFolders()
        } else {
            requestPermissions()
        }
    }

    private fun showFolderPopup(anchor: View, folders: List<MediaFolder>) {
        folderPopupWindow?.dismiss()

        val imageLoader = ImageLoader.Builder(requireContext())
            .crossfade(true)
            .allowHardware(false)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .build()

        val popupAdapter = FolderPopupAdapter(folders, imageLoader) { selectedFolder ->
            binding.spinnerText.text = selectedFolder.name
            viewModel.loadMedia(
                bucketId = selectedFolder.bucketId,
                mediaType = when (selectedFolder.type) {
                    FolderType.ALL_MEDIA -> null
                    FolderType.IMAGES_ONLY -> MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    FolderType.VIDEOS_ONLY -> MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    FolderType.FOLDER -> null
                }
            )
            folderPopupWindow?.dismiss()
        }
        val inflater = LayoutInflater.from(requireContext())
        val popupViewBinding = PopupFolderListBinding.inflate(inflater, null, false)
        val recyclerView = popupViewBinding.folderRecyclerViewPopup

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = popupAdapter
        recyclerView.layoutAnimation =
            AnimationUtils.loadLayoutAnimation(context, R.anim.layout_fade_in)

        val screenWidth = resources.displayMetrics.widthPixels
        val popupWidth = screenWidth / 2
        val height = ViewGroup.LayoutParams.WRAP_CONTENT

        folderPopupWindow = PopupWindow(popupViewBinding.root, popupWidth, height, true)
        folderPopupWindow?.isOutsideTouchable = true
        folderPopupWindow?.elevation = 8f

        folderPopupWindow?.showAsDropDown(anchor, 0, 0)

        val popupContent = popupViewBinding.root
        popupContent.viewTreeObserver.addOnPreDrawListener(object :
            ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                popupContent.viewTreeObserver.removeOnPreDrawListener(this)

                val fullHeight = popupContent.measuredHeight
                popupContent.clipBounds = Rect(0, 0, popupContent.width, 0)

                val animator = ValueAnimator.ofInt(0, fullHeight).apply {
                    duration = 350
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { animation ->
                        val value = animation.animatedValue as Int
                        popupContent.clipBounds = Rect(0, 0, popupContent.width, value)
                    }
                }

                animator.start()
                return true
            }
        })
    }

    private fun loadFolders() {
        viewLifecycleOwner.lifecycleScope.launch {
            val folders = MediaLoader.Companion.getMediaFolderSummaries(requireContext())
            folderList.clear()
            folderList.addAll(folders)
        }
    }

    fun hasMediaPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_MEDIA_VIDEO
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissionLauncher.launch(permissions)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            loadFolders()
        } else {
            Toast.makeText(requireContext(), "يجب منح صلاحية الوصول للوسائط", Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onDestroyView() {
        folderPopupWindow?.dismiss()
        folderPopupWindow = null
        super.onDestroyView()
        _binding = null
    }
}
package dev.anonymous.media_picker_library.data.source

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import dev.anonymous.media_picker_library.R
import dev.anonymous.media_picker_library.config.FolderType
import dev.anonymous.media_picker_library.data.model.MediaFolder
import dev.anonymous.media_picker_library.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaLoader {
    companion object {
        private const val TAG = "MediaPagingSource"

        suspend fun getMediaFolderSummaries(context: Context): List<MediaFolder> =
            withContext(Dispatchers.IO) {
                // لهيكلة بيانات المجلدات أثناء التجميع: اسم المجلد ومعرف الحاوية كـ مفتاح،
                // والقيمة هي كائن لتخزين عدد العناصر وأول URI كصورة مصغرة.
                data class FolderAggregator(var count: Int = 0, var firstUri: Uri? = null)

                val foldersAggregatorMap = mutableMapOf<Pair<String, Long>, FolderAggregator>()

                // متغيرات لتتبع العدد الإجمالي والصور المصغرة للمجلدات الافتراضية
                var totalMediaCount = 0
                var firstMediaUri: Uri? = null
                var totalImageCount = 0
                var firstImageUri: Uri? = null
                var totalVideoCount = 0
                var firstVideoUri: Uri? = null

                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.MEDIA_TYPE,
                    MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.Files.FileColumns.BUCKET_ID,
                    MediaStore.Files.FileColumns.DATE_ADDED
                )

                val selection =
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
                val selectionArgs = arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                )

                // الفرز حسب تاريخ الإضافة تنازليًا مهم للحصول على أحدث ملف كصورة مصغرة
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

                val queryUri =
                    MediaStore.Files.getContentUri("external") // أو "external_primary" لـ API 29+

                context.contentResolver.query(
                    queryUri,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )
                    ?.use { cursor ->
                        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                        val mediaTypeIndex =
                            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                        val bucketNameIndex =
                            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                        val bucketIdIndex =
                            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idIndex)
                            val mediaType = cursor.getInt(mediaTypeIndex)
                            // استخدام موارد السلاسل للأسماء الافتراضية والغير معروفة سيكون أفضل للتعريب
                            val bucketName = cursor.getString(bucketNameIndex)
                                ?: context.getString(R.string.unknown_folder_name)
                            val bucketId = cursor.getLong(bucketIdIndex)

                            val contentUri = when (mediaType) {
                                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                                    ContentUris.withAppendedId(
                                        // يجب استخدام MediaStore.Images.Media.EXTERNAL_CONTENT_URI للصور
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        id
                                    )

                                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                                    ContentUris.withAppendedId(
                                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                        id
                                    )

                                else -> continue // تخطي أنواع الوسائط غير المعروفة
                            }

                            // تحديث العدد الإجمالي وأول URI للمجلدات الافتراضية
                            totalMediaCount++
                            if (firstMediaUri == null) {
                                firstMediaUri = contentUri
                            }

                            if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                                totalImageCount++
                                if (firstImageUri == null) {
                                    firstImageUri = contentUri
                                }
                            } else {
                                totalVideoCount++
                                if (firstVideoUri == null) {
                                    firstVideoUri = contentUri
                                }
                            }

                            // تجميع معلومات المجلدات الفعلية
                            val folderKey = bucketName to bucketId
                            val aggregator =
                                foldersAggregatorMap.getOrPut(folderKey) { FolderAggregator() }
                            aggregator.count++
                            // نظرًا للفرز حسب DATE_ADDED DESC، أول URI نواجهه لكل مجلد سيكون الأحدث
                            if (aggregator.firstUri == null) {
                                aggregator.firstUri = contentUri
                            }
                        }
                    }

                // إنشاء المجلدات الافتراضية
                val predefinedFolders = mutableListOf<MediaFolder>()


                val actualFolders =
                    foldersAggregatorMap.mapNotNull { (key, aggregator) -> // استخدام mapNotNull
                        aggregator.firstUri?.let { uri -> // فقط إذا لم يكن firstUri هو null
                            MediaFolder(
                                name = key.first,
                                bucketId = key.second,
                                mediaCount = aggregator.count,
                                thumbnailUri = uri, // الآن uri مؤكد أنها ليست null
                                type = FolderType.FOLDER
                            )
                        }
                    }

                // عند إنشاء predefinedFolders (مثال لمجلد "كل الوسائط")
                if (totalMediaCount > 0) {
                    firstMediaUri?.let { uri -> // فقط إذا لم يكن firstMediaUri هو null
                        predefinedFolders.add(
                            MediaFolder(
                                name = context.getString(R.string.all_media_folder_name),
                                mediaCount = totalMediaCount,
                                thumbnailUri = uri,
                                type = FolderType.ALL_MEDIA
                            )
                        )
                    } // يمكنك إضافة else هنا إذا أردت إنشاء مجلد بصورة مصغرة افتراضية
                }

                if (totalVideoCount > 0) {
                    firstVideoUri?.let { uri ->
                        predefinedFolders.add(
                            MediaFolder(
                                name = context.getString(R.string.videos_folder_name),
                                mediaCount = totalVideoCount,
                                thumbnailUri = uri,
                                type = FolderType.VIDEOS_ONLY
                            )
                        )
                    }
                }

                if (totalImageCount > 0) {
                    firstImageUri?.let { uri ->
                        predefinedFolders.add(
                            MediaFolder(
                                name = context.getString(R.string.images_folder_name),
                                mediaCount = totalImageCount,
                                thumbnailUri = uri,
                                type = FolderType.IMAGES_ONLY
                            )
                        )
                    }
                }

                return@withContext predefinedFolders + actualFolders
            }

        suspend fun fetchAllMediaItems(
            context: Context,
            bucketId: Long?,
            mediaTypeFilter: Int?
        ): List<MediaItem> {

            val selectionBuilder = StringBuilder()
            val selectionArgsList = mutableListOf<String>()

            if (mediaTypeFilter != null) {
                selectionBuilder.append("${MediaStore.Files.FileColumns.MEDIA_TYPE} = $mediaTypeFilter")
            } else {
                selectionBuilder.append("${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)")
                selectionArgsList.add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
                selectionArgsList.add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            }

            bucketId?.let {
                selectionBuilder.append(" AND ${MediaStore.MediaColumns.BUCKET_ID} = ? ")
                selectionArgsList.add(it.toString())
            }

            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Video.VideoColumns.DURATION
            )
            val selection = selectionBuilder.toString()
            val selectionArgs = if (selectionArgsList.isNotEmpty()) {
                selectionArgsList.toTypedArray()
            } else null
            val sortOrder = "${MediaStore.Files.FileColumns._ID} DESC"

            val mediaList = mutableListOf<MediaItem>()
            return withContext(Dispatchers.IO) {
                try {
                    val cursor =
                        context.contentResolver.query(
                            uri,
                            projection,
                            selection,
                            selectionArgs,
                            sortOrder
                        )

                    cursor?.use {
                        val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                        val typeCol =
                            it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                        val durationCol =
                            it.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)

                        while (it.moveToNext()) {
                            val id = it.getLong(idCol)
                            val type = it.getInt(typeCol)
                            val duration = it.getLong(durationCol)

                            val contentUri = when (type) {
                                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                                    ContentUris.withAppendedId(
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        id
                                    )

                                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                                    ContentUris.withAppendedId(
                                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                        id
                                    )

                                else -> continue // Skip unknown media types
                            }

                            val isVideo = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                            mediaList.add(
                                MediaItem(
                                    id = id,
                                    uri = contentUri,
                                    isVideo = isVideo,
                                    duration = duration
                                )
                            )
                        }
                    }
                    Log.d(TAG, "fetchAllMediaItems: mediaList size ${mediaList.size}")
                    mediaList
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading media", e)
                    emptyList()
                }
            }
        }

        fun getVideoThumbnail(context: Context, uri: Uri): Pair<Uri?, Bitmap?> {
            return try {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val bitmap = context.contentResolver.loadThumbnail(uri, Size(200, 200), null)
                    Pair(null, bitmap)
                } else {
                    @Suppress("DEPRECATION")
                    val thumbnailUri = ContentUris.withAppendedId(
                        MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
                        ContentUris.parseId(uri)
                    )
                    Pair(thumbnailUri, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(null, null)
            }
        }

    }
}
package com.pin.lever

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.pin.lever.Lever.LoadedFrom
import okio.Okio
import java.io.IOException

internal class MediaStoreRequestHandler(context: Context?) :
    ContentStreamRequestHandler(context!!) {
    override fun canHandleRequest(data: Request?): Boolean {
        val uri = data!!.uri
        return ContentResolver.SCHEME_CONTENT == uri!!.scheme && MediaStore.AUTHORITY == uri.authority
    }

    @Throws(IOException::class)
    override fun load(
        request: Request?,
        networkPolicy: Int
    ): Result? {
        val contentResolver = context.contentResolver
        val exifOrientation =
            getExifOrientation(contentResolver, request!!.uri)
        val mimeType = contentResolver.getType(request.uri)
        val isVideo = mimeType != null && mimeType.startsWith("video/")
        if (request.hasSize()) {
            val leverKind = getLeverKind(
                request.targetWidth,
                request.targetHeight
            )
            if (!isVideo && leverKind == LeverKind.FULL) {
                val source = Okio.source(getInputStream(request))
                return Result(
                    null,
                    source,
                    LoadedFrom.DISK,
                    exifOrientation
                )
            }
            val id = ContentUris.parseId(request.uri)
            val options =
                createBitmapOptions(request)
            options!!.inJustDecodeBounds = true
            calculateInSampleSize(
                request.targetWidth, request.targetHeight, leverKind.width,
                leverKind.height, options, request
            )
            val bitmap: Bitmap?
            bitmap =
                if (isVideo) {
                    // Since MediaStore doesn't provide the full screen kind thumbnail, we use the mini kind
// instead which is the largest thumbnail size can be fetched from MediaStore.
                    val kind =
                        if (leverKind == LeverKind.FULL) MediaStore.Video.Thumbnails.MINI_KIND else leverKind.androidKind
                    MediaStore.Video.Thumbnails.getThumbnail(contentResolver, id, kind, options)
                } else {
                    MediaStore.Images.Thumbnails.getThumbnail(
                        contentResolver,
                        id,
                        leverKind.androidKind,
                        options
                    )
                }
            if (bitmap != null) {
                return Result(
                    bitmap,
                    null,
                    LoadedFrom.DISK,
                    exifOrientation
                )
            }
        }
        val source = Okio.source(getInputStream(request))
        return Result(null, source, LoadedFrom.DISK, exifOrientation)
    }

    internal enum class LeverKind(val androidKind: Int, val width: Int, val height: Int) {
        MICRO(
            MediaStore.Images.Thumbnails.MICRO_KIND,
            96,
            96
        ),
        MINI(
            MediaStore.Images.Thumbnails.MINI_KIND,
            512,
            384
        ),
        FULL(MediaStore.Images.Thumbnails.FULL_SCREEN_KIND, -1, -1);

    }

    companion object {
        private val CONTENT_ORIENTATION = arrayOf(
            MediaStore.Images.ImageColumns.ORIENTATION
        )

        fun getLeverKind(targetWidth: Int, targetHeight: Int): LeverKind {
            if (targetWidth <= LeverKind.MICRO.width && targetHeight <= LeverKind.MICRO.height) {
                return LeverKind.MICRO
            } else if (targetWidth <= LeverKind.MINI.width && targetHeight <= LeverKind.MINI.height) {
                return LeverKind.MINI
            }
            return LeverKind.FULL
        }

        fun getExifOrientation(contentResolver: ContentResolver, uri: Uri?): Int {
            var cursor: Cursor? = null
            return try {
                cursor = contentResolver.query(
                    uri,
                    CONTENT_ORIENTATION,
                    null,
                    null,
                    null
                )
                if (cursor == null || !cursor.moveToFirst()) {
                    0
                } else cursor.getInt(0)
            } catch (ignored: RuntimeException) { // If the orientation column doesn't exist, assume no rotation.
                0
            } finally {
                cursor?.close()
            }
        }
    }
}
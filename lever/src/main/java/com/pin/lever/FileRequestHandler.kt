package com.pin.lever

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.support.media.ExifInterface
import com.pin.lever.Lever.LoadedFrom
import okio.Okio
import java.io.IOException

internal class FileRequestHandler(context: Context?) :
    ContentStreamRequestHandler(context!!) {
    override fun canHandleRequest(data: Request?): Boolean {
        return ContentResolver.SCHEME_FILE == data!!.uri!!.scheme
    }

    @Throws(IOException::class)
    override fun load(
        request: Request?,
        networkPolicy: Int
    ): Result? {
        val source = Okio.source(getInputStream(request))
        return Result(
            null,
            source,
            LoadedFrom.DISK,
            getFileExifRotation(request!!.uri)
        )
    }

    companion object {
        @Throws(IOException::class)
        fun getFileExifRotation(uri: Uri?): Int {
            val exifInterface =
                ExifInterface(uri!!.path!!)
            return exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
    }
}
package com.pin.lever

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pin.lever.Lever.LoadedFrom
import com.pin.lever.Utils.getResourceId
import com.pin.lever.Utils.getResources
import java.io.IOException

internal class ResourceRequestHandler(private val context: Context) :
    RequestHandler() {
    override fun canHandleRequest(data: Request?): Boolean {
        return if (data!!.resourceId != 0) {
            true
        } else ContentResolver.SCHEME_ANDROID_RESOURCE == data.uri!!.scheme
    }

    @Throws(IOException::class)
    override fun load(
        request: Request?,
        networkPolicy: Int
    ): Result? {
        val res = getResources(context, request!!)
        val id = getResourceId(res, request)
        return Result(
            decodeResource(
                res,
                id,
                request
            ), LoadedFrom.DISK
        )
    }

    companion object {
        private fun decodeResource(
            resources: Resources,
            id: Int,
            data: Request
        ): Bitmap {
            val options = createBitmapOptions(data)
            if (requiresInSampleSize(options)) {
                BitmapFactory.decodeResource(resources, id, options)
                calculateInSampleSize(
                    data.targetWidth,
                    data.targetHeight,
                    options!!,
                    data
                )
            }
            return BitmapFactory.decodeResource(resources, id, options)
        }
    }

}
package com.pin.lever

import android.content.ContentResolver
import android.content.Context
import com.pin.lever.Lever.LoadedFrom
import okio.Okio
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

internal open class ContentStreamRequestHandler(val context: Context) :
    RequestHandler() {
    override fun canHandleRequest(data: Request?): Boolean {
        return ContentResolver.SCHEME_CONTENT == data!!.uri!!.scheme
    }

    @Throws(IOException::class)
    override fun load(
        request: Request?,
        networkPolicy: Int
    ): Result? {
        val source = Okio.source(getInputStream(request))
        return Result(source, LoadedFrom.DISK)
    }

    @Throws(FileNotFoundException::class)
    fun getInputStream(request: Request?): InputStream {
        val contentResolver = context.contentResolver
        return contentResolver.openInputStream(request!!.uri!!)
    }

}
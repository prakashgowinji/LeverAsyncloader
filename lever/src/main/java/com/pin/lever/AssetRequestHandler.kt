package com.pin.lever

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetManager
import com.pin.lever.Lever.LoadedFrom
import okio.Okio
import java.io.IOException

internal class AssetRequestHandler(private val context: Context) :
    RequestHandler() {
    private val lock = Any()
    private var assetManager: AssetManager? = null
    override fun canHandleRequest(data: Request?): Boolean {
        val uri = data!!.uri
        return ContentResolver.SCHEME_FILE == uri!!.scheme && !uri.pathSegments.isEmpty() && ANDROID_ASSET == uri.pathSegments[0]
    }

    @Throws(IOException::class)
    override fun load(
        request: Request?,
        networkPolicy: Int
    ): Result? {
        if (assetManager == null) {
            synchronized(lock) {
                if (assetManager == null) {
                    assetManager = context.assets
                }
            }
        }
        val source =
            Okio.source(assetManager!!.open(getFilePath(request)))
        return Result(source, LoadedFrom.DISK)
    }

    companion object {
        protected const val ANDROID_ASSET = "android_asset"
        private const val ASSET_PREFIX_LENGTH =
            (ContentResolver.SCHEME_FILE + ":///" + ANDROID_ASSET + "/").length

        fun getFilePath(request: Request?): String {
            return request!!.uri.toString()
                .substring(ASSET_PREFIX_LENGTH)
        }
    }

}
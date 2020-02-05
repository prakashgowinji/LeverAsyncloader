package com.pin.lever

import android.net.NetworkInfo
import com.pin.lever.Lever.LoadedFrom
import okhttp3.CacheControl
import java.io.IOException

internal class NetworkRequestHandler(
    private val downloader: Downloader,
    private val stats: Stats
) : RequestHandler() {
    override fun canHandleRequest(data: Request?): Boolean {
        val scheme = data!!.uri!!.scheme
        return SCHEME_HTTP == scheme || SCHEME_HTTPS == scheme
    }

    @Throws(IOException::class)
    override fun load(
        request: Request?,
        networkPolicy: Int
    ): Result? {
        val downloaderRequest =
            createRequest(request, networkPolicy)
        val response = downloader.load(downloaderRequest)
        val body = response.body()
        if (!response.isSuccessful) {
            body!!.close()
            throw ResponseException(response.code(), request!!.networkPolicy)
        }
        // Cache response is only null when the response comes fully from the network. Both completely
// cached and conditionally cached responses will have a non-null cache response.
        val loadedFrom =
            if (response.cacheResponse() == null) LoadedFrom.NETWORK else LoadedFrom.DISK
        // Sometimes response content length is zero when requests are being replayed. Haven't found
// root cause to this but retrying the request seems safe to do so.
        if (loadedFrom == LoadedFrom.DISK && body!!.contentLength() == 0L) {
            body.close()
            throw ContentLengthException("Received response with 0 content-length header.")
        }
        if (loadedFrom == LoadedFrom.NETWORK && body!!.contentLength() > 0) {
            stats.dispatchDownloadFinished(body.contentLength())
        }
        return Result(body!!.source(), loadedFrom)
    }

    override val retryCount: Int
        get() = 2

    override fun shouldRetry(
        airplaneMode: Boolean,
        info: NetworkInfo?
    ): Boolean {
        return info == null || info.isConnected
    }

    override fun supportsReplay(): Boolean {
        return true
    }

    internal class ContentLengthException(message: String?) :
        IOException(message)

    internal class ResponseException(val code: Int, val networkPolicy: Int) :
        IOException("HTTP $code")

    companion object {
        private const val SCHEME_HTTP = "http"
        private const val SCHEME_HTTPS = "https"
        private fun createRequest(
            request: Request?,
            networkPolicy: Int
        ): okhttp3.Request {
            var cacheControl: CacheControl? = null
            if (networkPolicy != 0) {
                cacheControl = if (NetworkPolicy.isOfflineOnly(networkPolicy)) {
                    CacheControl.FORCE_CACHE
                } else {
                    val builder = CacheControl.Builder()
                    if (!NetworkPolicy.shouldReadFromDiskCache(networkPolicy)) {
                        builder.noCache()
                    }
                    if (!NetworkPolicy.shouldWriteToDiskCache(networkPolicy)) {
                        builder.noStore()
                    }
                    builder.build()
                }
            }
            val builder =
                okhttp3.Request.Builder().url(request!!.uri.toString())
            if (cacheControl != null) {
                builder.cacheControl(cacheControl)
            }
            return builder.build()
        }
    }

}
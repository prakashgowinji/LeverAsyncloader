package com.pin.lever

import android.content.Context
import android.support.annotation.VisibleForTesting
import com.pin.lever.Utils.calculateDiskCacheSize
import com.pin.lever.Utils.createDefaultCacheDir
import okhttp3.*
import okhttp3.Cache
import okhttp3.Request
import java.io.File
import java.io.IOException

/** A [Downloader] which uses OkHttp to download images.  */
class OkHttp3Downloader : Downloader {
    @VisibleForTesting
    val client: Call.Factory
    private val cache: Cache?
    private var sharedClient = true

    /**
     * Create new downloader that uses OkHttp. This will install an image cache into your application
     * cache directory.
     */
    constructor(context: Context?) : this(
        createDefaultCacheDir(
            context!!
        )
    ) {
    }

    /**
     * Create new downloader that uses OkHttp. This will install an image cache into your application
     * cache directory.
     *
     * @param maxSize The size limit for the cache.
     */
    constructor(
        context: Context?,
        maxSize: Long
    ) : this(createDefaultCacheDir(context!!), maxSize) {
    }
    /**
     * Create new downloader that uses OkHttp. This will install an image cache into the specified
     * directory.
     *
     * @param cacheDir The directory in which the cache should be stored
     * @param maxSize The size limit for the cache.
     */
    /**
     * Create new downloader that uses OkHttp. This will install an image cache into the specified
     * directory.
     *
     * @param cacheDir The directory in which the cache should be stored
     */
    @JvmOverloads
    constructor(
        cacheDir: File?,
        maxSize: Long = calculateDiskCacheSize(cacheDir!!)
    ) : this(OkHttpClient.Builder().cache(Cache(cacheDir, maxSize)).build()) {
        sharedClient = false
    }

    /**
     * Create a new downloader that uses the specified OkHttp instance. A response cache will not be
     * automatically configured.
     */
    constructor(client: OkHttpClient) {
        this.client = client
        cache = client.cache()
    }

    /** Create a new downloader that uses the specified [Call.Factory] instance.  */
    constructor(client: Call.Factory) {
        this.client = client
        cache = null
    }

    @Throws(IOException::class)
    override fun load(request: Request): Response {
        return client.newCall(request).execute()
    }

    override fun shutdown() {
        if (!sharedClient && cache != null) {
            try {
                cache.close()
            } catch (ignored: IOException) {
            }
        }
    }
}
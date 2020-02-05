package com.pin.lever

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import com.pin.lever.Utils.KEY_SEPARATOR
import com.pin.lever.Utils.calculateMemoryCacheSize
import com.pin.lever.Utils.getBitmapBytes

/** A memory cache which uses a least-recently used eviction policy.  */
class LruCache(maxByteCount: Int) : Cache {
    val cache: LruCache<String, BitmapAndSize>

    /** Create a cache using an appropriate portion of the available RAM as the maximum size.  */
    constructor(context: Context) : this(
        calculateMemoryCacheSize(
            context
        )
    ) {
    }

    override fun get(key: String?): Bitmap? {
        val bitmapAndSize = cache[key]
        return bitmapAndSize?.bitmap
    }

    override fun set(key: String?, bitmap: Bitmap?) {
        if (key == null || bitmap == null) {
            throw NullPointerException("key == null || bitmap == null")
        }
        val byteCount = getBitmapBytes(bitmap)
        // If the bitmap is too big for the cache, don't even attempt to store it. Doing so will cause
// the cache to be cleared. Instead just evict an existing element with the same key if it
// exists.
        if (byteCount > maxSize()) {
            cache.remove(key)
            return
        }
        cache.put(key, BitmapAndSize(bitmap, byteCount))
    }

    override fun size(): Int {
        return cache.size()
    }

    override fun maxSize(): Int {
        return cache.maxSize()
    }

    override fun clear() {
        cache.evictAll()
    }

    override fun clearKeyUri(uri: String?) { // Keys are prefixed with a URI followed by '\n'.
        for (key in cache.snapshot().keys) {
            if (key.startsWith(uri!!)
                && key.length > uri.length && key[uri.length] == KEY_SEPARATOR
            ) {
                cache.remove(key)
            }
        }
    }

    /** Returns the number of times [.get] returned a value.  */
    fun hitCount(): Int {
        return cache.hitCount()
    }

    /** Returns the number of times [.get] returned `null`.  */
    fun missCount(): Int {
        return cache.missCount()
    }

    /** Returns the number of times [.set] was called.  */
    fun putCount(): Int {
        return cache.putCount()
    }

    /** Returns the number of values that have been evicted.  */
    fun evictionCount(): Int {
        return cache.evictionCount()
    }

    class BitmapAndSize(val bitmap: Bitmap, val byteCount: Int)

    /** Create a cache with a given maximum size in bytes.  */
    init {
        cache = object : LruCache<String, BitmapAndSize>(maxByteCount) {
            override fun sizeOf(key: String?, value: BitmapAndSize): Int {
                return value.byteCount
            }
        }
    }
}
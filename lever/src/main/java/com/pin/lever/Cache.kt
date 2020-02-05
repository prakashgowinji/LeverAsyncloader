package com.pin.lever

import android.graphics.Bitmap

interface Cache {
    /** Retrieve an image for the specified `key` or `null`.  */
    operator fun get(key: String?): Bitmap?

    /** Store an image in the cache for the specified `key`.  */
    operator fun set(key: String?, bitmap: Bitmap?)

    /** Returns the current size of the cache in bytes.  */
    fun size(): Int

    /** Returns the maximum size in bytes that the cache can hold.  */
    fun maxSize(): Int

    /** Clears the cache.  */
    fun clear()

    /** Remove items whose key is prefixed with `keyPrefix`.  */
    fun clearKeyUri(keyPrefix: String?)

    companion object {
        /** A cache which does not store any values.  */
        val NONE: Cache = object : Cache {
            override fun get(key: String?): Bitmap? {
                return null
            }

            override fun set(key: String?, bitmap: Bitmap?) { // Ignore.
            }

            override fun size(): Int {
                return 0
            }

            override fun maxSize(): Int {
                return 0
            }

            override fun clear() {}
            override fun clearKeyUri(keyPrefix: String?) {}
        }
    }
}
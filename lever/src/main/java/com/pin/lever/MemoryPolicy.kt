package com.pin.lever

enum class MemoryPolicy(val index: Int) {
    /** Skips memory cache lookup when processing a request.  */
    NO_CACHE(1),
    /**
     * Skips storing the final result into memory cache. Useful for one-off requests
     * to avoid evicting other bitmaps from the cache.
     */
    NO_STORE(1 shl 1);

    companion object {
        @JvmStatic
        fun shouldReadFromMemoryCache(memoryPolicy: Int): Boolean {
            return memoryPolicy and NO_CACHE.index == 0
        }

        @JvmStatic
        fun shouldWriteToMemoryCache(memoryPolicy: Int): Boolean {
            return memoryPolicy and NO_STORE.index == 0
        }
    }

}
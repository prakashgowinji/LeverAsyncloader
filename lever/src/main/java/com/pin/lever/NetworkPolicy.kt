package com.pin.lever

enum class NetworkPolicy(val index: Int) {
    /** Skips checking the disk cache and forces loading through the network.  */
    NO_CACHE(1 shl 0),
    /**
     * Skips storing the result into the disk cache.
     */
    NO_STORE(1 shl 1),
    /** Forces the request through the disk cache only, skipping network.  */
    OFFLINE(1 shl 2);

    companion object {
        fun shouldReadFromDiskCache(networkPolicy: Int): Boolean {
            return networkPolicy and NO_CACHE.index == 0
        }

        fun shouldWriteToDiskCache(networkPolicy: Int): Boolean {
            return networkPolicy and NO_STORE.index == 0
        }

        @JvmStatic
        fun isOfflineOnly(networkPolicy: Int): Boolean {
            return networkPolicy and OFFLINE.index != 0
        }
    }

}
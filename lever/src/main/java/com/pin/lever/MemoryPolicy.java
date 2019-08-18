package com.pin.lever;

public enum MemoryPolicy {

    /** Skips memory cache lookup when processing a request. */
    NO_CACHE(1),
    /**
     * Skips storing the final result into memory cache. Useful for one-off requests
     * to avoid evicting other bitmaps from the cache.
     */
    NO_STORE(1 << 1);

    static boolean shouldReadFromMemoryCache(int memoryPolicy) {
        return (memoryPolicy & MemoryPolicy.NO_CACHE.index) == 0;
    }

    static boolean shouldWriteToMemoryCache(int memoryPolicy) {
        return (memoryPolicy & MemoryPolicy.NO_STORE.index) == 0;
    }

    final int index;

    MemoryPolicy(int index) {
        this.index = index;
    }
}


package com.pin.lever;

@SuppressWarnings("PointlessBitwiseExpression")
public enum NetworkPolicy {

    /** Skips checking the disk cache and forces loading through the network. */
    NO_CACHE(1 << 0),

    /**
     * Skips storing the result into the disk cache.
     */
    NO_STORE(1 << 1),

    /** Forces the request through the disk cache only, skipping network. */
    OFFLINE(1 << 2);

    public static boolean shouldReadFromDiskCache(int networkPolicy) {
        return (networkPolicy & NetworkPolicy.NO_CACHE.index) == 0;
    }

    public static boolean shouldWriteToDiskCache(int networkPolicy) {
        return (networkPolicy & NetworkPolicy.NO_STORE.index) == 0;
    }

    public static boolean isOfflineOnly(int networkPolicy) {
        return (networkPolicy & NetworkPolicy.OFFLINE.index) != 0;
    }

    final int index;

    NetworkPolicy(int index) {
        this.index = index;
    }
}

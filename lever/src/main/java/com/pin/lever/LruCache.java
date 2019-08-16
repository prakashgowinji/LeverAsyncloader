package com.pin.lever;


import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import static com.pin.lever.Utils.KEY_SEPARATOR;

/** A memory cache which uses a least-recently used eviction policy. */
public final class LruCache implements Cache {
    final android.util.LruCache<String, LruCache.BitmapAndSize> cache;

    /** Create a cache using an appropriate portion of the available RAM as the maximum size. */
    public LruCache(@NonNull Context context) {
        this(Utils.calculateMemoryCacheSize(context));
    }

    /** Create a cache with a given maximum size in bytes. */
    public LruCache(int maxByteCount) {
        cache = new android.util.LruCache<String, LruCache.BitmapAndSize>(maxByteCount) {
            @Override protected int sizeOf(String key, BitmapAndSize value) {
                return value.byteCount;
            }
        };
    }

    @Nullable
    @Override public Bitmap get(@NonNull String key) {
        BitmapAndSize bitmapAndSize = cache.get(key);
        return bitmapAndSize != null ? bitmapAndSize.bitmap : null;
    }

    @Override public void set(@NonNull String key, @NonNull Bitmap bitmap) {
        if (key == null || bitmap == null) {
            throw new NullPointerException("key == null || bitmap == null");
        }

        int byteCount = Utils.getBitmapBytes(bitmap);

        // If the bitmap is too big for the cache, don't even attempt to store it. Doing so will cause
        // the cache to be cleared. Instead just evict an existing element with the same key if it
        // exists.
        if (byteCount > maxSize()) {
            cache.remove(key);
            return;
        }

        cache.put(key, new BitmapAndSize(bitmap, byteCount));
    }

    @Override public int size() {
        return cache.size();
    }

    @Override public int maxSize() {
        return cache.maxSize();
    }

    @Override public void clear() {
        cache.evictAll();
    }

    @Override public void clearKeyUri(String uri) {
        // Keys are prefixed with a URI followed by '\n'.
        for (String key : cache.snapshot().keySet()) {
            if (key.startsWith(uri)
                    && key.length() > uri.length()
                    && key.charAt(uri.length()) == KEY_SEPARATOR) {
                cache.remove(key);
            }
        }
    }

    /** Returns the number of times {@link #get} returned a value. */
    public int hitCount() {
        return cache.hitCount();
    }

    /** Returns the number of times {@link #get} returned {@code null}. */
    public int missCount() {
        return cache.missCount();
    }

    /** Returns the number of times {@link #set(String, Bitmap)} was called. */
    public int putCount() {
        return cache.putCount();
    }

    /** Returns the number of values that have been evicted. */
    public int evictionCount() {
        return cache.evictionCount();
    }

    static final class BitmapAndSize {
        final Bitmap bitmap;
        final int byteCount;

        BitmapAndSize(Bitmap bitmap, int byteCount) {
            this.bitmap = bitmap;
            this.byteCount = byteCount;
        }
    }
}

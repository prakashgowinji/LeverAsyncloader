package com.pin.lever;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** A {@link Downloader} which uses OkHttp to download images. */
public final class OkHttp3Downloader implements Downloader {
    @VisibleForTesting
    final Call.Factory client;
    private final okhttp3.Cache cache;
    private boolean sharedClient = true;

    /**
     * Create new downloader that uses OkHttp. This will install an image cache into your application
     * cache directory.
     */
    public OkHttp3Downloader(final Context context) {
        this(Utils.createDefaultCacheDir(context));
    }

    /**
     * Create new downloader that uses OkHttp. This will install an image cache into the specified
     * directory.
     *
     * @param cacheDir The directory in which the cache should be stored
     */
    public OkHttp3Downloader(final File cacheDir) {
        this(cacheDir, Utils.calculateDiskCacheSize(cacheDir));
    }

    /**
     * Create new downloader that uses OkHttp. This will install an image cache into your application
     * cache directory.
     *
     * @param maxSize The size limit for the cache.
     */
    public OkHttp3Downloader(final Context context, final long maxSize) {
        this(Utils.createDefaultCacheDir(context), maxSize);
    }

    /**
     * Create new downloader that uses OkHttp. This will install an image cache into the specified
     * directory.
     *
     * @param cacheDir The directory in which the cache should be stored
     * @param maxSize The size limit for the cache.
     */
    public OkHttp3Downloader(final File cacheDir, final long maxSize) {
        this(new OkHttpClient.Builder().cache(new Cache(cacheDir, maxSize)).build());
        sharedClient = false;
    }

    /**
     * Create a new downloader that uses the specified OkHttp instance. A response cache will not be
     * automatically configured.
     */
    public OkHttp3Downloader(OkHttpClient client) {
        this.client = client;
        this.cache = client.cache();
    }

    /** Create a new downloader that uses the specified {@link Call.Factory} instance. */
    public OkHttp3Downloader(Call.Factory client) {
        this.client = client;
        this.cache = null;
    }

    @NonNull
    @Override public Response load(@NonNull Request request) throws IOException {
        return client.newCall(request).execute();
    }

    @Override public void shutdown() {
        if (!sharedClient && cache != null) {
            try {
                cache.close();
            } catch (IOException ignored) {
            }
        }
    }
}
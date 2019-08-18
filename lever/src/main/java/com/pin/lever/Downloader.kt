package com.pin.lever

import java.io.IOException
import okhttp3.Response

interface Downloader {
    /**
     * Download the specified image `url` from the internet.
     *
     * @throws IOException if the requested URL cannot successfully be loaded.
     */
    @Throws(IOException::class)
    fun load(request: okhttp3.Request): Response

    /**
     * Allows to perform a clean up for this [Downloader] including closing the disk cache and
     * other resources.
     */
    fun shutdown()
}
package com.pin.lever

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.NetworkInfo
import com.pin.lever.Lever.LoadedFrom
import com.pin.lever.Utils.checkNotNull
import okio.Source
import java.io.IOException
import kotlin.math.floor
import kotlin.math.min

/**
 * `RequestHandler` allows you to extend Lever to load images in ways that are not
 * supported by default in the library.
 *
 *
 * <h2>Usage</h2>
 * `RequestHandler` must be subclassed to be used. You will have to override two methods
 * ([.canHandleRequest] and [.load]) with your custom logic to
 * load images.
 *
 *
 * You should then register your [RequestHandler] using
 * [Lever.Builder.addRequestHandler]
 *
 *
 * **Note:** This is a beta feature. The API is subject to change in a backwards incompatible
 * way at any time.
 *
 * @see Lever.Builder.addRequestHandler
 */
abstract class RequestHandler {
    /**
     * [Result] represents the result of a [.load] call in a
     * [RequestHandler].
     *
     * @see RequestHandler
     *
     * @see .load
     */
    class Result internal constructor(
        bitmap: Bitmap?,
        source: Source?,
        loadedFrom: LoadedFrom,
        exifOrientation: Int
    ) {
        /**
         * Returns the resulting [Lever.LoadedFrom] generated from a
         * [.load] call.
         */
        val loadedFrom: LoadedFrom
        /** The loaded [Bitmap]. Mutually exclusive with [.getSource].  */
        val bitmap: Bitmap?
        /** A stream of image data. Mutually exclusive with [.getBitmap].  */
        val source: Source?
        /**
         * Returns the resulting EXIF orientation generated from a [.load] call.
         * This is only accessible to built-in RequestHandlers.
         */
        val exifOrientation: Int

        constructor(bitmap: Bitmap, loadedFrom: LoadedFrom) : this(
            checkNotNull<Bitmap>(
                bitmap,
                "bitmap == null"
            ), null, loadedFrom, 0
        ) {
        }

        constructor(source: Source, loadedFrom: LoadedFrom) : this(
            null,
            checkNotNull<Source>(source, "source == null"),
            loadedFrom,
            0
        ) {
        }

        init {
            if (bitmap != null == (source != null)) {
                throw AssertionError()
            }
            this.bitmap = bitmap
            this.source = source
            this.loadedFrom = checkNotNull(loadedFrom, "loadedFrom == null")
            this.exifOrientation = exifOrientation
        }
    }

    /**
     * Whether or not this [RequestHandler] can handle a request with the given [Request].
     */
    abstract fun canHandleRequest(data: Request?): Boolean

    /**
     * Loads an image for the given [Request].
     *
     * @param request the data from which the image should be resolved.
     * @param networkPolicy the [NetworkPolicy] for this request.
     */
    @Throws(IOException::class)
    abstract fun load(
        request: Request?,
        networkPolicy: Int
    ): Result?

    open val retryCount: Int
        get() = 0

    open fun shouldRetry(airplaneMode: Boolean, info: NetworkInfo?): Boolean {
        return false
    }

    open fun supportsReplay(): Boolean {
        return false
    }

    companion object {
        /**
         * Lazily create [BitmapFactory.Options] based in given
         * [Request], only instantiating them if needed.
         */
        @JvmStatic
        fun createBitmapOptions(data: Request): BitmapFactory.Options? {
            val justBounds = data.hasSize()
            val hasConfig = data.config != null
            var options: BitmapFactory.Options? = null
            if (justBounds || hasConfig || data.purgeable) {
                options = BitmapFactory.Options()
                options.inJustDecodeBounds = justBounds
                options.inInputShareable = data.purgeable
                options.inPurgeable = data.purgeable
                if (hasConfig) {
                    options.inPreferredConfig = data.config
                }
            }
            return options
        }

        @JvmStatic
        fun requiresInSampleSize(options: BitmapFactory.Options?): Boolean {
            return options != null && options.inJustDecodeBounds
        }

        fun calculateInSampleSize(
            reqWidth: Int, reqHeight: Int, options: BitmapFactory.Options,
            request: Request
        ) {
            calculateInSampleSize(
                reqWidth, reqHeight, options.outWidth, options.outHeight, options,
                request
            )
        }

        @JvmStatic
        fun calculateInSampleSize(
            reqWidth: Int, reqHeight: Int, width: Int, height: Int,
            options: BitmapFactory.Options, request: Request
        ) {
            var sampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                val heightRatio: Int
                val widthRatio: Int
                if (reqHeight == 0) {
                    sampleSize = floor(
                        width.toFloat() / reqWidth
                    ).toInt()
                } else if (reqWidth == 0) {
                    sampleSize = floor(
                        height.toFloat() / reqHeight
                    ).toInt()
                } else {
                    heightRatio = floor(
                        height.toFloat() / reqHeight
                    ).toInt()
                    widthRatio = floor(
                        width.toFloat() / reqWidth
                    ).toInt()
                    sampleSize = if (request.centerInside) Math.max(
                        heightRatio,
                        widthRatio
                    ) else min(heightRatio, widthRatio)
                }
            }
            options.inSampleSize = sampleSize
            options.inJustDecodeBounds = false
        }
    }
}
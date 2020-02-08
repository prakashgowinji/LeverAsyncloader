package com.pin.lever

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.NetworkInfo
import android.os.Build
import android.support.media.ExifInterface
import android.view.Gravity
import com.pin.lever.Lever.LoadedFrom
import com.pin.lever.MemoryPolicy.Companion.shouldReadFromMemoryCache
import com.pin.lever.NetworkPolicy.Companion.isOfflineOnly
import com.pin.lever.NetworkRequestHandler.ResponseException
import com.pin.lever.RequestHandler.Companion.calculateInSampleSize
import com.pin.lever.RequestHandler.Companion.createBitmapOptions
import com.pin.lever.RequestHandler.Companion.requiresInSampleSize
import com.pin.lever.Utils.OWNER_HUNTER
import com.pin.lever.Utils.VERB_DECODED
import com.pin.lever.Utils.VERB_EXECUTING
import com.pin.lever.Utils.VERB_JOINED
import com.pin.lever.Utils.VERB_REMOVED
import com.pin.lever.Utils.VERB_TRANSFORMED
import com.pin.lever.Utils.getLogIdsForHunter
import com.pin.lever.Utils.isWebPFile
import com.pin.lever.Utils.log
import okio.Okio
import okio.Source
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

class BitmapHunter private constructor(
    lever: Lever,
    dispatcher: Dispatcher,
    cache: Cache,
    stats: Stats,
    action: Action<*>,
    requestHandler: RequestHandler
) : Runnable {
    val sequence: Int
    val lever: Lever
    val dispatcher: Dispatcher
    val cache: Cache
    val stats: Stats
    val key: String
    val data: Request
    val memoryPolicy: Int
    var networkPolicy: Int
    val requestHandler: RequestHandler
    var action: Action<*>?
    var actionsList:MutableList<Action<*>>? = null
    var result: Bitmap? = null
    var future: Future<*>? = null
    var loadedFrom: LoadedFrom? = null
    var exception: Exception? = null
    var exifOrientation:Int = 0 // Determined during decoding of original resource. = 0
    var retryCount: Int
    var priority: Lever.Priority
    override fun run() {
        try {
            updateThreadName(data)
            if (lever.isLoggingEnabled) {
                log(OWNER_HUNTER, VERB_EXECUTING, getLogIdsForHunter(this))
            }
            result = hunt()
            if (result == null) {
                dispatcher.dispatchFailed(this)
            } else {
                dispatcher.dispatchComplete(this)
            }
        } catch (e: ResponseException) {
            if (!isOfflineOnly(e.networkPolicy) || e.code != 504) {
                exception = e
            }
            dispatcher.dispatchFailed(this)
        } catch (e: IOException) {
            exception = e
            dispatcher.dispatchRetry(this)
        } catch (e: OutOfMemoryError) {
            val writer = StringWriter()
            stats.createSnapshot().dump(PrintWriter(writer))
            exception = RuntimeException(writer.toString(), e)
            dispatcher.dispatchFailed(this)
        } catch (e: Exception) {
            exception = e
            dispatcher.dispatchFailed(this)
        } finally {
            Thread.currentThread().name = Utils.THREAD_IDLE_NAME
        }
    }

    @Throws(IOException::class)
    fun hunt(): Bitmap? {
        var bitmap: Bitmap? = null
        if (shouldReadFromMemoryCache(memoryPolicy)) {
            bitmap = cache[key]
            if (bitmap != null) {
                stats.dispatchCacheHit()
                loadedFrom = LoadedFrom.MEMORY
                if (lever.isLoggingEnabled) {
                    log(OWNER_HUNTER, VERB_DECODED, data.logId(), "from cache")
                }
                return bitmap
            }
        }
        networkPolicy = if (retryCount == 0) NetworkPolicy.OFFLINE.index else networkPolicy
        val result = requestHandler.load(data, networkPolicy)
        if (result != null) {
            loadedFrom = result.loadedFrom
            exifOrientation = result.exifOrientation
            bitmap = result.bitmap
            // If there was no Bitmap then we need to decode it from the stream.
            if (bitmap == null) {
                val source = result.source
                bitmap = try {
                    decodeStream(source, data)
                } finally {
                    try {
                        source!!.close()
                    } catch (ignored: IOException) {
                    }
                }
            }
        }
        if (bitmap != null) {
            if (lever.isLoggingEnabled) {
                log(OWNER_HUNTER, VERB_DECODED, data.logId())
            }
            stats.dispatchBitmapDecoded(bitmap)
            if (data.needsTransformation() || exifOrientation != 0) {
                synchronized(DECODE_LOCK) {
                    if (data.needsMatrixTransform() || exifOrientation != 0) {
                        bitmap =
                            transformResult(data, bitmap!!, exifOrientation)
                        if (lever.isLoggingEnabled) {
                            log(OWNER_HUNTER, VERB_TRANSFORMED, data.logId())
                        }
                    }
                    if (data.hasCustomTransformations()) {
                        bitmap = applyCustomTransformations(
                            data.transformations,
                            bitmap
                        )
                        if (lever.isLoggingEnabled) {
                            log(
                                OWNER_HUNTER,
                                VERB_TRANSFORMED,
                                data.logId(),
                                "from custom transformations"
                            )
                        }
                    }
                }
                if (bitmap != null) {
                    stats.dispatchBitmapTransformed(bitmap!!)
                }
            }
        }
        return bitmap
    }

    fun attach(action: Action<*>) {
        val loggingEnabled = lever.isLoggingEnabled
        val request = action.request
        if (this.action == null) {
            this.action = action
            if (loggingEnabled) {
                if (actionsList == null || actionsList!!.isEmpty()) {
                    log(OWNER_HUNTER, VERB_JOINED, request!!.logId(), "to empty hunter")
                } else {
                    log(OWNER_HUNTER, VERB_JOINED, request!!.logId(), getLogIdsForHunter(this, "to "))
                }
            }
            return
        }
        if (actionsList == null) {
            actionsList = mutableListOf()
        }
        actionsList!!.add(action)
        if (loggingEnabled) {
            log(OWNER_HUNTER, VERB_JOINED, request!!.logId(), getLogIdsForHunter(this, "to "))
        }
        val actionPriority = action.priority
        if (actionPriority.ordinal > priority.ordinal) {
            priority = actionPriority
        }
    }

    fun detach(action: Action<*>) {
        var detached = false
        if (this.action === action) {
            this.action = null
            detached = true
        } else if (actionsList != null) {
            detached = actionsList!!.remove(action)
        }
        // The action being detached had the highest priority. Update this
// hunter's priority with the remaining actions.
        if (detached && action.priority === priority) {
            priority = computeNewPriority()
        }
        if (lever.isLoggingEnabled) {
            log(
                OWNER_HUNTER,
                VERB_REMOVED,
                action.request!!.logId(),
                getLogIdsForHunter(this, "from ")
            )
        }
    }

    private fun computeNewPriority(): Lever.Priority {
        var newPriority = Lever.Priority.LOW
        val hasMultiple = actionsList != null && !actionsList!!.isEmpty()
        val hasAny = action != null || hasMultiple
        // Hunter has no requests, low priority.
        if (!hasAny) {
            return newPriority
        }
        if (action != null) {
            newPriority = action!!.priority
        }
        if (hasMultiple) {
            var i = 0
            val n = actionsList!!.size
            while (i < n) {
                val actionPriority = actionsList!![i].priority
                if (actionPriority.ordinal > newPriority.ordinal) {
                    newPriority = actionPriority
                }
                i++
            }
        }
        return newPriority
    }

    fun cancel(): Boolean {
        return (action == null && (actionsList == null || actionsList!!.isEmpty())
                && future != null && future!!.cancel(false))
    }

    val isCancelled: Boolean
        get() = future != null && future!!.isCancelled

    fun shouldRetry(airplaneMode: Boolean, info: NetworkInfo?): Boolean {
        val hasRetries = retryCount > 0
        if (!hasRetries) {
            return false
        }
        retryCount--
        return requestHandler.shouldRetry(airplaneMode, info)
    }

    fun supportsReplay(): Boolean {
        return requestHandler.supportsReplay()
    }

    fun getActions(): List<Action<*>>? {
        return actionsList
    }

    companion object {
        /**
         * Global lock for bitmap decoding to ensure that we are only decoding one at a time. Since
         * this will only ever happen in background threads we help avoid excessive memory thrashing as
         * well as potential OOMs. Shamelessly stolen from Volley.
         */
        private val DECODE_LOCK = Any()
        private val NAME_BUILDER: ThreadLocal<StringBuilder> =
            object : ThreadLocal<StringBuilder>() {
                override fun initialValue(): StringBuilder {
                    return StringBuilder(Utils.THREAD_PREFIX)
                }
            }
        private val SEQUENCE_GENERATOR =
            AtomicInteger()
        private val ERRORING_HANDLER: RequestHandler = object : RequestHandler() {
            override fun canHandleRequest(data: Request?): Boolean {
                return true
            }

            @Throws(IOException::class)
            override fun load(
                request: Request?,
                networkPolicy: Int
            ): Result? {
                throw IllegalStateException("Unrecognized type of request: $request")
            }
        }

        /**
         * Decode a byte stream into a Bitmap. This method will take into account additional information
         * about the supplied request in order to do the decoding efficiently (such as through leveraging
         * `inSampleSize`).
         */
        @Throws(IOException::class)
        fun decodeStream(source: Source?, request: Request): Bitmap {
            val bufferedSource = Okio.buffer(source!!)
            val isWebPFile = isWebPFile(bufferedSource)
            val isPurgeable =
                request.purgeable && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
            val options = createBitmapOptions(request)
            val calculateSize = requiresInSampleSize(options)
            // We decode from a byte array because, a) when decoding a WebP network stream, BitmapFactory
// throws a JNI Exception, so we workaround by decoding a byte array, or b) user requested
// purgeable, which only affects bitmaps decoded from byte arrays.
            return if (isWebPFile || isPurgeable) {
                val bytes = bufferedSource.readByteArray()
                if (calculateSize) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    calculateInSampleSize(
                        request.targetWidth, request.targetHeight, options!!,
                        request
                    )
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } else {
                var stream = bufferedSource.inputStream()
                if (calculateSize) { // TODO use an InputStream that buffers with Okio...
                    val markStream = MarkableInputStream(stream!!)
                    stream = markStream
                    markStream.allowMarksToExpire(false)
                    val mark = markStream.savePosition(1024)
                    BitmapFactory.decodeStream(stream, null, options)
                    calculateInSampleSize(
                        request.targetWidth, request.targetHeight, options!!,
                        request
                    )
                    markStream.reset(mark)
                    markStream.allowMarksToExpire(true)
                }
                BitmapFactory.decodeStream(stream, null, options)
                    ?: // Treat null as an IO exception, we will eventually retry.
                    throw IOException("Failed to decode stream.")
            }
        }

        fun updateThreadName(data: Request) {
            val name = data.name
            val builder = NAME_BUILDER.get()
            builder.ensureCapacity(Utils.THREAD_PREFIX.length + name.length)
            builder.replace(Utils.THREAD_PREFIX.length, builder.length, name)
            Thread.currentThread().name = builder.toString()
        }

        fun forRequest(
            lever: Lever,
            dispatcher: Dispatcher,
            cache: Cache,
            stats: Stats,
            action: Action<*>
        ): BitmapHunter {
            val request = action.request
            val requestHandlers = lever.requestHandlers
            // Index-based loop to avoid allocating an iterator.
            var i = 0
            val count = requestHandlers.size
            while (i < count) {
                val requestHandler = requestHandlers[i]
                if (requestHandler.canHandleRequest(request)) {
                    return BitmapHunter(lever, dispatcher, cache, stats, action, requestHandler)
                }
                i++
            }
            return BitmapHunter(
                lever,
                dispatcher,
                cache,
                stats,
                action,
                ERRORING_HANDLER
            )
        }

        fun applyCustomTransformations(
            transformations: List<Transformation>?,
            result: Bitmap?
        ): Bitmap? {
            var result = result
            var i = 0
            val count = transformations!!.size
            while (i < count) {
                val transformation = transformations[i]
                var newResult: Bitmap
                newResult = try {
                    transformation.transform(result!!)
                } catch (e: RuntimeException) {
                    Lever.HANDLER.post {
                        throw RuntimeException(
                            "Transformation " + transformation.key() + " crashed with exception.",
                            e
                        )
                    }
                    return null
                }
                if (newResult == null) {
                    val builder = StringBuilder() //
                        .append("Transformation ")
                        .append(transformation.key())
                        .append(" returned null after ")
                        .append(i)
                        .append(" previous transformation(s).\n\nTransformation list:\n")
                    for (t in transformations) {
                        builder.append(t.key()).append('\n')
                    }
                    Lever.HANDLER.post { throw NullPointerException(builder.toString()) }
                    return null
                }
                if (newResult == result && result.isRecycled) {
                    Lever.HANDLER.post {
                        throw IllegalStateException(
                            "Transformation "
                                    + transformation.key()
                                    + " returned input Bitmap but recycled it."
                        )
                    }
                    return null
                }
                // If the transformation returned a new bitmap ensure they recycled the original.
                if (newResult != result && !result.isRecycled) {
                    Lever.HANDLER.post {
                        throw IllegalStateException(
                            "Transformation "
                                    + transformation.key()
                                    + " mutated input Bitmap but failed to recycle the original."
                        )
                    }
                    return null
                }
                result = newResult
                i++
            }
            return result
        }

        fun transformResult(
            data: Request,
            result: Bitmap,
            exifOrientation: Int
        ): Bitmap {
            var result = result
            val inWidth = result.width
            val inHeight = result.height
            val onlyScaleDown = data.onlyScaleDown
            var drawX = 0
            var drawY = 0
            var drawWidth = inWidth
            var drawHeight = inHeight
            val matrix = Matrix()
            if (data.needsMatrixTransform() || exifOrientation != 0) {
                var targetWidth = data.targetWidth
                var targetHeight = data.targetHeight
                val targetRotation = data.rotationDegrees
                if (targetRotation != 0f) {
                    val cosR =
                        Math.cos(Math.toRadians(targetRotation.toDouble()))
                    val sinR =
                        Math.sin(Math.toRadians(targetRotation.toDouble()))
                    if (data.hasRotationPivot) {
                        matrix.setRotate(targetRotation, data.rotationPivotX, data.rotationPivotY)
                        // Recalculate dimensions after rotation around pivot point
                        val x1T =
                            data.rotationPivotX * (1.0 - cosR) + data.rotationPivotY * sinR
                        val y1T =
                            data.rotationPivotY * (1.0 - cosR) - data.rotationPivotX * sinR
                        val x2T = x1T + data.targetWidth * cosR
                        val y2T = y1T + data.targetWidth * sinR
                        val x3T =
                            x1T + data.targetWidth * cosR - data.targetHeight * sinR
                        val y3T =
                            y1T + data.targetWidth * sinR + data.targetHeight * cosR
                        val x4T = x1T - data.targetHeight * sinR
                        val y4T = y1T + data.targetHeight * cosR
                        val maxX = Math.max(
                            x4T,
                            Math.max(x3T, Math.max(x1T, x2T))
                        )
                        val minX = Math.min(
                            x4T,
                            Math.min(x3T, Math.min(x1T, x2T))
                        )
                        val maxY = Math.max(
                            y4T,
                            Math.max(y3T, Math.max(y1T, y2T))
                        )
                        val minY = Math.min(
                            y4T,
                            Math.min(y3T, Math.min(y1T, y2T))
                        )
                        targetWidth = Math.floor(maxX - minX).toInt()
                        targetHeight = Math.floor(maxY - minY).toInt()
                    } else {
                        matrix.setRotate(targetRotation)
                        // Recalculate dimensions after rotation (around origin)
                        val x1T = 0.0
                        val y1T = 0.0
                        val x2T = data.targetWidth * cosR
                        val y2T = data.targetWidth * sinR
                        val x3T =
                            data.targetWidth * cosR - data.targetHeight * sinR
                        val y3T =
                            data.targetWidth * sinR + data.targetHeight * cosR
                        val x4T = -(data.targetHeight * sinR)
                        val y4T = data.targetHeight * cosR
                        val maxX = Math.max(
                            x4T,
                            Math.max(x3T, Math.max(x1T, x2T))
                        )
                        val minX = Math.min(
                            x4T,
                            Math.min(x3T, Math.min(x1T, x2T))
                        )
                        val maxY = Math.max(
                            y4T,
                            Math.max(y3T, Math.max(y1T, y2T))
                        )
                        val minY = Math.min(
                            y4T,
                            Math.min(y3T, Math.min(y1T, y2T))
                        )
                        targetWidth = Math.floor(maxX - minX).toInt()
                        targetHeight = Math.floor(maxY - minY).toInt()
                    }
                }
                // EXIf interpretation should be done before cropping in case the dimensions need to
// be recalculated
                if (exifOrientation != 0) {
                    val exifRotation = getExifRotation(exifOrientation)
                    val exifTranslation =
                        getExifTranslation(exifOrientation)
                    if (exifRotation != 0) {
                        matrix.preRotate(exifRotation.toFloat())
                        if (exifRotation == 90 || exifRotation == 270) { // Recalculate dimensions after exif rotation
                            val tmpHeight = targetHeight
                            targetHeight = targetWidth
                            targetWidth = tmpHeight
                        }
                    }
                    if (exifTranslation != 1) {
                        matrix.postScale(exifTranslation.toFloat(), 1f)
                    }
                }
                if (data.centerCrop) { // Keep aspect ratio if one dimension is set to 0
                    val widthRatio =
                        if (targetWidth != 0) targetWidth / inWidth.toFloat() else targetHeight / inHeight.toFloat()
                    val heightRatio =
                        if (targetHeight != 0) targetHeight / inHeight.toFloat() else targetWidth / inWidth.toFloat()
                    val scaleX: Float
                    val scaleY: Float
                    if (widthRatio > heightRatio) {
                        val newSize =
                            Math.ceil(inHeight * (heightRatio / widthRatio).toDouble()).toInt()
                        drawY = if (data.centerCropGravity and Gravity.TOP == Gravity.TOP) {
                            0
                        } else if (data.centerCropGravity and Gravity.BOTTOM == Gravity.BOTTOM) {
                            inHeight - newSize
                        } else {
                            (inHeight - newSize) / 2
                        }
                        drawHeight = newSize
                        scaleX = widthRatio
                        scaleY = targetHeight / drawHeight.toFloat()
                    } else if (widthRatio < heightRatio) {
                        val newSize =
                            Math.ceil(inWidth * (widthRatio / heightRatio).toDouble()).toInt()
                        drawX = if (data.centerCropGravity and Gravity.START == Gravity.START) {
                            0
                        } else if (data.centerCropGravity and Gravity.END == Gravity.END) {
                            inWidth - newSize
                        } else {
                            (inWidth - newSize) / 2
                        }
                        drawWidth = newSize
                        scaleX = targetWidth / drawWidth.toFloat()
                        scaleY = heightRatio
                    } else {
                        drawX = 0
                        drawWidth = inWidth
                        scaleY = heightRatio
                        scaleX = scaleY
                    }
                    if (shouldResize(
                            onlyScaleDown,
                            inWidth,
                            inHeight,
                            targetWidth,
                            targetHeight
                        )
                    ) {
                        matrix.preScale(scaleX, scaleY)
                    }
                } else if (data.centerInside) { // Keep aspect ratio if one dimension is set to 0
                    val widthRatio =
                        if (targetWidth != 0) targetWidth / inWidth.toFloat() else targetHeight / inHeight.toFloat()
                    val heightRatio =
                        if (targetHeight != 0) targetHeight / inHeight.toFloat() else targetWidth / inWidth.toFloat()
                    val scale =
                        if (widthRatio < heightRatio) widthRatio else heightRatio
                    if (shouldResize(
                            onlyScaleDown,
                            inWidth,
                            inHeight,
                            targetWidth,
                            targetHeight
                        )
                    ) {
                        matrix.preScale(scale, scale)
                    }
                } else if ((targetWidth != 0 || targetHeight != 0) //
                    && (targetWidth != inWidth || targetHeight != inHeight)
                ) { // If an explicit target size has been specified and they do not match the results bounds,
// pre-scale the existing matrix appropriately.
// Keep aspect ratio if one dimension is set to 0.
                    val sx =
                        if (targetWidth != 0) targetWidth / inWidth.toFloat() else targetHeight / inHeight.toFloat()
                    val sy =
                        if (targetHeight != 0) targetHeight / inHeight.toFloat() else targetWidth / inWidth.toFloat()
                    if (shouldResize(
                            onlyScaleDown,
                            inWidth,
                            inHeight,
                            targetWidth,
                            targetHeight
                        )
                    ) {
                        matrix.preScale(sx, sy)
                    }
                }
            }
            val newResult =
                Bitmap.createBitmap(result, drawX, drawY, drawWidth, drawHeight, matrix, true)
            if (newResult != result) {
                result.recycle()
                result = newResult
            }
            return result
        }

        private fun shouldResize(
            onlyScaleDown: Boolean, inWidth: Int, inHeight: Int,
            targetWidth: Int, targetHeight: Int
        ): Boolean {
            return (!onlyScaleDown || targetWidth != 0 && inWidth > targetWidth
                    || targetHeight != 0 && inHeight > targetHeight)
        }

        fun getExifRotation(orientation: Int): Int {
            val rotation: Int
            rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
                ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
                ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
                else -> 0
            }
            return rotation
        }

        fun getExifTranslation(orientation: Int): Int {
            val translation: Int
            translation = when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL, ExifInterface.ORIENTATION_FLIP_VERTICAL, ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE -> -1
                else -> 1
            }
            return translation
        }
    }

    init {
        sequence = SEQUENCE_GENERATOR.incrementAndGet()
        this.lever = lever
        this.dispatcher = dispatcher
        this.cache = cache
        this.stats = stats
        this.action = action
        key = action.key
        data = action.request!!
        priority = action.priority
        memoryPolicy = action.memoryPolicy
        networkPolicy = action.networkPolicy
        this.requestHandler = requestHandler
        retryCount = requestHandler.retryCount
    }
}
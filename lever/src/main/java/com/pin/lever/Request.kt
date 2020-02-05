package com.pin.lever

import android.graphics.Bitmap
import android.net.Uri
import android.support.annotation.DrawableRes
import android.support.annotation.Px
import android.view.Gravity
import com.pin.lever.Lever
import java.util.*
import java.util.concurrent.TimeUnit

/** Immutable data about an image and the transformations that will be applied to it.  */
class Request private constructor(
    /**
     * The image URI.
     *
     *
     * This is mutually exclusive with [.resourceId].
     */
    val uri: Uri?,
    /**
     * The image resource ID.
     *
     *
     * This is mutually exclusive with [.uri].
     */
    val resourceId: Int,
    /**
     * Optional stable key for this request to be used instead of the URI or resource ID when
     * caching. Two requests with the same value are considered to be for the same resource.
     */
    val stableKey: String?,
    transformations: List<Transformation>?,
    targetWidth: Int,
    targetHeight: Int,
    centerCrop: Boolean,
    centerInside: Boolean,
    centerCropGravity: Int,
    onlyScaleDown: Boolean,
    rotationDegrees: Float,
    rotationPivotX: Float,
    rotationPivotY: Float,
    hasRotationPivot: Boolean,
    purgeable: Boolean,
    config: Bitmap.Config?,
    priority: Lever.Priority
) {
    /** A unique ID for the request.  */
    var id = 0
    /** The time that the request was first submitted (in nanos).  */
    var started: Long = 0
    /** The [NetworkPolicy] to use for this request.  */
    @JvmField
    var networkPolicy = 0
    /** List of custom transformations to be applied after the built-in transformations.  */
    @JvmField
    var transformations: MutableList<Transformation>? = null
    /** Target image width for resizing.  */
    @JvmField
    val targetWidth: Int
    /** Target image height for resizing.  */
    @JvmField
    val targetHeight: Int
    /**
     * True if the final image should use the 'centerCrop' scale technique.
     *
     *
     * This is mutually exclusive with [.centerInside].
     */
    @JvmField
    val centerCrop: Boolean
    /** If centerCrop is set, controls alignment of centered image  */
    @JvmField
    val centerCropGravity: Int
    /**
     * True if the final image should use the 'centerInside' scale technique.
     *
     *
     * This is mutually exclusive with [.centerCrop].
     */
    @JvmField
    val centerInside: Boolean
    @JvmField
    val onlyScaleDown: Boolean
    /** Amount to rotate the image in degrees.  */
    @JvmField
    val rotationDegrees: Float
    /** Rotation pivot on the X axis.  */
    @JvmField
    val rotationPivotX: Float
    /** Rotation pivot on the Y axis.  */
    @JvmField
    val rotationPivotY: Float
    /** Whether or not [.rotationPivotX] and [.rotationPivotY] are set.  */
    @JvmField
    val hasRotationPivot: Boolean
    /** True if image should be decoded with inPurgeable and inInputShareable.  */
    @JvmField
    val purgeable: Boolean
    /** Target image config for decoding.  */
    val config: Bitmap.Config?
    /** The priority of this request.  */
    @JvmField
    val priority: Lever.Priority

    override fun toString(): String {
        val builder = StringBuilder("Request{")
        if (resourceId > 0) {
            builder.append(resourceId)
        } else {
            builder.append(uri)
        }
        if (transformations != null && !transformations!!.isEmpty()) {
            for (transformation in transformations!!) {
                builder.append(' ').append(transformation.key())
            }
        }
        if (stableKey != null) {
            builder.append(" stableKey(").append(stableKey).append(')')
        }
        if (targetWidth > 0) {
            builder.append(" resize(").append(targetWidth).append(',').append(targetHeight)
                .append(')')
        }
        if (centerCrop) {
            builder.append(" centerCrop")
        }
        if (centerInside) {
            builder.append(" centerInside")
        }
        if (rotationDegrees != 0f) {
            builder.append(" rotation(").append(rotationDegrees)
            if (hasRotationPivot) {
                builder.append(" @ ").append(rotationPivotX).append(',').append(rotationPivotY)
            }
            builder.append(')')
        }
        if (purgeable) {
            builder.append(" purgeable")
        }
        if (config != null) {
            builder.append(' ').append(config)
        }
        builder.append('}')
        return builder.toString()
    }

    fun logId(): String {
        val delta = System.nanoTime() - started
        return if (delta > TOO_LONG_LOG) {
            plainId() + '+' + TimeUnit.NANOSECONDS.toSeconds(delta) + 's'
        } else plainId() + '+' + TimeUnit.NANOSECONDS.toMillis(delta) + "ms"
    }

    fun plainId(): String {
        return "[R$id]"
    }

    val name: String
        get() = uri?.path?.toString() ?: Integer.toHexString(resourceId)

    fun hasSize(): Boolean {
        return targetWidth != 0 || targetHeight != 0
    }

    fun needsTransformation(): Boolean {
        return needsMatrixTransform() || hasCustomTransformations()
    }

    fun needsMatrixTransform(): Boolean {
        return hasSize() || rotationDegrees != 0f
    }

    fun hasCustomTransformations(): Boolean {
        return transformations != null
    }

    fun buildUpon(): Builder {
        return Builder(this)
    }

    /** Builder for creating [Request] instances.  */
    class Builder {
        private var uri: Uri? = null
        private var resourceId = 0
        private var stableKey: String? = null
        private var targetWidth = 0
        private var targetHeight = 0
        private var centerCrop = false
        private var centerCropGravity = 0
        private var centerInside = false
        private var onlyScaleDown = false
        private var rotationDegrees = 0f
        private var rotationPivotX = 0f
        private var rotationPivotY = 0f
        private var hasRotationPivot = false
        private var purgeable = false
        private var transformations: MutableList<Transformation>? =
            null
        private var config: Bitmap.Config? = null
        private var priority: Lever.Priority? = null

        /** Start building a request using the specified [Uri].  */
        constructor(uri: Uri) {
            setUri(uri)
        }

        /** Start building a request using the specified resource ID.  */
        constructor(@DrawableRes resourceId: Int) {
            setResourceId(resourceId)
        }

        internal constructor(uri: Uri?, resourceId: Int, bitmapConfig: Bitmap.Config?) {
            this.uri = uri
            this.resourceId = resourceId
            config = bitmapConfig
        }

        constructor(request: Request) {
            uri = request.uri
            resourceId = request.resourceId
            stableKey = request.stableKey
            targetWidth = request.targetWidth
            targetHeight = request.targetHeight
            centerCrop = request.centerCrop
            centerInside = request.centerInside
            centerCropGravity = request.centerCropGravity
            rotationDegrees = request.rotationDegrees
            rotationPivotX = request.rotationPivotX
            rotationPivotY = request.rotationPivotY
            hasRotationPivot = request.hasRotationPivot
            purgeable = request.purgeable
            onlyScaleDown = request.onlyScaleDown
            if (request.transformations != null) {
                transformations =
                    ArrayList(request.transformations)
            }
            config = request.config
            priority = request.priority
        }

        fun hasImage(): Boolean {
            return uri != null || resourceId != 0
        }

        fun hasSize(): Boolean {
            return targetWidth != 0 || targetHeight != 0
        }

        fun hasPriority(): Boolean {
            return priority != null
        }

        /**
         * Set the target image Uri.
         *
         *
         * This will clear an image resource ID if one is set.
         */
        fun setUri(uri: Uri): Builder {
            requireNotNull(uri) { "Image URI may not be null." }
            this.uri = uri
            resourceId = 0
            return this
        }

        /**
         * Set the target image resource ID.
         *
         *
         * This will clear an image Uri if one is set.
         */
        fun setResourceId(@DrawableRes resourceId: Int): Builder {
            require(resourceId != 0) { "Image resource ID may not be 0." }
            this.resourceId = resourceId
            uri = null
            return this
        }

        /**
         * Set the stable key to be used instead of the URI or resource ID when caching.
         * Two requests with the same value are considered to be for the same resource.
         */
        fun stableKey(stableKey: String?): Builder {
            this.stableKey = stableKey
            return this
        }

        /**
         * Resize the image to the specified size in pixels.
         * Use 0 as desired dimension to resize keeping aspect ratio.
         */
        fun resize(@Px targetWidth: Int, @Px targetHeight: Int): Builder {
            require(targetWidth >= 0) { "Width must be positive number or 0." }
            require(targetHeight >= 0) { "Height must be positive number or 0." }
            require(!(targetHeight == 0 && targetWidth == 0)) { "At least one dimension has to be positive number." }
            this.targetWidth = targetWidth
            this.targetHeight = targetHeight
            return this
        }

        /** Clear the resize transformation, if any. This will also clear center crop/inside if set.  */
        fun clearResize(): Builder {
            targetWidth = 0
            targetHeight = 0
            centerCrop = false
            centerInside = false
            return this
        }
        /**
         * Crops an image inside of the bounds specified by [.resize] rather than
         * distorting the aspect ratio. This cropping technique scales the image so that it fills the
         * requested bounds, aligns it using provided gravity parameter and then crops the extra.
         */
        /**
         * Crops an image inside of the bounds specified by [.resize] rather than
         * distorting the aspect ratio. This cropping technique scales the image so that it fills the
         * requested bounds and then crops the extra.
         */
        @JvmOverloads
        fun centerCrop(alignGravity: Int = Gravity.CENTER): Builder {
            check(!centerInside) { "Center crop can not be used after calling centerInside" }
            centerCrop = true
            centerCropGravity = alignGravity
            return this
        }

        /** Clear the center crop transformation flag, if set.  */
        fun clearCenterCrop(): Builder {
            centerCrop = false
            centerCropGravity = Gravity.CENTER
            return this
        }

        /**
         * Centers an image inside of the bounds specified by [.resize]. This scales
         * the image so that both dimensions are equal to or less than the requested bounds.
         */
        fun centerInside(): Builder {
            check(!centerCrop) { "Center inside can not be used after calling centerCrop" }
            centerInside = true
            return this
        }

        /** Clear the center inside transformation flag, if set.  */
        fun clearCenterInside(): Builder {
            centerInside = false
            return this
        }

        /**
         * Only resize an image if the original image size is bigger than the target size
         * specified by [.resize].
         */
        fun onlyScaleDown(): Builder {
            check(!(targetHeight == 0 && targetWidth == 0)) { "onlyScaleDown can not be applied without resize" }
            onlyScaleDown = true
            return this
        }

        /** Clear the onlyScaleUp flag, if set.  */
        fun clearOnlyScaleDown(): Builder {
            onlyScaleDown = false
            return this
        }

        /** Rotate the image by the specified degrees.  */
        fun rotate(degrees: Float): Builder {
            rotationDegrees = degrees
            return this
        }

        /** Rotate the image by the specified degrees around a pivot point.  */
        fun rotate(
            degrees: Float,
            pivotX: Float,
            pivotY: Float
        ): Builder {
            rotationDegrees = degrees
            rotationPivotX = pivotX
            rotationPivotY = pivotY
            hasRotationPivot = true
            return this
        }

        /** Clear the rotation transformation, if any.  */
        fun clearRotation(): Builder {
            rotationDegrees = 0f
            rotationPivotX = 0f
            rotationPivotY = 0f
            hasRotationPivot = false
            return this
        }

        fun purgeable(): Builder {
            purgeable = true
            return this
        }

        /** Decode the image using the specified config.  */
        fun config(config: Bitmap.Config): Builder {
            requireNotNull(config) { "config == null" }
            this.config = config
            return this
        }

        /** Execute request using the specified priority.  */
        fun priority(priority: Lever.Priority): Builder {
            requireNotNull(priority) { "Priority invalid." }
            check(this.priority == null) { "Priority already set." }
            this.priority = priority
            return this
        }

        /**
         * Add a custom transformation to be applied to the image.
         *
         *
         * Custom transformations will always be run after the built-in transformations.
         */
        fun transform(transformation: Transformation): Builder {
            requireNotNull(transformation) { "Transformation must not be null." }
            requireNotNull(transformation.key()) { "Transformation key must not be null." }
            if (transformations == null) {
                transformations = ArrayList(2)
            }
            transformations!!.add(transformation)
            return this
        }

        /**
         * Add a list of custom transformations to be applied to the image.
         *
         *
         * Custom transformations will always be run after the built-in transformations.
         */
        fun transform(transformations: List<Transformation>): Builder {
            requireNotNull(transformations) { "Transformation list must not be null." }
            var i = 0
            val size = transformations.size
            while (i < size) {
                transform(transformations[i])
                i++
            }
            return this
        }

        /** Create the immutable [Request] object.  */
        fun build(): Request {
            check(!(centerInside && centerCrop)) { "Center crop and center inside can not be used together." }
            check(!(centerCrop && targetWidth == 0 && targetHeight == 0)) { "Center crop requires calling resize with positive width and height." }
            check(!(centerInside && targetWidth == 0 && targetHeight == 0)) { "Center inside requires calling resize with positive width and height." }
            if (priority == null) {
                priority = Lever.Priority.NORMAL
            }
            return Request(
                uri, resourceId, stableKey, transformations, targetWidth, targetHeight,
                centerCrop, centerInside, centerCropGravity, onlyScaleDown, rotationDegrees,
                rotationPivotX, rotationPivotY, hasRotationPivot, purgeable, config, priority!!
            )
        }

        fun transform(transformations: List<Transformation?>) {

        }
    }

    companion object {
        private val TOO_LONG_LOG = TimeUnit.SECONDS.toNanos(5)
    }

    init {
        if (transformations != null) {
            this.transformations = Collections.unmodifiableList(transformations)
        }
        this.targetWidth = targetWidth
        this.targetHeight = targetHeight
        this.centerCrop = centerCrop
        this.centerInside = centerInside
        this.centerCropGravity = centerCropGravity
        this.onlyScaleDown = onlyScaleDown
        this.rotationDegrees = rotationDegrees
        this.rotationPivotX = rotationPivotX
        this.rotationPivotY = rotationPivotY
        this.hasRotationPivot = hasRotationPivot
        this.purgeable = purgeable
        this.config = config
        this.priority = priority
    }
}
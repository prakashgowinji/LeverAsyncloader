package com.pin.lever

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.support.annotation.DrawableRes
import android.support.annotation.IdRes
import android.support.annotation.VisibleForTesting
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.RemoteViews
import com.pin.lever.Lever.LoadedFrom
import com.pin.lever.RemoteViewsAction.AppWidgetAction
import com.pin.lever.RemoteViewsAction.NotificationAction
import com.pin.lever.Utils.OWNER_MAIN
import com.pin.lever.Utils.VERB_COMPLETED
import com.pin.lever.Utils.VERB_CREATED
import com.pin.lever.Utils.VERB_CHANGED
import com.pin.lever.Utils.checkMain
import com.pin.lever.Utils.checkNotMain
import com.pin.lever.Utils.createKey
import com.pin.lever.Utils.log
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

// Public API.
class RequestCreator {
    private val lever: Lever?
    private val data: Request.Builder
    private var noFade = false
    private var deferred = false
    private var setPlaceholder = true
    private var placeholderResId = 0
    private var errorResId = 0
    private var memoryPolicy = 0
    private var networkPolicy = 0
    private var placeholderDrawable: Drawable? = null
    private var errorDrawable: Drawable? = null
    /** Internal use only. Used by [DeferredRequestCreator].  */
    var tag: Any? = null
        private set

    internal constructor(lever: Lever, uri: Uri?, resourceId: Int) {
        check(!lever.shutdown) { "Lever instance already shut down. Cannot submit new requests." }
        this.lever = lever
        data = Request.Builder(uri, resourceId, lever.defaultBitmapConfig)
    }

    @VisibleForTesting
    internal constructor() {
        lever = null
        data = Request.Builder(null, 0, null)
    }

    /**
     * Explicitly opt-out to having a placeholder set when calling `into`.
     *
     *
     * By default, Lever will either set a supplied placeholder or clear the target
     * [ImageView] in order to ensure behavior in situations where views are recycled. This
     * method will prevent that behavior and retain any already set image.
     */
    fun noPlaceholder(): RequestCreator {
        check(placeholderResId == 0) { "Placeholder resource already set." }
        check(placeholderDrawable == null) { "Placeholder image already set." }
        setPlaceholder = false
        return this
    }

    /**
     * A placeholder drawable to be used while the image is being loaded. If the requested image is
     * not immediately available in the memory cache then this resource will be set on the target
     * [ImageView].
     */
    fun placeholder(@DrawableRes placeholderResId: Int): RequestCreator {
        check(setPlaceholder) { "Already explicitly declared as no placeholder." }
        require(placeholderResId != 0) { "Placeholder image resource invalid." }
        check(placeholderDrawable == null) { "Placeholder image already set." }
        this.placeholderResId = placeholderResId
        return this
    }

    /**
     * A placeholder drawable to be used while the image is being loaded. If the requested image is
     * not immediately available in the memory cache then this resource will be set on the target
     * [ImageView].
     *
     *
     * If you are not using a placeholder image but want to clear an existing image (such as when
     * used in an [adapter][android.widget.Adapter]), pass in `null`.
     */
    fun placeholder(placeholderDrawable: Drawable): RequestCreator {
        check(setPlaceholder) { "Already explicitly declared as no placeholder." }
        check(placeholderResId == 0) { "Placeholder image already set." }
        this.placeholderDrawable = placeholderDrawable
        return this
    }

    /** An error drawable to be used if the request image could not be loaded.  */
    fun error(@DrawableRes errorResId: Int): RequestCreator {
        require(errorResId != 0) { "Error image resource invalid." }
        check(errorDrawable == null) { "Error image already set." }
        this.errorResId = errorResId
        return this
    }

    /** An error drawable to be used if the request image could not be loaded.  */
    fun error(errorDrawable: Drawable): RequestCreator {
        requireNotNull(errorDrawable) { "Error image may not be null." }
        check(errorResId == 0) { "Error image already set." }
        this.errorDrawable = errorDrawable
        return this
    }

    /**
     * Assign a tag to this request. Tags are an easy way to logically associate
     * related requests that can be managed together e.g. paused, resumed,
     * or canceled.
     *
     *
     * You can either use simple [String] tags or objects that naturally
     * define the scope of your requests within your app such as a
     * [android.content.Context], an [android.app.Activity], or a
     * [android.app.Fragment].
     *
     * **WARNING:**: Lever will keep a reference to the tag for
     * as long as this tag is paused and/or has active requests. Look out for
     * potential leaks.
     *
     * @see Lever.cancelTag
     * @see Lever.pauseTag
     * @see Lever.resumeTag
     */
    fun tag(tag: Any): RequestCreator {
        requireNotNull(tag) { "Tag invalid." }
        check(this.tag == null) { "Tag already set." }
        this.tag = tag
        return this
    }

    /**
     * Attempt to resize the image to fit exactly into the target [ImageView]'s bounds. This
     * will result in delayed execution of the request until the [ImageView] has been laid out.
     *
     *
     * *Note:* This method works only when your target is an [ImageView].
     */
    fun fit(): RequestCreator {
        deferred = true
        return this
    }

    /** Internal use only. Used by [DeferredRequestCreator].  */
    fun unfit(): RequestCreator {
        deferred = false
        return this
    }

    /** Internal use only. Used by [DeferredRequestCreator].  */
    fun clearTag(): RequestCreator {
        tag = null
        return this
    }

    /** Resize the image to the specified dimension size.  */
    fun resizeDimen(targetWidthResId: Int, targetHeightResId: Int): RequestCreator {
        val resources = lever!!.context.resources
        val targetWidth = resources.getDimensionPixelSize(targetWidthResId)
        val targetHeight = resources.getDimensionPixelSize(targetHeightResId)
        return resize(targetWidth, targetHeight)
    }

    /** Resize the image to the specified size in pixels.  */
    fun resize(targetWidth: Int, targetHeight: Int): RequestCreator {
        data.resize(targetWidth, targetHeight)
        return this
    }

    /**
     * Crops an image inside of the bounds specified by [.resize] rather than
     * distorting the aspect ratio. This cropping technique scales the image so that it fills the
     * requested bounds and then crops the extra.
     */
    fun centerCrop(): RequestCreator {
        data.centerCrop(Gravity.CENTER)
        return this
    }

    /**
     * Crops an image inside of the bounds specified by [.resize] rather than
     * distorting the aspect ratio. This cropping technique scales the image so that it fills the
     * requested bounds and then crops the extra, preferring the contents at `alignGravity`.
     */
    fun centerCrop(alignGravity: Int): RequestCreator {
        data.centerCrop(alignGravity)
        return this
    }

    /**
     * Centers an image inside of the bounds specified by [.resize]. This scales
     * the image so that both dimensions are equal to or less than the requested bounds.
     */
    fun centerInside(): RequestCreator {
        data.centerInside()
        return this
    }

    /**
     * Only resize an image if the original image size is bigger than the target size
     * specified by [.resize].
     */
    fun onlyScaleDown(): RequestCreator {
        data.onlyScaleDown()
        return this
    }

    /** Rotate the image by the specified degrees.  */
    fun rotate(degrees: Float): RequestCreator {
        data.rotate(degrees)
        return this
    }

    /** Rotate the image by the specified degrees around a pivot point.  */
    fun rotate(degrees: Float, pivotX: Float, pivotY: Float): RequestCreator {
        data.rotate(degrees, pivotX, pivotY)
        return this
    }

    /**
     * Attempt to decode the image using the specified config.
     *
     *
     * Note: This value may be ignored by [BitmapFactory]. See
     * [its documentation][BitmapFactory.Options.inPreferredConfig] for more details.
     */
    fun config(config: Bitmap.Config): RequestCreator {
        data.config(config)
        return this
    }

    /**
     * Sets the stable key for this request to be used instead of the URI or resource ID when
     * caching. Two requests with the same value are considered to be for the same resource.
     */
    fun stableKey(stableKey: String): RequestCreator {
        data.stableKey(stableKey)
        return this
    }

    /**
     * Set the priority of this request.
     *
     *
     * This will affect the order in which the requests execute but does not guarantee it.
     * By default, all requests have [Priority.NORMAL] priority, except for
     * [.fetch] requests, which have [Priority.LOW] priority by default.
     */
    fun priority(priority: Lever.Priority): RequestCreator {
        data.priority(priority)
        return this
    }

    /**
     * Add a custom transformation to be applied to the image.
     *
     *
     * Custom transformations will always be run after the built-in transformations.
     */
// TODO show example of calling resize after a transform in the javadoc
    fun transform(transformation: Transformation): RequestCreator {
        data.transform(transformation)
        return this
    }

    /**
     * Add a list of custom transformations to be applied to the image.
     *
     *
     * Custom transformations will always be run after the built-in transformations.
     */
    fun transform(transformations: List<Transformation?>): RequestCreator {
        data.transform(transformations)
        return this
    }

    /**
     * Specifies the [MemoryPolicy] to use for this request. You may specify additional policy
     * options using the varargs parameter.
     */
    fun memoryPolicy(
        policy: MemoryPolicy,
        vararg additional: MemoryPolicy
    ): RequestCreator {
        requireNotNull(policy) { "Memory policy cannot be null." }
        memoryPolicy = memoryPolicy or policy.index
        requireNotNull(additional) { "Memory policy cannot be null." }
        if (additional.size > 0) {
            for (memoryPolicy in additional) {
                requireNotNull(memoryPolicy) { "Memory policy cannot be null." }
                this.memoryPolicy = this.memoryPolicy or memoryPolicy.index
            }
        }
        return this
    }

    /**
     * Specifies the [NetworkPolicy] to use for this request. You may specify additional policy
     * options using the varargs parameter.
     */
    fun networkPolicy(
        policy: NetworkPolicy,
        vararg additional: NetworkPolicy
    ): RequestCreator {
        requireNotNull(policy) { "Network policy cannot be null." }
        networkPolicy = networkPolicy or policy.index
        requireNotNull(additional) { "Network policy cannot be null." }
        if (additional.size > 0) {
            for (networkPolicy in additional) {
                requireNotNull(networkPolicy) { "Network policy cannot be null." }
                this.networkPolicy = this.networkPolicy or networkPolicy.index
            }
        }
        return this
    }

    /**
     * Set inPurgeable and inInputShareable when decoding. This will force the bitmap to be decoded
     * from a byte array instead of a stream, since inPurgeable only affects the former.
     *
     *
     * *Note*: as of API level 21 (Lollipop), the inPurgeable field is deprecated and will be
     * ignored.
     */
    fun purgeable(): RequestCreator {
        data.purgeable()
        return this
    }

    /** Disable brief fade in of images loaded from the disk cache or network.  */
    fun noFade(): RequestCreator {
        noFade = true
        return this
    }

    /**
     * Synchronously fulfill this request. Must not be called from the main thread.
     *
     *
     * *Note*: The result of this operation is not cached in memory because the underlying
     * [Cache] implementation is not guaranteed to be thread-safe.
     */
    @Throws(IOException::class)
    fun get(): Bitmap? {
        val started = System.nanoTime()
        checkNotMain()
        check(!deferred) { "Fit cannot be used with instance." }
        if (!data.hasImage()) {
            return null
        }
        val finalData = createRequest(started)
        val key = createKey(finalData, StringBuilder())
        val action: Action<*> =
            GetAction(lever, finalData, memoryPolicy, networkPolicy, tag, key)
        return BitmapHunter.forRequest(lever!!, lever.dispatcher, lever.cache, lever.stats, action)
            .hunt()
    }
    /**
     * Asynchronously fulfills the request without a [ImageView] or [Target],
     * and invokes the target [Callback] with the result. This is useful when you want to warm
     * up the cache with an image.
     *
     *
     * *Note:* The [Callback] param is a strong reference and will prevent your
     * [android.app.Activity] or [android.app.Fragment] from being garbage collected
     * until the request is completed.
     */
    /**
     * Asynchronously fulfills the request without a [ImageView] or [Target]. This is
     * useful when you want to warm up the cache with an image.
     *
     *
     * *Note:* It is safe to invoke this method from any thread.
     */
    @JvmOverloads
    fun fetch(callback: Callback? = null) {
        val started = System.nanoTime()
        check(!deferred) { "Fit cannot be used with fetch." }
        if (data.hasImage()) { // Fetch requests have lower priority by default.
            if (!data.hasPriority()) {
                data.priority(Lever.Priority.LOW)
            }
            val request = createRequest(started)
            val key = createKey(request, StringBuilder())
            if (MemoryPolicy.shouldReadFromMemoryCache(memoryPolicy)) {
                val bitmap = lever!!.quickMemoryCacheCheck(key)
                if (bitmap != null) {
                    if (lever.isLoggingEnabled) {
                        log(
                            OWNER_MAIN,
                            VERB_COMPLETED,
                            request.plainId(),
                            "from " + LoadedFrom.MEMORY
                        )
                    }
                    callback?.onSuccess()
                    return
                }
            }
            val action: Action<*> =
                FetchAction(lever, request, memoryPolicy, networkPolicy, tag, key, callback)
            lever!!.submit(action)
        }
    }

    /**
     * Asynchronously fulfills the request into the specified [Target]. In most cases, you
     * should use this when you are dealing with a custom [View][android.view.View] or view
     * holder which should implement the [Target] interface.
     *
     *
     * Implementing on a [View][android.view.View]:
     * <blockquote><pre>
     * public class ProfileView extends FrameLayout implements Target {
     * @Override public void onBitmapLoaded(Bitmap bitmap, LoadedFrom from) {
     * setBackgroundDrawable(new BitmapDrawable(bitmap));
     * }
     *
     * @Override public void onBitmapFailed(Exception e, Drawable errorDrawable) {
     * setBackgroundDrawable(errorDrawable);
     * }
     *
     * @Override public void onPrepareLoad(Drawable placeHolderDrawable) {
     * setBackgroundDrawable(placeHolderDrawable);
     * }
     * }
    </pre></blockquote> *
     * Implementing on a view holder object for use inside of an adapter:
     * <blockquote><pre>
     * public class ViewHolder implements Target {
     * public FrameLayout frame;
     * public TextView name;
     *
     * @Override public void onBitmapLoaded(Bitmap bitmap, LoadedFrom from) {
     * frame.setBackgroundDrawable(new BitmapDrawable(bitmap));
     * }
     *
     * @Override public void onBitmapFailed(Exception e, Drawable errorDrawable) {
     * frame.setBackgroundDrawable(errorDrawable);
     * }
     *
     * @Override public void onPrepareLoad(Drawable placeHolderDrawable) {
     * frame.setBackgroundDrawable(placeHolderDrawable);
     * }
     * }
    </pre></blockquote> *
     *
     *
     * *Note:* This method keeps a weak reference to the [Target] instance and will be
     * garbage collected if you do not keep a strong reference to it. To receive callbacks when an
     * image is loaded use [.into].
     */
    fun into(target: Target) {
        val started = System.nanoTime()
        checkMain()
        requireNotNull(target) { "Target must not be null." }
        check(!deferred) { "Fit cannot be used with a Target." }
        if (!data.hasImage()) {
            lever!!.cancelRequest(target)
            target.onPrepareLoad(if (setPlaceholder) getPlaceholderDrawable()!! else null)
            return
        }
        val request = createRequest(started)
        val requestKey = createKey(request)
        if (MemoryPolicy.shouldReadFromMemoryCache(memoryPolicy)) {
            val bitmap = lever!!.quickMemoryCacheCheck(requestKey)
            if (bitmap != null) {
                lever.cancelRequest(target)
                target.onBitmapLoaded(bitmap, LoadedFrom.MEMORY)
                return
            }
        }
        target.onPrepareLoad(if (setPlaceholder) getPlaceholderDrawable()!! else null)
        val action: Action<*> = TargetAction(
            lever, target, request, memoryPolicy, networkPolicy, errorDrawable,
            requestKey, tag, errorResId
        )
        lever!!.enqueueAndSubmit(action)
    }
    /**
     * Asynchronously fulfills the request into the specified [RemoteViews] object with the
     * given `viewId`. This is used for loading bitmaps into a [Notification].
     */
    /**
     * Asynchronously fulfills the request into the specified [RemoteViews] object with the
     * given `viewId`. This is used for loading bitmaps into a [Notification].
     */
    /**
     * Asynchronously fulfills the request into the specified [RemoteViews] object with the
     * given `viewId`. This is used for loading bitmaps into a [Notification].
     */
    @JvmOverloads
    fun into(
        remoteViews: RemoteViews, @IdRes viewId: Int,
        notificationId: Int,
        notification: Notification,
        notificationTag: String? = null,
        callback: Callback? = null
    ) {
        val started = System.nanoTime()
        requireNotNull(remoteViews) { "RemoteViews must not be null." }
        requireNotNull(notification) { "Notification must not be null." }
        check(!deferred) { "Fit cannot be used with RemoteViews." }
        require(!(placeholderDrawable != null || placeholderResId != 0 || errorDrawable != null)) { "Cannot use placeholder or error drawables with remote views." }
        val request = createRequest(started)
        val key =
            createKey(request, StringBuilder()) // Non-main thread needs own builder.
        val action: RemoteViewsAction = NotificationAction(
            lever, request, remoteViews, viewId, notificationId, notification,
            notificationTag!!, memoryPolicy, networkPolicy, key, tag, errorResId, callback
        )
        performRemoteViewInto(action)
    }
    /**
     * Asynchronously fulfills the request into the specified [RemoteViews] object with the
     * given `viewId`. This is used for loading bitmaps into all instances of a widget.
     */
    /**
     * Asynchronously fulfills the request into the specified [RemoteViews] object with the
     * given `viewId`. This is used for loading bitmaps into all instances of a widget.
     */
    @JvmOverloads
    fun into(
        remoteViews: RemoteViews, @IdRes viewId: Int, appWidgetIds: IntArray,
        callback: Callback? = null
    ) {
        val started = System.nanoTime()
        requireNotNull(remoteViews) { "remoteViews must not be null." }
        requireNotNull(appWidgetIds) { "appWidgetIds must not be null." }
        check(!deferred) { "Fit cannot be used with remote views." }
        require(!(placeholderDrawable != null || placeholderResId != 0 || errorDrawable != null)) { "Cannot use placeholder or error drawables with remote views." }
        val request = createRequest(started)
        val key =
            createKey(request, StringBuilder()) // Non-main thread needs own builder.
        val action: RemoteViewsAction = AppWidgetAction(
            lever, request, remoteViews, viewId, appWidgetIds, memoryPolicy,
            networkPolicy, key, tag, errorResId, callback
        )
        performRemoteViewInto(action)
    }
    /**
     * Asynchronously fulfills the request into the specified [ImageView] and invokes the
     * target [Callback] if it's not `null`.
     *
     *
     * *Note:* The [Callback] param is a strong reference and will prevent your
     * [android.app.Activity] or [android.app.Fragment] from being garbage collected. If
     * you use this method, it is **strongly** recommended you invoke an adjacent
     * [Lever.cancelRequest] call to prevent temporary leaking.
     */
    /**
     * Asynchronously fulfills the request into the specified [ImageView].
     *
     *
     * *Note:* This method keeps a weak reference to the [ImageView] instance and will
     * automatically support object recycling.
     */
    @JvmOverloads
    fun into(
        target: ImageView?,
        callback: Callback? = null
    ) {
        val started = System.nanoTime()
        checkMain()
        requireNotNull(target) { "Target must not be null." }
        if (!data.hasImage()) {
            lever!!.cancelRequest(target)
            if (setPlaceholder) {
                LeverDrawable.setPlaceholder(target, getPlaceholderDrawable())
            }
            return
        }
        if (deferred) {
            check(!data.hasSize()) { "Fit cannot be used with resize." }
            val width = target.width
            val height = target.height
            if (width == 0 || height == 0) {
                if (setPlaceholder) {
                    LeverDrawable.setPlaceholder(target, getPlaceholderDrawable())
                }
                lever!!.defer(target, DeferredRequestCreator(this, target, callback))
                return
            }
            data.resize(width, height)
        }
        val request = createRequest(started)
        val requestKey = createKey(request)
        if (MemoryPolicy.shouldReadFromMemoryCache(memoryPolicy)) {
            val bitmap = lever!!.quickMemoryCacheCheck(requestKey)
            if (bitmap != null) {
                lever.cancelRequest(target)
                LeverDrawable.setBitmap(
                    target,
                    lever.context,
                    bitmap,
                    LoadedFrom.MEMORY,
                    noFade,
                    lever.indicatorsEnabled
                )
                if (lever.isLoggingEnabled) {
                    log(OWNER_MAIN, VERB_COMPLETED, request.plainId(), "from " + LoadedFrom.MEMORY)
                }
                callback?.onSuccess()
                return
            }
        }
        if (setPlaceholder) {
            LeverDrawable.setPlaceholder(target, getPlaceholderDrawable())
        }
        val action: Action<*> = ImageViewAction(
            lever, target, request, memoryPolicy, networkPolicy, errorResId,
            errorDrawable, requestKey, tag, callback, noFade
        )
        lever!!.enqueueAndSubmit(action)
    }

    private fun getPlaceholderDrawable(): Drawable? {
        return if (placeholderResId != 0) {
            if (Build.VERSION.SDK_INT >= 21) {
                lever!!.context.getDrawable(placeholderResId)
            } else if (Build.VERSION.SDK_INT >= 16) {
                lever!!.context.resources.getDrawable(placeholderResId, null)
            } else {
                val value = TypedValue()
                lever!!.context.resources.getValue(placeholderResId, value, true)
                lever.context.resources.getDrawable(value.resourceId, null)
            }
        } else {
            placeholderDrawable // This may be null which is expected and desired behavior.
        }
    }

    /** Create the request optionally passing it through the request transformer.  */
    private fun createRequest(started: Long): Request {
        val id = nextId.getAndIncrement()
        val request = data.build()
        request.id = id
        request.started = started
        val loggingEnabled = lever!!.isLoggingEnabled
        if (loggingEnabled) {
            log(OWNER_MAIN, VERB_CREATED, request.plainId(), request.toString())
        }
        val transformed = lever.transformRequest(request)
        if (transformed != request) { // If the request was changed, copy over the id and timestamp from the original.
            transformed.id = id
            transformed.started = started
            if (loggingEnabled) {
                log(OWNER_MAIN, VERB_CHANGED, transformed.logId(), "into $transformed")
            }
        }
        return transformed
    }

    private fun performRemoteViewInto(action: RemoteViewsAction) {
        if (MemoryPolicy.shouldReadFromMemoryCache(memoryPolicy)) {
            val bitmap = lever!!.quickMemoryCacheCheck(action.key)
            if (bitmap != null) {
                action.complete(bitmap, LoadedFrom.MEMORY)
                return
            }
        }
        if (placeholderResId != 0) {
            action.setImageResource(placeholderResId)
        }
        lever!!.enqueueAndSubmit(action)
    }

    companion object {
        private val nextId =
            AtomicInteger()
    }
}
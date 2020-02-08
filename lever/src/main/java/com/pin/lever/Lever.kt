package com.pin.lever

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Process
import android.support.annotation.DrawableRes
import android.support.annotation.IdRes
import android.widget.ImageView
import android.widget.RemoteViews
import com.pin.lever.Action.RequestWeakReference
import com.pin.lever.Lever
import com.pin.lever.Lever.Builder
import com.pin.lever.MemoryPolicy.Companion.shouldReadFromMemoryCache
import com.pin.lever.RemoteViewsAction.RemoteViewsTarget
import com.pin.lever.Utils.OWNER_MAIN
import com.pin.lever.Utils.THREAD_LEAK_CLEANING_MS
import com.pin.lever.Utils.VERB_COMPLETED
import com.pin.lever.Utils.VERB_ERRORED
import com.pin.lever.Utils.VERB_RESUMED
import com.pin.lever.Utils.THREAD_PREFIX
import com.pin.lever.Utils.VERB_CANCELED
import com.pin.lever.Utils.checkMain
import com.pin.lever.Utils.log
import java.io.File
import java.lang.ref.ReferenceQueue
import java.util.*
import java.util.concurrent.ExecutorService

/**
 * Image downloading, transformation, and caching manager.
 *
 *
 * Use [.instance] for the global singleton instance
 * or construct your own instance with [Builder].
 */
class Lever internal constructor(
    val context: Context,
    val dispatcher: Dispatcher,
    val cache: Cache,
    private val listener: Listener?,
    private val requestTransformer: RequestTransformer?,
    extraRequestHandlers: List<RequestHandler>?,
    stats: Stats,
    val defaultBitmapConfig: Bitmap.Config?,
    indicatorsEnabled: Boolean,
    loggingEnabled: Boolean
) {
    /** Callbacks for Lever events.  */
    interface Listener {
        /**
         * Invoked when an image has failed to load. This is useful for reporting image failures to a
         * remote analytics service, for example.
         */
        fun onImageLoadFailed(
            lever: Lever?,
            uri: Uri?,
            exception: Exception?
        )
    }

    /**
     * A transformer that is called immediately before every request is submitted. This can be used to
     * modify any information about a request.
     *
     *
     * For example, if you use a CDN you can change the hostname for the image based on the current
     * location of the user in order to instance faster download speeds.
     *
     *
     * **NOTE:** This is a beta feature. The API is subject to change in a backwards incompatible
     * way at any time.
     */
    interface RequestTransformer {
        /**
         * Transform a request before it is submitted to be processed.
         *
         * @return The original request or a new request to replace it. Must not be null.
         */
        fun transformRequest(request: Request): Request

        companion object {
            /** A [RequestTransformer] which returns the original request.  */
            val IDENTITY: RequestTransformer = object : RequestTransformer {
                override fun transformRequest(request: Request): Request {
                    return request
                }
            }
        }
    }

    /**
     * The priority of a request.
     *
     * @see RequestCreator.priority
     */
    enum class Priority {
        LOW, NORMAL, HIGH
    }

    private val cleanupThread: CleanupThread
    val requestHandlers: List<RequestHandler>
    val stats: Stats
    val targetToAction: MutableMap<Any, Action<*>>
    val targetToDeferredRequestCreator: MutableMap<ImageView, DeferredRequestCreator?>
    @JvmField
    val referenceQueue: ReferenceQueue<Any>
    @JvmField
    var indicatorsEnabled: Boolean
    /** `true` if debug logging is enabled.  */
    /**
     * Toggle whether debug logging is enabled.
     *
     *
     * **WARNING:** Enabling this will result in excessive object allocation. This should be only
     * be used for debugging Lever behavior. Do NOT pass `BuildConfig.DEBUG`.
     */
    // Public API.
    @Volatile
    var isLoggingEnabled: Boolean
    var shutdown = false
    /** Cancel any existing requests for the specified target [ImageView].  */
    fun cancelRequest(view: ImageView) { // checkMain() is called from cancelExistingRequest()
        requireNotNull(view) { "view cannot be null." }
        cancelExistingRequest(view)
    }

    /** Cancel any existing requests for the specified [Target] instance.  */
    fun cancelRequest(target: Target) { // checkMain() is called from cancelExistingRequest()
        requireNotNull(target) { "target cannot be null." }
        cancelExistingRequest(target)
    }

    /**
     * Cancel any existing requests for the specified [RemoteViews] target with the given `viewId`.
     */
    fun cancelRequest(remoteViews: RemoteViews, @IdRes viewId: Int) { // checkMain() is called from cancelExistingRequest()
        requireNotNull(remoteViews) { "remoteViews cannot be null." }
        cancelExistingRequest(RemoteViewsTarget(remoteViews, viewId))
    }

    /**
     * Cancel any existing requests with given tag. You can set a tag
     * on new requests with [RequestCreator.tag].
     *
     * @see RequestCreator.tag
     */
    fun cancelTag(tag: Any) {
        checkMain()
        requireNotNull(tag) { "Cannot cancel requests with null tag." }
        val actions: List<Action<*>> =
            ArrayList(targetToAction.values)
        run {
            var i = 0
            val n = actions.size
            while (i < n) {
                val action = actions[i]
                if (tag == action.tag) {
                    cancelExistingRequest(action.getTarget())
                }
                i++
            }
        }
        val deferredRequestCreators: List<DeferredRequestCreator?> =
            ArrayList(targetToDeferredRequestCreator.values)
        var i = 0
        val n = deferredRequestCreators.size
        while (i < n) {
            val deferredRequestCreator = deferredRequestCreators[i]
            if (tag == deferredRequestCreator!!.tag) {
                deferredRequestCreator.cancel()
            }
            i++
        }
    }

    /**
     * Pause existing requests with the given tag. Use [.resumeTag]
     * to resume requests with the given tag.
     *
     * @see .resumeTag
     * @see RequestCreator.tag
     */
    fun pauseTag(tag: Any) {
        requireNotNull(tag) { "tag == null" }
        dispatcher.dispatchPauseTag(tag)
    }

    /**
     * Resume paused requests with the given tag. Use [.pauseTag]
     * to pause requests with the given tag.
     *
     * @see .pauseTag
     * @see RequestCreator.tag
     */
    fun resumeTag(tag: Any) {
        requireNotNull(tag) { "tag == null" }
        dispatcher.dispatchResumeTag(tag)
    }

    /**
     * Start an image request using the specified URI.
     *
     *
     * Passing `null` as a `uri` will not trigger any request but will set a placeholder,
     * if one is specified.
     *
     * @see .load
     * @see .load
     * @see .load
     */
    fun load(uri: Uri?): RequestCreator {
        return RequestCreator(this, uri, 0)
    }

    /**
     * Start an image request using the specified path. This is a convenience method for calling
     * [.load].
     *
     *
     * This path may be a remote URL, file resource (prefixed with `file:`), content resource
     * (prefixed with `content:`), or android resource (prefixed with `android.resource:`.
     *
     *
     * Passing `null` as a `path` will not trigger any request but will set a
     * placeholder, if one is specified.
     *
     * @see .load
     * @see .load
     * @see .load
     * @throws IllegalArgumentException if `path` is empty or blank string.
     */
    fun load(path: String?): RequestCreator {
        if (path == null) {
            return RequestCreator(this, null, 0)
        }
        require(path.trim { it <= ' ' }.length != 0) { "Path must not be empty." }
        return load(Uri.parse(path))
    }

    /**
     * Start an image request using the specified image file. This is a convenience method for
     * calling [.load].
     *
     *
     * Passing `null` as a `file` will not trigger any request but will set a
     * placeholder, if one is specified.
     *
     *
     * Equivalent to calling [load(Uri.fromFile(file))][.load].
     *
     * @see .load
     * @see .load
     * @see .load
     */
    fun load(file: File): RequestCreator {
        return if (file == null) {
            RequestCreator(this, null, 0)
        } else load(Uri.fromFile(file))
    }

    /**
     * Start an image request using the specified drawable resource ID.
     *
     * @see .load
     * @see .load
     * @see .load
     */
    fun load(@DrawableRes resourceId: Int): RequestCreator {
        require(resourceId != 0) { "Resource ID must not be zero." }
        return RequestCreator(this, null, resourceId)
    }

    /**
     * Invalidate all memory cached images for the specified `uri`.
     *
     * @see .invalidate
     * @see .invalidate
     */
    fun invalidate(uri: Uri?) {
        if (uri != null) {
            cache.clearKeyUri(uri.toString())
        }
    }

    /**
     * Invalidate all memory cached images for the specified `path`. You can also pass a
     * [stable key][RequestCreator.stableKey].
     *
     * @see .invalidate
     * @see .invalidate
     */
    fun invalidate(path: String?) {
        if (path != null) {
            invalidate(Uri.parse(path))
        }
    }

    /**
     * Invalidate all memory cached images for the specified `file`.
     *
     * @see .invalidate
     * @see .invalidate
     */
    fun invalidate(file: File) {
        requireNotNull(file) { "file == null" }
        invalidate(Uri.fromFile(file))
    }

    /** Toggle whether to display debug indicators on images.  */
    fun setIndicatorsEnabled(enabled: Boolean) {
        indicatorsEnabled = enabled
    }

    /** `true` if debug indicators should are displayed on images.  */
    fun areIndicatorsEnabled(): Boolean {
        return indicatorsEnabled
    }

    /**
     * Creates a [StatsSnapshot] of the current stats for this instance.
     *
     *
     * **NOTE:** The snapshot may not always be completely up-to-date if requests are still in
     * progress.
     */
    val snapshot: StatsSnapshot
        get() = stats.createSnapshot()

    /** Stops this instance from accepting further requests.  */
    fun shutdown() {
        if (this === singleton) {
            throw UnsupportedOperationException("Default singleton instance cannot be shutdown.")
        }
        if (shutdown) {
            return
        }
        cache.clear()
        cleanupThread.shutdown()
        stats.shutdown()
        dispatcher.shutdown()
        for (deferredRequestCreator in targetToDeferredRequestCreator.values) {
            deferredRequestCreator!!.cancel()
        }
        targetToDeferredRequestCreator.clear()
        shutdown = true
    }

    fun transformRequest(request: Request): Request {
        return requestTransformer!!.transformRequest(request)
            ?: throw IllegalStateException(
                "Request transformer "
                        + requestTransformer.javaClass.canonicalName
                        + " returned null for "
                        + request
            )
    }

    fun defer(
        view: ImageView,
        request: DeferredRequestCreator?
    ) { // If there is already a deferred request, cancel it.
        if (targetToDeferredRequestCreator.containsKey(view)) {
            cancelExistingRequest(view)
        }
        targetToDeferredRequestCreator[view] = request
    }

    fun enqueueAndSubmit(action: Action<*>) {
        val target = action.getTarget()
        if (target != null && targetToAction[target] !== action) { // This will also check we are on the main thread.
            cancelExistingRequest(target)
            targetToAction[target] = action
        }
        submit(action)
    }

    fun submit(action: Action<*>?) {
        dispatcher.dispatchSubmit(action)
    }

    fun quickMemoryCacheCheck(key: String?): Bitmap? {
        val cached = cache[key]
        if (cached != null) {
            stats.dispatchCacheHit()
        } else {
            stats.dispatchCacheMiss()
        }
        return cached
    }

    fun complete(hunter: BitmapHunter) {
        val single = hunter.action
        val joined = hunter.getActions()
        val hasMultiple = joined != null && !joined.isEmpty()
        val shouldDeliver = single != null || hasMultiple
        if (!shouldDeliver) {
            return
        }
        val uri = hunter.data.uri
        val exception = hunter.exception
        val result = hunter.result
        val from = hunter.loadedFrom
        single?.let { deliverAction(result, from, it, exception) }
        if (hasMultiple) {
            var i = 0
            val n = joined!!.size
            while (i < n) {
                val join = joined[i]
                deliverAction(result, from, join, exception)
                i++
            }
        }
        if (listener != null && exception != null) {
            listener.onImageLoadFailed(this, uri, exception)
        }
    }

    fun resumeAction(action: Action<*>) {
        var bitmap: Bitmap? = null
        if (shouldReadFromMemoryCache(action.memoryPolicy)) {
            bitmap = quickMemoryCacheCheck(action.key)
        }
        if (bitmap != null) { // Resumed action is cached, complete immediately.
            deliverAction(bitmap, LoadedFrom.MEMORY, action, null)
            if (isLoggingEnabled) {
                log(OWNER_MAIN, VERB_COMPLETED, action.request!!.logId(), "from " + LoadedFrom.MEMORY)
            }
        } else { // Re-submit the action to the executor.
            enqueueAndSubmit(action)
            if (isLoggingEnabled) {
                log(OWNER_MAIN, VERB_RESUMED, action.request!!.logId())
            }
        }
    }

    private fun deliverAction(
        result: Bitmap?,
        from: LoadedFrom?,
        action: Action<*>,
        e: Exception?
    ) {
        if (action.isCancelled) {
            return
        }
        if (!action.willReplay()) {
            targetToAction.remove(action.getTarget())
        }
        if (result != null) {
            if (from == null) {
                throw AssertionError("LoadedFrom cannot be null.")
            }
            action.complete(result, from)
            if (isLoggingEnabled) {
                log(OWNER_MAIN, VERB_COMPLETED, action.request!!.logId(), "from $from")
            }
        } else {
            action.error(e)
            if (isLoggingEnabled) {
                log(OWNER_MAIN, VERB_ERRORED, action.request!!.logId(), e!!.message)
            }
        }
    }

    fun cancelExistingRequest(target: Any?) {
        checkMain()
        val action = targetToAction.remove(target)
        if (action != null) {
            action.cancel()
            dispatcher.dispatchCancel(action)
        }
        if (target is ImageView) {
            val deferredRequestCreator =
                targetToDeferredRequestCreator.remove(target)
            deferredRequestCreator?.cancel()
        }
    }

    /**
     * When the target of an action is weakly reachable but the request hasn't been canceled, it
     * gets added to the reference queue. This thread empties the reference queue and cancels the
     * request.
     */
    private class CleanupThread internal constructor(
        private val referenceQueue: ReferenceQueue<Any>,
        private val handler: Handler
    ) : Thread() {
        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            while (true) {
                try { // Prior to Android 5.0, even when there is no local variable, the result from
// remove() & obtainMessage() is kept as a stack local variable.
// We're forcing this reference to be cleared and replaced by looping every second
// when there is nothing to do.
// This behavior has been tested and reproduced with heap dumps.
                    val remove =
                        referenceQueue.remove(THREAD_LEAK_CLEANING_MS.toLong()) as RequestWeakReference<*>?
                    val message = handler.obtainMessage()
                    if (remove != null) {
                        message.what = Dispatcher.REQUEST_GCED
                        message.obj = remove.action
                        handler.sendMessage(message)
                    } else {
                        message.recycle()
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    handler.post { throw RuntimeException(e) }
                    break
                }
            }
        }

        fun shutdown() {
            interrupt()
        }

        init {
            isDaemon = true
            name = THREAD_PREFIX + "refQueue"
        }
    }

    /** Fluent API for creating [Lever] instances.  */
    // Public API.
    class Builder(context: Context) {
        private val context: Context
        private var downloader: Downloader? = null
        private var service: ExecutorService? = null
        private var cache: Cache? = null
        private var listener: Listener? = null
        private var transformer: RequestTransformer? = null
        private var requestHandlers: MutableList<RequestHandler>? = null
        private var defaultBitmapConfig: Bitmap.Config? = null
        private var indicatorsEnabled = false
        private var loggingEnabled = false
        /**
         * Specify the default [Bitmap.Config] used when decoding images. This can be overridden
         * on a per-request basis using [config(..)][RequestCreator.config].
         */
        fun defaultBitmapConfig(bitmapConfig: Bitmap.Config): Builder {
            requireNotNull(bitmapConfig) { "Bitmap config must not be null." }
            defaultBitmapConfig = bitmapConfig
            return this
        }

        /** Specify the [Downloader] that will be used for downloading images.  */
        fun downloader(downloader: Downloader): Builder {
            requireNotNull(downloader) { "Downloader must not be null." }
            check(this.downloader == null) { "Downloader already set." }
            this.downloader = downloader
            return this
        }

        /**
         * Specify the executor service for loading images in the background.
         *
         *
         * Note: Calling [shutdown()][Lever.shutdown] will not shutdown supplied executors.
         */
        fun executor(executorService: ExecutorService): Builder {
            requireNotNull(executorService) { "Executor service must not be null." }
            check(service == null) { "Executor service already set." }
            service = executorService
            return this
        }

        /** Specify the memory cache used for the most recent images.  */
        fun memoryCache(memoryCache: Cache): Builder {
            requireNotNull(memoryCache) { "Memory cache must not be null." }
            check(cache == null) { "Memory cache already set." }
            cache = memoryCache
            return this
        }

        /** Specify a listener for interesting events.  */
        fun listener(listener: Listener): Builder {
            requireNotNull(listener) { "Listener must not be null." }
            check(this.listener == null) { "Listener already set." }
            this.listener = listener
            return this
        }

        /**
         * Specify a transformer for all incoming requests.
         *
         *
         * **NOTE:** This is a beta feature. The API is subject to change in a backwards incompatible
         * way at any time.
         */
        fun requestTransformer(transformer: RequestTransformer): Builder {
            requireNotNull(transformer) { "Transformer must not be null." }
            check(this.transformer == null) { "Transformer already set." }
            this.transformer = transformer
            return this
        }

        /** Register a [RequestHandler].  */
        fun addRequestHandler(requestHandler: RequestHandler): Builder {
            requireNotNull(requestHandler) { "RequestHandler must not be null." }
            if (requestHandlers == null) {
                requestHandlers = ArrayList()
            }
            check(!requestHandlers!!.contains(requestHandler)) { "RequestHandler already registered." }
            requestHandlers!!.add(requestHandler)
            return this
        }

        /** Toggle whether to display debug indicators on images.  */
        fun indicatorsEnabled(enabled: Boolean): Builder {
            indicatorsEnabled = enabled
            return this
        }

        /**
         * Toggle whether debug logging is enabled.
         *
         *
         * **WARNING:** Enabling this will result in excessive object allocation. This should be only
         * be used for debugging purposes. Do NOT pass `BuildConfig.DEBUG`.
         */
        fun loggingEnabled(enabled: Boolean): Builder {
            loggingEnabled = enabled
            return this
        }

        /** Create the [Lever] instance.  */
        fun build(): Lever {
            val context = context
            if (downloader == null) {
                downloader = OkHttp3Downloader(context)
            }
            if (cache == null) {
                cache = LruCache(context)
            }
            if (service == null) {
                service = LeverExecutorService()
            }
            if (transformer == null) {
                transformer = RequestTransformer.IDENTITY
            }
            val stats = Stats(cache!!)
            val dispatcher = Dispatcher(
                context,
                service!!,
                HANDLER,
                downloader!!,
                cache!!,
                stats
            )
            return Lever(
                context, dispatcher, cache!!, listener, transformer, requestHandlers, stats,
                defaultBitmapConfig, indicatorsEnabled, loggingEnabled
            )
        }

        /** Start building a new [Lever] instance.  */
        init {
            requireNotNull(context) { "Context must not be null." }
            this.context = context.applicationContext
        }
    }

    /** Describes where the image was loaded from.  */
    enum class LoadedFrom(val debugColor: Int) {
        MEMORY(Color.GREEN), DISK(Color.BLUE), NETWORK(Color.RED);

    }

    companion object {
        const val TAG = "Lever"
        @JvmField
        val HANDLER: Handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    Dispatcher.HUNTER_BATCH_COMPLETE -> {
                        val batch =
                            msg.obj as List<BitmapHunter>
                        var i = 0
                        val n = batch.size
                        while (i < n) {
                            val hunter = batch[i]
                            hunter.lever.complete(hunter)
                            i++
                        }
                    }
                    Dispatcher.REQUEST_GCED -> {
                        val action = msg.obj as Action<*>
                        if (action.lever!!.isLoggingEnabled) {
                            log(
                                OWNER_MAIN,
                                VERB_CANCELED,
                                action.request!!.logId(),
                                "target got garbage collected"
                            )
                        }
                        action.lever!!.cancelExistingRequest(action.getTarget())
                    }
                    Dispatcher.REQUEST_BATCH_RESUME -> {
                        val batch =
                            msg.obj as List<Action<*>>
                        var i = 0
                        val n = batch.size
                        while (i < n) {
                            val action = batch[i]
                            action.lever!!.resumeAction(action)
                            i++
                        }
                    }
                    else -> throw AssertionError("Unknown handler message received: " + msg.what)
                }
            }
        }
        @SuppressLint("StaticFieldLeak")
        @Volatile
        var singleton: Lever? = null

        /**
         * The global [Lever] instance.
         *
         *
         * This instance is automatically initialized with defaults that are suitable to most
         * implementations.
         *
         *  * LRU memory cache of 15% the available application RAM
         *  * Disk cache of 2% storage space up to 50MB but no less than 5MB. (Note: this is only
         * available on API 14+ *or* if you are using a standalone library that provides a disk
         * cache on all API levels like OkHttp)
         *  * Three download threads for disk and network access.
         *
         *
         *
         * If these settings do not meet the requirements of your application you can construct your own
         * with full control over the configuration by using [Lever.Builder] to create a
         * [Lever] instance. You can either use this directly or by setting it as the global
         * instance with [.setSingletonInstance].
         */
        fun instance(): Lever? {
            if (singleton == null) {
                synchronized(Lever::class.java) {
                    if (singleton == null) {
                        checkNotNull(LeverProvider.context) { "context == null" }
                        singleton =
                            Builder(LeverProvider.context!!).build()
                    }
                }
            }
            return singleton
        }

        /**
         * Set the global instance returned from [.instance].
         *
         *
         * This method must be called before any calls to [.instance] and may only be called once.
         */
        fun setSingletonInstance(lever: Lever) {
            requireNotNull(lever) { "Lever must not be null." }
            synchronized(Lever::class.java) {
                check(singleton == null) { "Singleton instance already exists." }
                singleton = lever
            }
        }
    }

    init {
        val builtInHandlers = 7 // Adjust this as internal handlers are added or removed.
        val extraCount = extraRequestHandlers?.size ?: 0
        val allRequestHandlers: MutableList<RequestHandler> =
            ArrayList(builtInHandlers + extraCount)
        // ResourceRequestHandler needs to be the first in the list to avoid
// forcing other RequestHandlers to perform null checks on request.uri
// to cover the (request.resourceId != 0) case.
        allRequestHandlers.add(ResourceRequestHandler(context))
        if (extraRequestHandlers != null) {
            allRequestHandlers.addAll(extraRequestHandlers)
        }
        allRequestHandlers.add(ContactsPhotoRequestHandler(context))
        allRequestHandlers.add(MediaStoreRequestHandler(context))
        allRequestHandlers.add(ContentStreamRequestHandler(context))
        allRequestHandlers.add(AssetRequestHandler(context))
        allRequestHandlers.add(FileRequestHandler(context))
        allRequestHandlers.add(NetworkRequestHandler(dispatcher.downloader, stats))
        requestHandlers = Collections.unmodifiableList(allRequestHandlers)
        this.stats = stats
        targetToAction = WeakHashMap()
        targetToDeferredRequestCreator =
            WeakHashMap()
        this.indicatorsEnabled = indicatorsEnabled
        isLoggingEnabled = loggingEnabled
        referenceQueue = ReferenceQueue()
        cleanupThread = CleanupThread(referenceQueue, HANDLER)
        cleanupThread.start()
    }
}
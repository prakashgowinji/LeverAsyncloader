package com.pin.lever

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.*
import com.pin.lever.MemoryPolicy.Companion.shouldWriteToMemoryCache
import com.pin.lever.NetworkPolicy
import com.pin.lever.NetworkRequestHandler.ContentLengthException
import com.pin.lever.Utils.OWNER_DISPATCHER
import com.pin.lever.Utils.VERB_BATCHED
import com.pin.lever.Utils.VERB_CANCELED
import com.pin.lever.Utils.VERB_ENQUEUED
import com.pin.lever.Utils.VERB_IGNORED
import com.pin.lever.Utils.VERB_PAUSED
import com.pin.lever.Utils.VERB_REPLAYING
import com.pin.lever.Utils.VERB_RETRYING
import com.pin.lever.Utils.VERB_DELIVERED
import com.pin.lever.Utils.flushStackLocalLeaks
import com.pin.lever.Utils.getLogIdsForHunter
import com.pin.lever.Utils.getService
import com.pin.lever.Utils.hasPermission
import com.pin.lever.Utils.isAirplaneModeOn
import com.pin.lever.Utils.log
import java.util.*
import java.util.concurrent.ExecutorService

class Dispatcher(
    context: Context,
    service: ExecutorService,
    mainThreadHandler: Handler,
    downloader: Downloader,
    cache: Cache,
    stats: Stats
) {
    val dispatcherThread: DispatcherThread
    val context: Context
    val service: ExecutorService
    val downloader: Downloader
    val hunterMap: MutableMap<String, BitmapHunter?>
    val failedActions: MutableMap<Any?, Action<*>>
    val pausedActions: MutableMap<Any?, Action<*>>
    val pausedTags: MutableSet<Any>
    val handler: Handler
    val mainThreadHandler: Handler
    val cache: Cache
    val stats: Stats
    val batch: MutableList<BitmapHunter>
    val receiver: NetworkBroadcastReceiver
    val scansNetworkChanges: Boolean
    var airplaneMode: Boolean
    fun shutdown() { // Shutdown the thread pool only if it is the one created by Lever.
        (service as? LeverExecutorService)?.shutdown()
        downloader.shutdown()
        dispatcherThread.quit()
        // Unregister network broadcast receiver on the main thread.
        Lever.HANDLER.post { receiver.unregister() }
    }

    fun dispatchSubmit(action: Action<*>?) {
        handler.sendMessage(
            handler.obtainMessage(
                REQUEST_SUBMIT,
                action
            )
        )
    }

    fun dispatchCancel(action: Action<*>?) {
        handler.sendMessage(
            handler.obtainMessage(
                REQUEST_CANCEL,
                action
            )
        )
    }

    fun dispatchPauseTag(tag: Any?) {
        handler.sendMessage(
            handler.obtainMessage(
                TAG_PAUSE,
                tag
            )
        )
    }

    fun dispatchResumeTag(tag: Any?) {
        handler.sendMessage(
            handler.obtainMessage(
                TAG_RESUME,
                tag
            )
        )
    }

    fun dispatchComplete(hunter: BitmapHunter?) {
        handler.sendMessage(
            handler.obtainMessage(
                HUNTER_COMPLETE,
                hunter
            )
        )
    }

    fun dispatchRetry(hunter: BitmapHunter?) {
        handler.sendMessageDelayed(
            handler.obtainMessage(
                HUNTER_RETRY,
                hunter
            ), RETRY_DELAY.toLong()
        )
    }

    fun dispatchFailed(hunter: BitmapHunter?) {
        handler.sendMessage(
            handler.obtainMessage(
                HUNTER_DECODE_FAILED,
                hunter
            )
        )
    }

    fun dispatchNetworkStateChange(info: NetworkInfo?) {
        handler.sendMessage(
            handler.obtainMessage(
                NETWORK_STATE_CHANGE,
                info
            )
        )
    }

    fun dispatchAirplaneModeChange(airplaneMode: Boolean) {
        handler.sendMessage(
            handler.obtainMessage(
                AIRPLANE_MODE_CHANGE,
                if (airplaneMode) AIRPLANE_MODE_ON else AIRPLANE_MODE_OFF,
                0
            )
        )
    }

    @JvmOverloads
    fun performSubmit(
        action: Action<*>,
        dismissFailed: Boolean = true
    ) {
        if (pausedTags.contains(action.tag)) {
            pausedActions[action.target] = action
            if (action.lever!!.isLoggingEnabled) {
                log(
                    OWNER_DISPATCHER, VERB_PAUSED, action.request!!.logId(),
                    "because tag '" + action.tag + "' is paused"
                )
            }
            return
        }
        var hunter = hunterMap[action.key]
        if (hunter != null) {
            hunter.attach(action)
            return
        }
        if (service.isShutdown) {
            if (action.lever!!.isLoggingEnabled) {
                log(OWNER_DISPATCHER, VERB_IGNORED, action.request!!.logId(), "because shut down")
            }
            return
        }
        hunter = BitmapHunter.forRequest(action.lever!!, this, cache, stats, action)
        hunter.future = service.submit(hunter)
        hunterMap[action.key] = hunter
        if (dismissFailed) {
            failedActions.remove(action.getTarget())
        }
        if (action.lever.isLoggingEnabled) {
            log(OWNER_DISPATCHER, VERB_ENQUEUED, action.request!!.logId())
        }
    }

    fun performCancel(action: Action<*>) {
        val key = action.key
        val hunter = hunterMap[key]
        if (hunter != null) {
            hunter.detach(action)
            if (hunter.cancel()) {
                hunterMap.remove(key)
                if (action.lever!!.isLoggingEnabled) {
                    log(OWNER_DISPATCHER, VERB_CANCELED, action.request!!.logId())
                }
            }
        }
        if (pausedTags.contains(action.tag)) {
            pausedActions.remove(action.getTarget())
            if (action.lever!!.isLoggingEnabled) {
                log(
                    OWNER_DISPATCHER, VERB_CANCELED, action.request!!.logId(),
                    "because paused request got canceled"
                )
            }
        }
        val remove = failedActions.remove(action.getTarget())
        if (remove != null && remove.lever!!.isLoggingEnabled) {
            log(OWNER_DISPATCHER, VERB_CANCELED, remove.request!!.logId(), "from replaying")
        }
    }

    fun performPauseTag(tag: Any) { // Trying to pause a tag that is already paused.
        if (!pausedTags.add(tag)) {
            return
        }
        // Go through all active hunters and detach/pause the requests
// that have the paused tag.
        val it = hunterMap.values.iterator()
        while (it.hasNext()) {
            val hunter = it.next()
            val loggingEnabled = hunter!!.lever.isLoggingEnabled
            val single = hunter.action
            val joined = hunter.getActions()
            val hasMultiple = joined != null && !joined.isEmpty()
            // Hunter has no requests, bail early.
            if (single == null && !hasMultiple) {
                continue
            }
            if (single != null && single.tag == tag) {
                hunter.detach(single)
                pausedActions[single.getTarget()] = single
                if (loggingEnabled) {
                    log(
                        OWNER_DISPATCHER, VERB_PAUSED, single.request!!.logId(),
                        "because tag '$tag' was paused"
                    )
                }
            }
            if (hasMultiple) {
                for (i in joined!!.indices.reversed()) {
                    val action = joined[i]
                    if (action.tag != tag) {
                        continue
                    }
                    hunter.detach(action)
                    pausedActions[action.getTarget()] = action
                    if (loggingEnabled) {
                        log(
                            OWNER_DISPATCHER, VERB_PAUSED, action.request!!.logId(),
                            "because tag '$tag' was paused"
                        )
                    }
                }
            }
            // Check if the hunter can be cancelled in case all its requests
// had the tag being paused here.
            if (hunter.cancel()) {
                it.remove()
                if (loggingEnabled) {
                    log(
                        OWNER_DISPATCHER,
                        VERB_CANCELED,
                        getLogIdsForHunter(hunter),
                        "all actions paused"
                    )
                }
            }
        }
    }

    fun performResumeTag(tag: Any) { // Trying to resume a tag that is not paused.
        if (!pausedTags.remove(tag)) {
            return
        }
        var batch: MutableList<Action<*>?>? = null
        val i =
            pausedActions.values.iterator()
        while (i.hasNext()) {
            val action = i.next()
            if (action.tag == tag) {
                if (batch == null) {
                    batch = ArrayList()
                }
                batch.add(action)
                i.remove()
            }
        }
        if (batch != null) {
            mainThreadHandler.sendMessage(
                mainThreadHandler.obtainMessage(
                    REQUEST_BATCH_RESUME,
                    batch
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun performRetry(hunter: BitmapHunter) {
        if (hunter.isCancelled) return
        if (service.isShutdown) {
            performError(hunter, false)
            return
        }
        var networkInfo: NetworkInfo? = null
        if (scansNetworkChanges) {
            val connectivityManager = getService<ConnectivityManager>(
                context,
                Context.CONNECTIVITY_SERVICE
            )
            networkInfo = connectivityManager.activeNetworkInfo
        }
        if (hunter.shouldRetry(airplaneMode, networkInfo)) {
            if (hunter.lever.isLoggingEnabled) {
                log(OWNER_DISPATCHER, VERB_RETRYING, getLogIdsForHunter(hunter))
            }
            if (hunter.exception is ContentLengthException) {
                hunter.networkPolicy = hunter.networkPolicy or NetworkPolicy.NO_CACHE.index
            }
            hunter.future = service.submit(hunter)
        } else { // Mark for replay only if we observe network info changes and support replay.
            val willReplay = scansNetworkChanges && hunter.supportsReplay()
            performError(hunter, willReplay)
            if (willReplay) {
                markForReplay(hunter)
            }
        }
    }

    fun performComplete(hunter: BitmapHunter) {
        if (shouldWriteToMemoryCache(hunter.memoryPolicy)) {
            cache[hunter.key] = hunter.result
        }
        hunterMap.remove(hunter.key)
        batch(hunter)
        if (hunter.lever.isLoggingEnabled) {
            log(OWNER_DISPATCHER, VERB_BATCHED, getLogIdsForHunter(hunter), "for completion")
        }
    }

    fun performBatchComplete() {
        val copy: List<BitmapHunter> = ArrayList(batch)
        batch.clear()
        mainThreadHandler.sendMessage(
            mainThreadHandler.obtainMessage(
                HUNTER_BATCH_COMPLETE,
                copy
            )
        )
        logBatch(copy)
    }

    fun performError(hunter: BitmapHunter, willReplay: Boolean) {
        if (hunter.lever.isLoggingEnabled) {
            log(
                OWNER_DISPATCHER, VERB_BATCHED, getLogIdsForHunter(hunter),
                "for error" + if (willReplay) " (will replay)" else ""
            )
        }
        hunterMap.remove(hunter.key)
        batch(hunter)
    }

    fun performAirplaneModeChange(airplaneMode: Boolean) {
        this.airplaneMode = airplaneMode
    }

    fun performNetworkStateChange(info: NetworkInfo?) {
        if (service is LeverExecutorService) {
            service.adjustThreadCount(info)
        }
        // Intentionally check only if isConnected() here before we flush out failed actions.
        if (info != null && info.isConnected) {
            flushFailedActions()
        }
    }

    private fun flushFailedActions() {
        if (!failedActions.isEmpty()) {
            val iterator =
                failedActions.values.iterator()
            while (iterator.hasNext()) {
                val action = iterator.next()
                iterator.remove()
                if (action.lever!!.isLoggingEnabled) {
                    log(OWNER_DISPATCHER, VERB_REPLAYING, action.request!!.logId())
                }
                performSubmit(action, false)
            }
        }
    }

    private fun markForReplay(hunter: BitmapHunter) {
        val action = hunter.action
        action?.let { markForReplay(it) }
        val joined = hunter.getActions()
        if (joined != null) {
            var i = 0
            val n = joined.size
            while (i < n) {
                val join = joined[i]
                markForReplay(join)
                i++
            }
        }
    }

    private fun markForReplay(action: Action<*>) {
        val target = action.getTarget()
        if (target != null) {
            action.willReplay = true
            failedActions[target] = action
        }
    }

    private fun batch(hunter: BitmapHunter) {
        if (hunter.isCancelled) {
            return
        }
        if (hunter.result != null) {
            hunter.result!!.prepareToDraw()
        }
        batch.add(hunter)
        if (!handler.hasMessages(HUNTER_DELAY_NEXT_BATCH)) {
            handler.sendEmptyMessageDelayed(
                HUNTER_DELAY_NEXT_BATCH,
                BATCH_DELAY.toLong()
            )
        }
    }

    private fun logBatch(copy: List<BitmapHunter>?) {
        if (copy == null || copy.isEmpty()) return
        val hunter = copy[0]
        val lever = hunter.lever
        if (lever.isLoggingEnabled) {
            val builder = StringBuilder()
            for (bitmapHunter in copy) {
                if (builder.length > 0) builder.append(", ")
                builder.append(getLogIdsForHunter(bitmapHunter))
            }
            log(OWNER_DISPATCHER, VERB_DELIVERED, builder.toString())
        }
    }

    private class DispatcherHandler internal constructor(
        looper: Looper?,
        private val dispatcher: Dispatcher
    ) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                REQUEST_SUBMIT -> {
                    val action = msg.obj as Action<*>
                    dispatcher.performSubmit(action)
                }
                REQUEST_CANCEL -> {
                    val action = msg.obj as Action<*>
                    dispatcher.performCancel(action)
                }
                TAG_PAUSE -> {
                    val tag = msg.obj
                    dispatcher.performPauseTag(tag)
                }
                TAG_RESUME -> {
                    val tag = msg.obj
                    dispatcher.performResumeTag(tag)
                }
                HUNTER_COMPLETE -> {
                    val hunter = msg.obj as BitmapHunter
                    dispatcher.performComplete(hunter)
                }
                HUNTER_RETRY -> {
                    val hunter = msg.obj as BitmapHunter
                    dispatcher.performRetry(hunter)
                }
                HUNTER_DECODE_FAILED -> {
                    val hunter = msg.obj as BitmapHunter
                    dispatcher.performError(hunter, false)
                }
                HUNTER_DELAY_NEXT_BATCH -> {
                    dispatcher.performBatchComplete()
                }
                NETWORK_STATE_CHANGE -> {
                    val info = msg.obj as NetworkInfo?
                    dispatcher.performNetworkStateChange(info)
                }
                AIRPLANE_MODE_CHANGE -> {
                    dispatcher.performAirplaneModeChange(msg.arg1 == AIRPLANE_MODE_ON)
                }
                else -> Lever.HANDLER.post { throw AssertionError("Unknown handler message received: " + msg.what) }
            }
        }

    }

    class DispatcherThread : HandlerThread(
        Utils.THREAD_PREFIX + DISPATCHER_THREAD_NAME,
        Process.THREAD_PRIORITY_BACKGROUND
    )

    class NetworkBroadcastReceiver(private val dispatcher: Dispatcher) :
        BroadcastReceiver() {
        fun register() {
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            if (dispatcher.scansNetworkChanges) {
                filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            }
            dispatcher.context.registerReceiver(this, filter)
        }

        fun unregister() {
            dispatcher.context.unregisterReceiver(this)
        }

        @SuppressLint("MissingPermission")
        override fun onReceive(
            context: Context,
            intent: Intent
        ) { // On some versions of Android this may be called with a null Intent,
// also without extras (getExtras() == null), in such case we use defaults.
            if (intent == null) {
                return
            }
            val action = intent.action
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED == action) {
                if (!intent.hasExtra(EXTRA_AIRPLANE_STATE)) {
                    return  // No airplane state, ignore it. Should we query Utils.isAirplaneModeOn?
                }
                dispatcher.dispatchAirplaneModeChange(
                    intent.getBooleanExtra(
                        EXTRA_AIRPLANE_STATE,
                        false
                    )
                )
            } else if (ConnectivityManager.CONNECTIVITY_ACTION == action) {
                val connectivityManager = getService<ConnectivityManager>(
                    context,
                    Context.CONNECTIVITY_SERVICE
                )
                dispatcher.dispatchNetworkStateChange(connectivityManager.activeNetworkInfo)
            }
        }

        companion object {
            const val EXTRA_AIRPLANE_STATE = "state"
        }

    }

    companion object {
        private const val RETRY_DELAY = 500
        private const val AIRPLANE_MODE_ON = 1
        private const val AIRPLANE_MODE_OFF = 0
        const val REQUEST_SUBMIT = 1
        const val REQUEST_CANCEL = 2
        const val REQUEST_GCED = 3
        const val HUNTER_COMPLETE = 4
        const val HUNTER_RETRY = 5
        const val HUNTER_DECODE_FAILED = 6
        const val HUNTER_DELAY_NEXT_BATCH = 7
        const val HUNTER_BATCH_COMPLETE = 8
        const val NETWORK_STATE_CHANGE = 9
        const val AIRPLANE_MODE_CHANGE = 10
        const val TAG_PAUSE = 11
        const val TAG_RESUME = 12
        const val REQUEST_BATCH_RESUME = 13
        private const val DISPATCHER_THREAD_NAME = "Dispatcher"
        private const val BATCH_DELAY = 200 // ms
    }

    init {
        dispatcherThread = DispatcherThread()
        dispatcherThread.start()
        flushStackLocalLeaks(dispatcherThread.looper)
        this.context = context
        this.service = service
        hunterMap = LinkedHashMap()
        failedActions = WeakHashMap()
        pausedActions = WeakHashMap()
        pausedTags = LinkedHashSet()
        handler = DispatcherHandler(dispatcherThread.looper, this)
        this.downloader = downloader
        this.mainThreadHandler = mainThreadHandler
        this.cache = cache
        this.stats = stats
        batch = ArrayList(4)
        airplaneMode = isAirplaneModeOn(this.context)
        scansNetworkChanges =
            hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)
        receiver = NetworkBroadcastReceiver(this)
        receiver.register()
    }
}
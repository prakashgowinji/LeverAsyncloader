package com.pin.lever

import android.annotation.TargetApi
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.*
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.provider.Settings
import android.util.Log
import okio.BufferedSource
import okio.ByteString
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ThreadFactory

internal object Utils {
    const val TAG = "QQQ Utils"
    const val THREAD_PREFIX = "Lever-"
    const val THREAD_IDLE_NAME = THREAD_PREFIX + "Idle"
    private const val LEVER_CACHE = "lever-cache"
    private const val KEY_PADDING = 50 // Determined by exact science.
    private const val MIN_DISK_CACHE_SIZE = 5 * 1024 * 1024 // 5MB
    private const val MAX_DISK_CACHE_SIZE = 50 * 1024 * 1024 // 50MB
    const val THREAD_LEAK_CLEANING_MS = 1000
    const val KEY_SEPARATOR = '\n'
    /** Thread confined to main thread for key creation.  */
    val MAIN_THREAD_KEY_BUILDER = StringBuilder()
    /** Logging  */
    const val OWNER_MAIN = "Main"
    const val OWNER_DISPATCHER = "Dispatcher"
    const val OWNER_HUNTER = "Hunter"
    const val VERB_CREATED = "created"
    const val VERB_CHANGED = "changed"
    const val VERB_IGNORED = "ignored"
    const val VERB_ENQUEUED = "enqueued"
    const val VERB_CANCELED = "canceled"
    const val VERB_BATCHED = "batched"
    const val VERB_RETRYING = "retrying"
    const val VERB_EXECUTING = "executing"
    const val VERB_DECODED = "decoded"
    const val VERB_TRANSFORMED = "transformed"
    const val VERB_JOINED = "joined"
    const val VERB_REMOVED = "removed"
    const val VERB_DELIVERED = "delivered"
    const val VERB_REPLAYING = "replaying"
    const val VERB_COMPLETED = "completed"
    const val VERB_ERRORED = "errored"
    const val VERB_PAUSED = "paused"
    const val VERB_RESUMED = "resumed"
    /* WebP file header
       0                   1                   2                   3
       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |      'R'      |      'I'      |      'F'      |      'F'      |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                           File Size                           |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |      'W'      |      'E'      |      'B'      |      'P'      |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    */
    private val WEBP_FILE_HEADER_RIFF = ByteString.encodeUtf8("RIFF")
    private val WEBP_FILE_HEADER_WEBP = ByteString.encodeUtf8("WEBP")
    @JvmStatic
    fun getBitmapBytes(bitmap: Bitmap): Int {
        val result =
            if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) bitmap.allocationByteCount else bitmap.byteCount
        check(result >= 0) { "Negative size: $bitmap" }
        return result
    }

    @JvmStatic
    fun <T> checkNotNull(value: T?, message: String?): T {
        if (value == null) {
            throw NullPointerException(message)
        }
        return value
    }

    @JvmStatic
    fun checkNotMain() {
        check(!isMain) { "Method call should not happen from the main thread." }
    }

    @JvmStatic
    fun checkMain() {
        check(isMain) { "Method call should happen from the main thread." }
    }

    val isMain: Boolean
        get() = Looper.getMainLooper().thread === Thread.currentThread()

    fun getLogIdsForHunter(hunter: BitmapHunter): String {
        return getLogIdsForHunter(hunter, "")
    }

    @JvmStatic
    fun getLogIdsForHunter(hunter: BitmapHunter, prefix: String?): String {
        val builder = StringBuilder(prefix!!)
        val action = hunter.action
        if (action != null) {
            builder.append(action.request!!.logId())
        }
        val actions = hunter.getActions()
        if (actions != null) {
            var i = 0
            val count = actions.size
            while (i < count) {
                if (i > 0 || action != null) builder.append(", ")
                builder.append(actions[i].request!!.logId())
                i++
            }
        }
        return builder.toString()
    }

    @JvmStatic
    @JvmOverloads
    fun log(
        owner: String?,
        verb: String?,
        logId: String?,
        extras: String? = ""
    ) {
        Log.d(
            TAG,
            String.format("%1$-11s %2$-12s %3\$s %4\$s", owner, verb, logId, extras)
        )
    }

    fun createKey(data: Request): String {
        val result =
            createKey(data, MAIN_THREAD_KEY_BUILDER)
        MAIN_THREAD_KEY_BUILDER.setLength(0)
        return result
    }

    @JvmStatic
    fun createKey(data: Request, builder: StringBuilder): String {
        if (data.stableKey != null) {
            builder.ensureCapacity(data.stableKey.length + KEY_PADDING)
            builder.append(data.stableKey)
        } else if (data.uri != null) {
            val path = data.uri.toString()
            builder.ensureCapacity(path.length + KEY_PADDING)
            builder.append(path)
        } else {
            builder.ensureCapacity(KEY_PADDING)
            builder.append(data.resourceId)
        }
        builder.append(KEY_SEPARATOR)
        if (data.rotationDegrees != 0f) {
            builder.append("rotation:").append(data.rotationDegrees)
            if (data.hasRotationPivot) {
                builder.append('@').append(data.rotationPivotX).append('x')
                    .append(data.rotationPivotY)
            }
            builder.append(KEY_SEPARATOR)
        }
        if (data.hasSize()) {
            builder.append("resize:").append(data.targetWidth).append('x').append(data.targetHeight)
            builder.append(KEY_SEPARATOR)
        }
        if (data.centerCrop) {
            builder.append("centerCrop:").append(data.centerCropGravity)
                .append(KEY_SEPARATOR)
        } else if (data.centerInside) {
            builder.append("centerInside").append(KEY_SEPARATOR)
        }
        if (data.transformations != null) {
            var i = 0
            val count = data.transformations!!.size
            val transformations: MutableList<Transformation>? = data.transformations
            while (i < count) {
                val key = transformations!![i].key()
                if(key != null) {
                    builder.append(key)
                    builder.append(KEY_SEPARATOR)
                    i++
                }
            }
        }
        return builder.toString()
    }

    @JvmStatic
    fun createDefaultCacheDir(context: Context): File {
        val cache = File(
            context.applicationContext.cacheDir,
            LEVER_CACHE
        )
        if (!cache.exists()) {
            cache.mkdirs()
        }
        return cache
    }

    @JvmStatic
    @TargetApi(VERSION_CODES.JELLY_BEAN_MR2)
    fun calculateDiskCacheSize(dir: File): Long {
        var size = MIN_DISK_CACHE_SIZE.toLong()
        try {
            val statFs = StatFs(dir.absolutePath)
            val blockCount =
                if (VERSION.SDK_INT < VERSION_CODES.JELLY_BEAN_MR2) statFs.blockCount.toLong() else statFs.blockCountLong
            val blockSize =
                if (VERSION.SDK_INT < VERSION_CODES.JELLY_BEAN_MR2) statFs.blockSize.toLong() else statFs.blockSizeLong
            val available = blockCount * blockSize
            // Target 2% of the total space.
            size = available / 50
        } catch (ignored: IllegalArgumentException) {
        }
        // Bound inside min/max size for disk cache.
        return Math.max(
            Math.min(
                size,
                MAX_DISK_CACHE_SIZE.toLong()
            ), MIN_DISK_CACHE_SIZE.toLong()
        )
    }

    @JvmStatic
    fun calculateMemoryCacheSize(context: Context): Int {
        val am = getService<ActivityManager>(
            context,
            Context.ACTIVITY_SERVICE
        )
        val largeHeap =
            context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0
        val memoryClass = if (largeHeap) am.largeMemoryClass else am.memoryClass
        // Target ~15% of the available heap.
        return (1024L * 1024L * memoryClass / 7).toInt()
    }

    @JvmStatic
    fun isAirplaneModeOn(context: Context): Boolean {
        val contentResolver = context.contentResolver
        return try {
            if (VERSION.SDK_INT < VERSION_CODES.JELLY_BEAN_MR1) {
                Settings.System.getInt(
                    contentResolver,
                    Settings.System.AIRPLANE_MODE_ON,
                    0
                ) != 0
            } else Settings.Global.getInt(
                contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) != 0
        } catch (e: NullPointerException) { // Some devices might crash here, assume that
// airplane mode is off.
            false
        } catch (e: SecurityException) {
            false
        }
    }

    @JvmStatic
    fun <T> getService(context: Context, service: String?): T {
        return context.getSystemService(service) as T
    }

    @JvmStatic
    fun hasPermission(
        context: Context,
        permission: String
    ): Boolean {
        return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    @Throws(IOException::class)
    fun isWebPFile(source: BufferedSource): Boolean {
        return (source.rangeEquals(0, WEBP_FILE_HEADER_RIFF)
                && source.rangeEquals(8, WEBP_FILE_HEADER_WEBP))
    }

    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun getResourceId(resources: Resources, data: Request): Int {
        if (data.resourceId != 0 || data.uri == null) {
            return data.resourceId
        }
        val pkg =
            data.uri.authority ?: throw FileNotFoundException("No package provided: " + data.uri)
        val id: Int
        val segments = data.uri.pathSegments
        id = if (segments == null || segments.isEmpty()) {
            throw FileNotFoundException("No path segments: " + data.uri)
        } else if (segments.size == 1) {
            try {
                segments[0].toInt()
            } catch (e: NumberFormatException) {
                throw FileNotFoundException("Last path segment is not a resource ID: " + data.uri)
            }
        } else if (segments.size == 2) {
            val type = segments[0]
            val name = segments[1]
            resources.getIdentifier(name, type, pkg)
        } else {
            throw FileNotFoundException("More than two path segments: " + data.uri)
        }
        return id
    }

    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun getResources(
        context: Context,
        data: Request
    ): Resources {
        if (data.resourceId != 0 || data.uri == null) {
            return context.resources
        }
        val pkg =
            data.uri.authority ?: throw FileNotFoundException("No package provided: " + data.uri)
        return try {
            val pm = context.packageManager
            pm.getResourcesForApplication(pkg)
        } catch (e: PackageManager.NameNotFoundException) {
            throw FileNotFoundException("Unable to obtain resources for package: " + data.uri)
        }
    }

    /**
     * Prior to Android 5, HandlerThread always keeps a stack local reference to the last message
     * that was sent to it. This method makes sure that stack local reference never stays there
     * for too long by sending new messages to it every second.
     */
    @JvmStatic
    fun flushStackLocalLeaks(looper: Looper?) {
        val handler: Handler = object : Handler(looper) {
            override fun handleMessage(msg: Message) {
                sendMessageDelayed(
                    obtainMessage(),
                    THREAD_LEAK_CLEANING_MS.toLong()
                )
            }
        }
        handler.sendMessageDelayed(
            handler.obtainMessage(),
            THREAD_LEAK_CLEANING_MS.toLong()
        )
    }

    internal class LeverThreadFactory : ThreadFactory {
        override fun newThread(r: Runnable): Thread {
            return LeverThread(r)
        }
    }

    private class LeverThread internal constructor(r: Runnable?) : Thread(r) {
        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            super.run()
        }
    }
}
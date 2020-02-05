package com.pin.lever

import android.graphics.Bitmap
import android.os.*
import com.pin.lever.Lever

class Stats(val cache: Cache) {
    val statsThread: HandlerThread
    val handler: Handler
    var cacheHits: Long = 0
    var cacheMisses: Long = 0
    var totalDownloadSize: Long = 0
    var totalOriginalBitmapSize: Long = 0
    var totalTransformedBitmapSize: Long = 0
    var averageDownloadSize: Long = 0
    var averageOriginalBitmapSize: Long = 0
    var averageTransformedBitmapSize: Long = 0
    var downloadCount = 0
    var originalBitmapCount = 0
    var transformedBitmapCount = 0
    fun dispatchBitmapDecoded(bitmap: Bitmap) {
        processBitmap(bitmap, BITMAP_DECODE_FINISHED)
    }

    fun dispatchBitmapTransformed(bitmap: Bitmap) {
        processBitmap(bitmap, BITMAP_TRANSFORMED_FINISHED)
    }

    fun dispatchDownloadFinished(size: Long) {
        handler.sendMessage(
            handler.obtainMessage(
                DOWNLOAD_FINISHED,
                size
            )
        )
    }

    fun dispatchCacheHit() {
        handler.sendEmptyMessage(CACHE_HIT)
    }

    fun dispatchCacheMiss() {
        handler.sendEmptyMessage(CACHE_MISS)
    }

    fun shutdown() {
        statsThread.quit()
    }

    fun performCacheHit() {
        cacheHits++
    }

    fun performCacheMiss() {
        cacheMisses++
    }

    fun performDownloadFinished(size: Long) {
        downloadCount++
        totalDownloadSize += size
        averageDownloadSize =
            getAverage(downloadCount, totalDownloadSize)
    }

    fun performBitmapDecoded(size: Long) {
        originalBitmapCount++
        totalOriginalBitmapSize += size
        averageOriginalBitmapSize =
            getAverage(originalBitmapCount, totalOriginalBitmapSize)
    }

    fun performBitmapTransformed(size: Long) {
        transformedBitmapCount++
        totalTransformedBitmapSize += size
        averageTransformedBitmapSize = getAverage(
            originalBitmapCount,
            totalTransformedBitmapSize
        )
    }

    fun createSnapshot(): StatsSnapshot {
        return StatsSnapshot(
            cache.maxSize(),
            cache.size(),
            cacheHits,
            cacheMisses,
            totalDownloadSize,
            totalOriginalBitmapSize,
            totalTransformedBitmapSize,
            averageDownloadSize,
            averageOriginalBitmapSize,
            averageTransformedBitmapSize,
            downloadCount,
            originalBitmapCount,
            transformedBitmapCount,
            System.currentTimeMillis()
        )
    }

    private fun processBitmap(
        bitmap: Bitmap,
        what: Int
    ) { // Never send bitmaps to the handler as they could be recycled before we process them.
        val bitmapSize = Utils.getBitmapBytes(bitmap)
        handler.sendMessage(handler.obtainMessage(what, bitmapSize, 0))
    }

    private class StatsHandler internal constructor(looper: Looper?, private val stats: Stats) :
        Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                CACHE_HIT -> stats.performCacheHit()
                CACHE_MISS -> stats.performCacheMiss()
                BITMAP_DECODE_FINISHED -> stats.performBitmapDecoded(
                    msg.arg1.toLong()
                )
                BITMAP_TRANSFORMED_FINISHED -> stats.performBitmapTransformed(
                    msg.arg1.toLong()
                )
                DOWNLOAD_FINISHED -> stats.performDownloadFinished(msg.obj as Long)
                else -> Lever.HANDLER.post { throw AssertionError("Unhandled stats message." + msg.what) }
            }
        }

    }

    companion object {
        private const val CACHE_HIT = 0
        private const val CACHE_MISS = 1
        private const val BITMAP_DECODE_FINISHED = 2
        private const val BITMAP_TRANSFORMED_FINISHED = 3
        private const val DOWNLOAD_FINISHED = 4
        private const val STATS_THREAD_NAME = Utils.THREAD_PREFIX + "Stats"
        private fun getAverage(count: Int, totalSize: Long): Long {
            return totalSize / count
        }
    }

    init {
        statsThread = HandlerThread(
            STATS_THREAD_NAME,
            Process.THREAD_PRIORITY_BACKGROUND
        )
        statsThread.start()
        Utils.flushStackLocalLeaks(statsThread.looper)
        handler = StatsHandler(statsThread.looper, this)
    }
}
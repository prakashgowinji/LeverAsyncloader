package com.pin.lever

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

class StatsSnapshot(
    val maxSize: Int,
    val size: Int,
    val cacheHits: Long,
    val cacheMisses: Long,
    val totalDownloadSize: Long,
    val totalOriginalBitmapSize: Long,
    val totalTransformedBitmapSize: Long,
    val averageDownloadSize: Long,
    val averageOriginalBitmapSize: Long,
    val averageTransformedBitmapSize: Long,
    val downloadCount: Int,
    val originalBitmapCount: Int,
    val transformedBitmapCount: Int,
    val timeStamp: Long
) {
    /** Prints out this [StatsSnapshot] into log.  */
    fun dump() {
        val logWriter = StringWriter()
        dump(PrintWriter(logWriter))
        Log.i(TAG, logWriter.toString())
    }

    /** Prints out this [StatsSnapshot] with the the provided [PrintWriter].  */
    fun dump(writer: PrintWriter) {
        writer.println("===============BEGIN LEVER STATS ===============")
        writer.println("Memory Cache Stats")
        writer.print("  Max Cache Size: ")
        writer.println(maxSize)
        writer.print("  Cache Size: ")
        writer.println(size)
        writer.print("  Cache % Full: ")
        writer.println(Math.ceil(size.toFloat() / maxSize * 100.toDouble()).toInt())
        writer.print("  Cache Hits: ")
        writer.println(cacheHits)
        writer.print("  Cache Misses: ")
        writer.println(cacheMisses)
        writer.println("Network Stats")
        writer.print("  Download Count: ")
        writer.println(downloadCount)
        writer.print("  Total Download Size: ")
        writer.println(totalDownloadSize)
        writer.print("  Average Download Size: ")
        writer.println(averageDownloadSize)
        writer.println("Bitmap Stats")
        writer.print("  Total Bitmaps Decoded: ")
        writer.println(originalBitmapCount)
        writer.print("  Total Bitmap Size: ")
        writer.println(totalOriginalBitmapSize)
        writer.print("  Total Transformed Bitmaps: ")
        writer.println(transformedBitmapCount)
        writer.print("  Total Transformed Bitmap Size: ")
        writer.println(totalTransformedBitmapSize)
        writer.print("  Average Bitmap Size: ")
        writer.println(averageOriginalBitmapSize)
        writer.print("  Average Transformed Bitmap Size: ")
        writer.println(averageTransformedBitmapSize)
        writer.println("===============END LEVER STATS ===============")
        writer.flush()
    }

    override fun toString(): String {
        return ("StatsSnapshot{"
                + "maxSize="
                + maxSize
                + ", size="
                + size
                + ", cacheHits="
                + cacheHits
                + ", cacheMisses="
                + cacheMisses
                + ", downloadCount="
                + downloadCount
                + ", totalDownloadSize="
                + totalDownloadSize
                + ", averageDownloadSize="
                + averageDownloadSize
                + ", totalOriginalBitmapSize="
                + totalOriginalBitmapSize
                + ", totalTransformedBitmapSize="
                + totalTransformedBitmapSize
                + ", averageOriginalBitmapSize="
                + averageOriginalBitmapSize
                + ", averageTransformedBitmapSize="
                + averageTransformedBitmapSize
                + ", originalBitmapCount="
                + originalBitmapCount
                + ", transformedBitmapCount="
                + transformedBitmapCount
                + ", timeStamp="
                + timeStamp
                + '}')
    }

    companion object {
        private const val TAG = "QQQ StatsSnapshot"
    }

}
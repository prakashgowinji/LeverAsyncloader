package com.pin.lever

import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.telephony.TelephonyManager
import com.pin.lever.Utils.LeverThreadFactory
import java.util.concurrent.*

/**
 * The default [java.util.concurrent.ExecutorService] used for new [Lever] instances.
 *
 *
 * Exists as a custom type so that we can differentiate the use of defaults versus a user-supplied
 * instance.
 */
internal class LeverExecutorService : ThreadPoolExecutor(
    DEFAULT_THREAD_COUNT,
    DEFAULT_THREAD_COUNT,
    0,
    TimeUnit.MILLISECONDS,
    PriorityBlockingQueue(),
    LeverThreadFactory()
) {
    fun adjustThreadCount(info: NetworkInfo?) {
        if (info == null || !info.isConnectedOrConnecting) {
            setThreadCount(DEFAULT_THREAD_COUNT)
            return
        }
        when (info.type) {
            ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_WIMAX, ConnectivityManager.TYPE_ETHERNET -> setThreadCount(
                4
            )
            ConnectivityManager.TYPE_MOBILE -> when (info.subtype) {
                TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_EHRPD -> setThreadCount(
                    3
                )
                TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B -> setThreadCount(
                    2
                )
                TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE -> setThreadCount(
                    1
                )
                else -> setThreadCount(DEFAULT_THREAD_COUNT)
            }
            else -> setThreadCount(DEFAULT_THREAD_COUNT)
        }
    }

    private fun setThreadCount(threadCount: Int) {
        corePoolSize = threadCount
        maximumPoolSize = threadCount
    }

    override fun submit(task: Runnable): Future<*> {
        val ftask = LeverFutureTask(task as BitmapHunter)
        execute(ftask)
        return ftask
    }

    private class LeverFutureTask internal constructor(private val hunter: BitmapHunter) :
        FutureTask<BitmapHunter?>(hunter, null),
        Comparable<LeverFutureTask> {
        override fun compareTo(other: LeverFutureTask): Int {
            val p1 = hunter.priority
            val p2 = other.hunter.priority
            // High-priority requests are "lesser" so they are sorted to the front.
// Equal priorities are sorted by sequence number to provide FIFO ordering.
            return if (p1 == p2) hunter.sequence - other.hunter.sequence else p2.ordinal - p1.ordinal
        }

    }

    companion object {
        private const val DEFAULT_THREAD_COUNT = 3
    }
}
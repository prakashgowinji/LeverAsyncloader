package com.pin.lever;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * The default {@link java.util.concurrent.ExecutorService} used for new {@link Lever} instances.
 * <p>
 * Exists as a custom type so that we can differentiate the use of defaults versus a user-supplied
 * instance.
 */
class LeverExecutorService extends ThreadPoolExecutor {
    private static final int DEFAULT_THREAD_COUNT = 3;

    LeverExecutorService() {
        super(DEFAULT_THREAD_COUNT, DEFAULT_THREAD_COUNT, 0, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<Runnable>(), new Utils.LeverThreadFactory());
    }

    void adjustThreadCount(NetworkInfo info) {
        if (info == null || !info.isConnectedOrConnecting()) {
            setThreadCount(DEFAULT_THREAD_COUNT);
            return;
        }
        switch (info.getType()) {
            case ConnectivityManager.TYPE_WIFI:
            case ConnectivityManager.TYPE_WIMAX:
            case ConnectivityManager.TYPE_ETHERNET:
                setThreadCount(4);
                break;
            case ConnectivityManager.TYPE_MOBILE:
                switch (info.getSubtype()) {
                    case TelephonyManager.NETWORK_TYPE_LTE:  // 4G
                    case TelephonyManager.NETWORK_TYPE_HSPAP:
                    case TelephonyManager.NETWORK_TYPE_EHRPD:
                        setThreadCount(3);
                        break;
                    case TelephonyManager.NETWORK_TYPE_UMTS: // 3G
                    case TelephonyManager.NETWORK_TYPE_CDMA:
                    case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    case TelephonyManager.NETWORK_TYPE_EVDO_B:
                        setThreadCount(2);
                        break;
                    case TelephonyManager.NETWORK_TYPE_GPRS: // 2G
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                        setThreadCount(1);
                        break;
                    default:
                        setThreadCount(DEFAULT_THREAD_COUNT);
                }
                break;
            default:
                setThreadCount(DEFAULT_THREAD_COUNT);
        }
    }

    private void setThreadCount(int threadCount) {
        setCorePoolSize(threadCount);
        setMaximumPoolSize(threadCount);
    }

    @Override
    public Future<?> submit(Runnable task) {
        LeverFutureTask ftask = new LeverFutureTask((BitmapHunter) task);
        execute(ftask);
        return ftask;
    }

    private static final class LeverFutureTask extends FutureTask<BitmapHunter>
            implements Comparable<LeverFutureTask> {
        private final BitmapHunter hunter;

        LeverFutureTask(BitmapHunter hunter) {
            super(hunter, null);
            this.hunter = hunter;
        }

        @Override
        public int compareTo(LeverFutureTask other) {
            Lever.Priority p1 = hunter.getPriority();
            Lever.Priority p2 = other.hunter.getPriority();

            // High-priority requests are "lesser" so they are sorted to the front.
            // Equal priorities are sorted by sequence number to provide FIFO ordering.
            return (p1 == p2 ? hunter.sequence - other.hunter.sequence : p2.ordinal() - p1.ordinal());
        }
    }
}

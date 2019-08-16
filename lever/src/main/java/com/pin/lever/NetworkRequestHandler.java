package com.pin.lever;

import android.net.NetworkInfo;
import java.io.IOException;
import okhttp3.CacheControl;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.pin.lever.Lever.LoadedFrom.DISK;
import static com.pin.lever.Lever.LoadedFrom.NETWORK;

class NetworkRequestHandler extends RequestHandler {
    private static final String SCHEME_HTTP = "http";
    private static final String SCHEME_HTTPS = "https";

    private final Downloader downloader;
    private final Stats stats;

    NetworkRequestHandler(Downloader downloader, Stats stats) {
        this.downloader = downloader;
        this.stats = stats;
    }

    @Override public boolean canHandleRequest(Request data) {
        String scheme = data.uri.getScheme();
        return (SCHEME_HTTP.equals(scheme) || SCHEME_HTTPS.equals(scheme));
    }

    @Override public Result load(Request request, int networkPolicy) throws IOException {
        okhttp3.Request downloaderRequest = createRequest(request, networkPolicy);
        Response response = downloader.load(downloaderRequest);
        ResponseBody body = response.body();

        if (!response.isSuccessful()) {
            body.close();
            throw new ResponseException(response.code(), request.networkPolicy);
        }

        // Cache response is only null when the response comes fully from the network. Both completely
        // cached and conditionally cached responses will have a non-null cache response.
        Lever.LoadedFrom loadedFrom = response.cacheResponse() == null ? NETWORK : DISK;

        // Sometimes response content length is zero when requests are being replayed. Haven't found
        // root cause to this but retrying the request seems safe to do so.
        if (loadedFrom == DISK && body.contentLength() == 0) {
            body.close();
            throw new ContentLengthException("Received response with 0 content-length header.");
        }
        if (loadedFrom == NETWORK && body.contentLength() > 0) {
            stats.dispatchDownloadFinished(body.contentLength());
        }
        return new Result(body.source(), loadedFrom);
    }

    @Override int getRetryCount() {
        return 2;
    }

    @Override boolean shouldRetry(boolean airplaneMode, NetworkInfo info) {
        return info == null || info.isConnected();
    }

    @Override boolean supportsReplay() {
        return true;
    }

    private static okhttp3.Request createRequest(Request request, int networkPolicy) {
        CacheControl cacheControl = null;
        if (networkPolicy != 0) {
            if (NetworkPolicy.isOfflineOnly(networkPolicy)) {
                cacheControl = CacheControl.FORCE_CACHE;
            } else {
                CacheControl.Builder builder = new CacheControl.Builder();
                if (!NetworkPolicy.shouldReadFromDiskCache(networkPolicy)) {
                    builder.noCache();
                }
                if (!NetworkPolicy.shouldWriteToDiskCache(networkPolicy)) {
                    builder.noStore();
                }
                cacheControl = builder.build();
            }
        }

        okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(request.uri.toString());
        if (cacheControl != null) {
            builder.cacheControl(cacheControl);
        }
        return builder.build();
    }

    static class ContentLengthException extends IOException {
        ContentLengthException(String message) {
            super(message);
        }
    }

    static final class ResponseException extends IOException {
        final int code;
        final int networkPolicy;

        ResponseException(int code, int networkPolicy) {
            super("HTTP " + code);
            this.code = code;
            this.networkPolicy = networkPolicy;
        }
    }
}


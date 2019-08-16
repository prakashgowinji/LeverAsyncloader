package com.pin.lever;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import static com.pin.lever.Lever.Priority;

abstract class Action<T> {
    static class RequestWeakReference<M> extends WeakReference<M> {
        final Action action;

        RequestWeakReference(Action action, M referent, ReferenceQueue<? super M> q) {
            super(referent, q);
            this.action = action;
        }
    }

    final Lever lever;
    final Request request;
    final WeakReference<T> target;
    final boolean noFade;
    final int memoryPolicy;
    final int networkPolicy;
    final int errorResId;
    final Drawable errorDrawable;
    final String key;
    final Object tag;

    boolean willReplay;
    boolean cancelled;

    Action(Lever lever, T target, Request request, int memoryPolicy, int networkPolicy,
           int errorResId, Drawable errorDrawable, String key, Object tag, boolean noFade) {
        this.lever = lever;
        this.request = request;
        this.target =
                target == null ? null : new RequestWeakReference<>(this, target, lever.referenceQueue);
        this.memoryPolicy = memoryPolicy;
        this.networkPolicy = networkPolicy;
        this.noFade = noFade;
        this.errorResId = errorResId;
        this.errorDrawable = errorDrawable;
        this.key = key;
        this.tag = (tag != null ? tag : this);
    }

    abstract void complete(Bitmap result, Lever.LoadedFrom from);

    abstract void error(Exception e);

    void cancel() {
        cancelled = true;
    }

    Request getRequest() {
        return request;
    }

    T getTarget() {
        return target == null ? null : target.get();
    }

    String getKey() {
        return key;
    }

    boolean isCancelled() {
        return cancelled;
    }

    boolean willReplay() {
        return willReplay;
    }

    int getMemoryPolicy() {
        return memoryPolicy;
    }

    int getNetworkPolicy() {
        return networkPolicy;
    }

    Lever getLever() {
        return lever;
    }

    Priority getPriority() {
        return request.priority;
    }

    Object getTag() {
        return tag;
    }
}
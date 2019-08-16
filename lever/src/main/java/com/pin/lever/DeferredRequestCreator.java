package com.pin.lever;

import android.support.annotation.VisibleForTesting;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.ImageView;
import java.lang.ref.WeakReference;

class DeferredRequestCreator implements OnPreDrawListener, OnAttachStateChangeListener {
    private final RequestCreator creator;
    @VisibleForTesting
    final WeakReference<ImageView> target;
    @VisibleForTesting Callback callback;

    DeferredRequestCreator(RequestCreator creator, ImageView target, Callback callback) {
        this.creator = creator;
        this.target = new WeakReference<>(target);
        this.callback = callback;

        target.addOnAttachStateChangeListener(this);

        // Only add the pre-draw listener if the view is already attached
        if (target.getWindowToken() != null) {
            onViewAttachedToWindow(target);
        }
    }

    @Override public void onViewAttachedToWindow(View view) {
        view.getViewTreeObserver().addOnPreDrawListener(this);
    }

    @Override public void onViewDetachedFromWindow(View view) {
        view.getViewTreeObserver().removeOnPreDrawListener(this);
    }

    @Override public boolean onPreDraw() {
        ImageView target = this.target.get();
        if (target == null) {
            return true;
        }

        ViewTreeObserver vto = target.getViewTreeObserver();
        if (!vto.isAlive()) {
            return true;
        }

        int width = target.getWidth();
        int height = target.getHeight();

        if (width <= 0 || height <= 0) {
            return true;
        }

        target.removeOnAttachStateChangeListener(this);
        vto.removeOnPreDrawListener(this);
        this.target.clear();

        this.creator.unfit().resize(width, height).into(target, callback);
        return true;
    }

    void cancel() {
        creator.clearTag();
        callback = null;

        ImageView target = this.target.get();
        if (target == null) {
            return;
        }
        this.target.clear();

        target.removeOnAttachStateChangeListener(this);

        ViewTreeObserver vto = target.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.removeOnPreDrawListener(this);
        }
    }

    Object getTag() {
        return creator.getTag();
    }
}


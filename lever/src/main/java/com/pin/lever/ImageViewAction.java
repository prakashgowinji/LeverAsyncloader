package com.pin.lever;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

class ImageViewAction extends Action<ImageView> {

    Callback callback;

    ImageViewAction(Lever lever, ImageView imageView, Request data, int memoryPolicy,
                    int networkPolicy, int errorResId, Drawable errorDrawable, String key, Object tag,
                    Callback callback, boolean noFade) {
        super(lever, imageView, data, memoryPolicy, networkPolicy, errorResId, errorDrawable, key,
                tag, noFade);
        this.callback = callback;
    }

    @Override public void complete(Bitmap result, Lever.LoadedFrom from) {
        if (result == null) {
            throw new AssertionError(
                    String.format("Attempted to complete action with no result!\n%s", this));
        }

        ImageView target = this.target.get();
        if (target == null) {
            return;
        }

        Context context = lever.context;
        boolean indicatorsEnabled = lever.indicatorsEnabled;
        LeverDrawable.setBitmap(target, context, result, from, noFade, indicatorsEnabled);

        if (callback != null) {
            callback.onSuccess();
        }
    }

    @Override public void error(Exception e) {
        ImageView target = this.target.get();
        if (target == null) {
            return;
        }
        Drawable placeholder = target.getDrawable();
        if (placeholder instanceof Animatable) {
            ((Animatable) placeholder).stop();
        }
        if (errorResId != 0) {
            target.setImageResource(errorResId);
        } else if (errorDrawable != null) {
            target.setImageDrawable(errorDrawable);
        }

        if (callback != null) {
            callback.onError(e);
        }
    }

    @Override void cancel() {
        super.cancel();
        if (callback != null) {
            callback = null;
        }
    }
}

package com.pin.lever

import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.pin.lever.Lever.LoadedFrom
import com.pin.lever.LeverDrawable.Companion.setBitmap

internal class ImageViewAction(
    lever: Lever?,
    imageView: ImageView?,
    data: Request?,
    memoryPolicy: Int,
    networkPolicy: Int,
    errorResId: Int,
    errorDrawable: Drawable?,
    key: String?,
    tag: Any?,
    var callback: Callback?,
    noFade: Boolean
) : Action<ImageView?>(
    lever, imageView, data, memoryPolicy, networkPolicy, errorResId, errorDrawable, key,
    tag, noFade
) {
    public override fun complete(result: Bitmap?, from: LoadedFrom?) {
        if (result == null) {
            throw AssertionError(
                String.format(
                    "Attempted to complete action with no result!\n%s",
                    this
                )
            )
        }
        val target = target!!.get() ?: return
        val context = lever!!.context
        val indicatorsEnabled = lever.indicatorsEnabled
        setBitmap(target, context, result, from!!, noFade, indicatorsEnabled)
        if (callback != null) {
            callback!!.onSuccess()
        }
    }

    public override fun error(e: Exception?) {
        val target = target!!.get() ?: return
        val placeholder = target.drawable
        if (placeholder is Animatable) {
            (placeholder as Animatable).stop()
        }
        if (errorResId != 0) {
            target.setImageResource(errorResId)
        } else if (errorDrawable != null) {
            target.setImageDrawable(errorDrawable)
        }
        if (callback != null) {
            callback!!.onError(e!!)
        }
    }

    public override fun cancel() {
        super.cancel()
        if (callback != null) {
            callback = null
        }
    }

}
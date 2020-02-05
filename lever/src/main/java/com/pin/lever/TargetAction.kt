package com.pin.lever

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.pin.lever.Lever.LoadedFrom

internal class TargetAction(
    lever: Lever?,
    target: Target?,
    data: Request?,
    memoryPolicy: Int,
    networkPolicy: Int,
    errorDrawable: Drawable?,
    key: String?,
    tag: Any?,
    errorResId: Int
) : Action<Target?>(
    lever, target, data, memoryPolicy, networkPolicy, errorResId, errorDrawable, key, tag,
    false
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
        val target = getTarget()
        if (target != null) {
            target.onBitmapLoaded(result, from!!)
            check(!result.isRecycled) { "Target callback must not recycle bitmap!" }
        }
    }

    public override fun error(e: Exception?) {
        val target = getTarget()
        if (target != null) {
            if (errorResId != 0) {
                target.onBitmapFailed(e!!, lever!!.context.resources.getDrawable(errorResId))
            } else {
                target.onBitmapFailed(e!!, errorDrawable)
            }
        }
    }
}
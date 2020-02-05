package com.pin.lever

import android.graphics.Bitmap
import com.pin.lever.Lever.LoadedFrom

internal class FetchAction(
    lever: Lever?,
    data: Request?,
    memoryPolicy: Int,
    networkPolicy: Int,
    tag: Any?,
    key: String?,
    callback: Callback?
) : Action<Any?>(
    lever,
    null,
    data,
    memoryPolicy,
    networkPolicy,
    0,
    null,
    key,
    tag,
    false
) {
    private val targetAny: Any
    private var callback: Callback?
    public override fun complete(result: Bitmap?, from: LoadedFrom?) {
        if (callback != null) {
            callback!!.onSuccess()
        }
    }

    public override fun error(e: Exception?) {
        if (callback != null) {
            callback!!.onError(e!!)
        }
    }

    public override fun cancel() {
        super.cancel()
        callback = null
    }

    public override fun getTarget(): Any {
        return targetAny
    }

    init {
        this.targetAny = Any()
        this.callback = callback
    }
}
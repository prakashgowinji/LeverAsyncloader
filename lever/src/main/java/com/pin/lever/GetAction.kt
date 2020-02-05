package com.pin.lever

import android.graphics.Bitmap
import com.pin.lever.Lever.LoadedFrom

internal class GetAction(
    lever: Lever?,
    data: Request?,
    memoryPolicy: Int,
    networkPolicy: Int,
    tag: Any?,
    key: String?
) : Action<Void?>(
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
    public override fun complete(result: Bitmap?, from: LoadedFrom?) {}
    public override fun error(e: Exception?) {}
}
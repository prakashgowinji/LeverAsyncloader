package com.pin.lever

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

interface Target {
    /**
     * Callback when an image has been successfully loaded.
     *
     *
     * **Note:** You must not recycle the bitmap.
     */
    abstract fun onBitmapLoaded(bitmap: Bitmap, from: Lever.LoadedFrom)

    /**
     * Callback indicating the image could not be successfully loaded.
     *
     *
     * **Note:** The passed [Drawable] may be `null` if none has been
     * specified via [RequestCreator.error]
     * or [RequestCreator.error].
     */
    abstract fun onBitmapFailed(e: Exception, errorDrawable: Drawable?)

    /**
     * Callback invoked right before your request is submitted.
     *
     *
     * **Note:** The passed [Drawable] may be `null` if none has been
     * specified via [RequestCreator.placeholder]
     * or [RequestCreator.placeholder].
     */
    abstract fun onPrepareLoad(placeHolderDrawable: Drawable?)
}
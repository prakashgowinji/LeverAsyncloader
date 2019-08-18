package com.pin.lever

import android.graphics.Bitmap

interface Transformation {
    /**
     * Transform the source bitmap into a new bitmap. If you create a new bitmap instance, you must
     * call [android.graphics.Bitmap.recycle] on `source`. You may return the original
     * if no transformation is required.
     */
    fun transform(source: Bitmap): Bitmap

    /**
     * Returns a unique key for the transformation, used for caching purposes. If the transformation
     * has parameters (e.g. size, scale factor, etc) then these should be part of the key.
     */
    fun key(): String
}
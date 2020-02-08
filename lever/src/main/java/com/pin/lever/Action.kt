package com.pin.lever

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.pin.lever.Lever.LoadedFrom
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

abstract class Action<T>(
    val lever: Lever?,
    target: T?,
    val request: Request?,
    memoryPolicy: Int,
    networkPolicy: Int,
    errorResId: Int,
    errorDrawable: Drawable?,
    key: String?,
    tag: Any?,
    noFade: Boolean
) {
    internal class RequestWeakReference<M>(
        val action: Action<*>,
        referent: M,
        q: ReferenceQueue<in M>
    ) : WeakReference<M>(referent, q)

    var target: WeakReference<T>?
    val noFade: Boolean
    val memoryPolicy: Int
    val networkPolicy: Int
    val errorResId: Int
    val errorDrawable: Drawable?
    val key: String
    val tag: Any
    var willReplay = false
    var isCancelled = false
    abstract fun complete(result: Bitmap?, from: LoadedFrom?)
    abstract fun error(e: Exception?)
    open fun cancel() {
        isCancelled = true
    }

    open fun getTarget(): T? {
        return target?.get()
    }

    fun willReplay(): Boolean {
        return willReplay
    }

    val priority: Lever.Priority
        get() = request!!.priority

    init {
        this.target = if (target == null) null else RequestWeakReference<T>(
            this,
            target,
            lever!!.referenceQueue as ReferenceQueue<in T>
        )
        this.memoryPolicy = memoryPolicy
        this.networkPolicy = networkPolicy
        this.noFade = noFade
        this.errorResId = errorResId
        this.errorDrawable = errorDrawable
        this.key = key!!
        this.tag = tag ?: this
    }
}
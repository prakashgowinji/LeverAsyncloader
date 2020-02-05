package com.pin.lever

import android.support.annotation.VisibleForTesting
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import java.lang.ref.WeakReference

class DeferredRequestCreator(
    private val creator: RequestCreator,
    target: ImageView,
    callback: Callback?
) : ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
    @VisibleForTesting
    val target: WeakReference<ImageView>
    @VisibleForTesting
    var callback: Callback?

    override fun onViewAttachedToWindow(view: View) {
        view.viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onViewDetachedFromWindow(view: View) {
        view.viewTreeObserver.removeOnPreDrawListener(this)
    }

    override fun onPreDraw(): Boolean {
        val target = target.get() ?: return true
        val vto = target.viewTreeObserver
        if (!vto.isAlive) {
            return true
        }
        val width = target.width
        val height = target.height
        if (width <= 0 || height <= 0) {
            return true
        }
        target.removeOnAttachStateChangeListener(this)
        vto.removeOnPreDrawListener(this)
        this.target.clear()
        creator.unfit().resize(width, height).into(target, callback)
        return true
    }

    fun cancel() {
        creator.clearTag()
        callback = null
        val target = target.get() ?: return
        this.target.clear()
        target.removeOnAttachStateChangeListener(this)
        val vto = target.viewTreeObserver
        if (vto.isAlive) {
            vto.removeOnPreDrawListener(this)
        }
    }

    val tag: Any?
        get() = creator.tag

    init {
        this.target = WeakReference(target)
        this.callback = callback
        target.addOnAttachStateChangeListener(this)
        // Only add the pre-draw listener if the view is already attached
        if (target.windowToken != null) {
            onViewAttachedToWindow(target)
        }
    }
}
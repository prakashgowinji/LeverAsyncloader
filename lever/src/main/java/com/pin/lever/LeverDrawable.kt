package com.pin.lever

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.widget.ImageView
import com.pin.lever.Lever.LoadedFrom

internal class LeverDrawable(
    context: Context, bitmap: Bitmap?, placeholder: Drawable?,
    loadedFrom: LoadedFrom, noFade: Boolean, private val debugging: Boolean
) : BitmapDrawable(context.resources, bitmap) {
    private val density: Float
    private val loadedFrom: LoadedFrom
    var placeholder: Drawable? = null
    var startTimeMillis: Long = 0
    var animating = false
    var alphaColor = 0xFF
    override fun draw(canvas: Canvas) {
        if (!animating) {
            super.draw(canvas)
        } else {
            val normalized =
                (SystemClock.uptimeMillis() - startTimeMillis) / FADE_DURATION
            if (normalized >= 1f) {
                animating = false
                placeholder = null
                super.draw(canvas)
            } else {
                if (placeholder != null) {
                    placeholder!!.draw(canvas)
                }
                // setAlpha will call invalidateSelf and drive the animation.
                val partialAlpha = (alphaColor * normalized).toInt()
                super.setAlpha(partialAlpha)
                super.draw(canvas)
                super.setAlpha(alphaColor)
            }
        }
        if (debugging) {
            drawDebugIndicator(canvas)
        }
    }

    override fun setAlpha(alpha: Int) {
        this.alphaColor = alpha
        if (placeholder != null) {
            placeholder!!.alpha = alpha
        }
        super.setAlpha(alpha)
    }

    override fun setColorFilter(cf: ColorFilter) {
        if (placeholder != null) {
            placeholder!!.colorFilter = cf
        }
        super.setColorFilter(cf)
    }

    override fun onBoundsChange(bounds: Rect) {
        if (placeholder != null) {
            placeholder!!.bounds = bounds
        }
        super.onBoundsChange(bounds)
    }

    private fun drawDebugIndicator(canvas: Canvas) {
        DEBUG_PAINT.color = Color.WHITE
        var path =
            getTrianglePath(0, 0, (16 * density).toInt())
        canvas.drawPath(path, DEBUG_PAINT)
        DEBUG_PAINT.color = loadedFrom.debugColor
        path = getTrianglePath(0, 0, (15 * density).toInt())
        canvas.drawPath(path, DEBUG_PAINT)
    }

    companion object {
        // Only accessed from main thread.
        private val DEBUG_PAINT = Paint()
        private const val FADE_DURATION = 200f //ms
        /**
         * Create or update the drawable on the target [ImageView] to display the supplied bitmap
         * image.
         */
        @JvmStatic
        fun setBitmap(
            target: ImageView, context: Context, bitmap: Bitmap?,
            loadedFrom: LoadedFrom, noFade: Boolean, debugging: Boolean
        ) {
            val placeholder = target.drawable
            if (placeholder is Animatable) {
                (placeholder as Animatable).stop()
            }
            val drawable =
                LeverDrawable(context, bitmap, placeholder, loadedFrom, noFade, debugging)
            target.setImageDrawable(drawable)
        }

        /**
         * Create or update the drawable on the target [ImageView] to display the supplied
         * placeholder image.
         */
        fun setPlaceholder(
            target: ImageView,
            placeholderDrawable: Drawable?
        ) {
            target.setImageDrawable(placeholderDrawable)
            if (target.drawable is Animatable) {
                (target.drawable as Animatable).start()
            }
        }

        private fun getTrianglePath(x1: Int, y1: Int, width: Int): Path {
            val path = Path()
            path.moveTo(x1.toFloat(), y1.toFloat())
            path.lineTo(x1 + width.toFloat(), y1.toFloat())
            path.lineTo(x1.toFloat(), y1 + width.toFloat())
            return path
        }
    }

    init {
        density = context.resources.displayMetrics.density
        this.loadedFrom = loadedFrom
        val fade = loadedFrom != LoadedFrom.MEMORY && !noFade
        if (fade) {
            this.placeholder = placeholder
            animating = true
            startTimeMillis = SystemClock.uptimeMillis()
        }
    }
}
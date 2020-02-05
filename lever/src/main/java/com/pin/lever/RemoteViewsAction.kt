package com.pin.lever

import android.app.Notification
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.pin.lever.Lever.LoadedFrom
import com.pin.lever.RemoteViewsAction.RemoteViewsTarget
import com.pin.lever.Utils.getService

internal abstract class RemoteViewsAction(
    lever: Lever?, data: Request?, val remoteViews: RemoteViews, val viewId: Int,
    errorResId: Int, memoryPolicy: Int, networkPolicy: Int, tag: Any?, key: String?,
    var callback: Callback?
) : Action<RemoteViewsTarget?>(
    lever,
    null,
    data,
    memoryPolicy,
    networkPolicy,
    errorResId,
    null,
    key,
    tag,
    false
) {
    private var viewsTarget: RemoteViewsTarget? = null
    public override fun complete(result: Bitmap?, from: LoadedFrom?) {
        remoteViews.setImageViewBitmap(viewId, result)
        update()
        if (callback != null) {
            callback!!.onSuccess()
        }
    }

    public override fun cancel() {
        super.cancel()
        if (callback != null) {
            callback = null
        }
    }

    public override fun error(e: Exception?) {
        if (errorResId != 0) {
            setImageResource(errorResId)
        }
        if (callback != null) {
            callback!!.onError(e!!)
        }
    }

    public override fun getTarget(): RemoteViewsTarget? {
        if (viewsTarget == null) {
            viewsTarget = RemoteViewsTarget(remoteViews, viewId)
        }
        return viewsTarget
    }

    fun setImageResource(resId: Int) {
        remoteViews.setImageViewResource(viewId, resId)
        update()
    }

    abstract fun update()
    internal class RemoteViewsTarget(val remoteViews: RemoteViews, val viewId: Int) {
        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val remoteViewsTarget = o as RemoteViewsTarget
            return viewId == remoteViewsTarget.viewId && remoteViews ==
                    remoteViewsTarget.remoteViews
        }

        override fun hashCode(): Int {
            return 31 * remoteViews.hashCode() + viewId
        }

    }

    internal class AppWidgetAction(
        lever: Lever?,
        data: Request?,
        remoteViews: RemoteViews,
        viewId: Int,
        private val appWidgetIds: IntArray,
        memoryPolicy: Int,
        networkPolicy: Int,
        key: String?,
        tag: Any?,
        errorResId: Int,
        callback: Callback?
    ) : RemoteViewsAction(
        lever, data, remoteViews, viewId, errorResId, memoryPolicy, networkPolicy, tag, key,
        callback
    ) {
        override fun update() {
            val manager = AppWidgetManager.getInstance(lever!!.context)
            manager.updateAppWidget(appWidgetIds, remoteViews)
        }

    }

    internal class NotificationAction(
        lever: Lever?,
        data: Request?,
        remoteViews: RemoteViews,
        viewId: Int,
        private val notificationId: Int,
        private val notification: Notification,
        private val notificationTag: String,
        memoryPolicy: Int,
        networkPolicy: Int,
        key: String?,
        tag: Any?,
        errorResId: Int,
        callback: Callback?
    ) : RemoteViewsAction(
        lever, data, remoteViews, viewId, errorResId, memoryPolicy, networkPolicy, tag, key,
        callback
    ) {
        override fun update() {
            val manager = getService<NotificationManager>(
                lever!!.context,
                Context.NOTIFICATION_SERVICE
            )
            manager.notify(notificationTag, notificationId, notification)
        }

    }

}
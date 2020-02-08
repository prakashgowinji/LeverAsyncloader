package com.pinload.utils

import android.content.Context
import android.widget.Toast

object ToastUtils {
    private var toast: Toast? = null

    fun showShortToast(context: Context, message: String) {
        toast?.cancel()
        toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        toast!!.show()
    }

    fun showLongToast(context: Context, message: String) {
        toast?.cancel()
        toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        toast!!.show()
    }

    fun showShortToast(context: Context, message: Int) {
        toast?.cancel()
        toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        toast!!.show()
    }

    fun showLongToast(context: Context, message: Int) {
        toast?.cancel()
        toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        toast!!.show()
    }
}
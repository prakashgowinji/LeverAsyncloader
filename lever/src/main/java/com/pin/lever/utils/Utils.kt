package com.pin.lever.utils

import android.app.ProgressDialog
import android.content.Context
import android.net.ConnectivityManager
import android.text.format.DateUtils
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


fun getProgressDialog(context: Context, msg: String): ProgressDialog {
    val progressDialog = ProgressDialog(context)
    progressDialog.setMessage(msg)
    progressDialog.setCancelable(false)
    return progressDialog
}

fun isOnline(context: Context): Boolean {
    val connectivity = context
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val netInfo = connectivity.activeNetworkInfo
    if (netInfo == null) {
        return false
    } else {
        return netInfo.isConnected
    }
    return false
}

fun getRelativeTimeSpan(millisTime: Long): CharSequence {
    val now = System.currentTimeMillis()
    return DateUtils.getRelativeTimeSpanString(millisTime, now, DateUtils.MINUTE_IN_MILLIS)
}

fun getMillisFromString(dateTimeString: String): Long {
    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
    try {
        val mDate = simpleDateFormat.parse(dateTimeString)
        val timeInMilliseconds = mDate.getTime()
        return timeInMilliseconds
    } catch (e: ParseException) {
        e.printStackTrace()
    }
    return 0
}


fun getAge(dobString: String): Int {
    var date: Date? = null
    val sdf = SimpleDateFormat("yyyy-MM-dd")
    try {
        date = sdf.parse(dobString)
    } catch (e: ParseException) {
        e.printStackTrace()
    }
    if (date == null) return 0
    val dateOfBirth = Calendar.getInstance()
    val today = Calendar.getInstance()
    dateOfBirth.time = date
    var age = today.get(Calendar.YEAR) - dateOfBirth.get(Calendar.YEAR)
    if (today.get(Calendar.DAY_OF_YEAR) < dateOfBirth.get(Calendar.DAY_OF_YEAR)) {
        age--
    }
    val ageInt = age

    return ageInt
}
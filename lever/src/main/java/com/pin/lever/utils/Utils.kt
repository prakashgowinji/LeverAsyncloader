package com.pin.lever.utils

import android.app.ProgressDialog
import android.content.Context
import android.net.ConnectivityManager
import android.os.Environment
import android.text.format.DateUtils
import java.text.ParseException
import java.text.SimpleDateFormat
import java.io.File.separator
import okhttp3.ResponseBody
import java.io.*
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
    val netInfo = connectivity.activeNetworkInfo ?: return false
    return netInfo.isConnected
}

fun getRelativeTimeSpan(millisTime: Long): CharSequence {
    val now = System.currentTimeMillis()
    return DateUtils.getRelativeTimeSpanString(millisTime, now, DateUtils.MINUTE_IN_MILLIS)
}

fun getMillisFromString(dateTimeString: String): Long {
    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
    try {
        val mDate = simpleDateFormat.parse(dateTimeString)
        return mDate.time
    } catch (e: ParseException) {
        e.printStackTrace()
    }
    return 0
}


/**
 * Method to create the file from the server response while downloading
 */
private fun writeResponseBodyToDisk(body: ResponseBody, filename: String): Boolean {
    try {
        // Todo change the file location/name according to your needs
        val futureStudioIconFile = File(Environment.getExternalStorageDirectory().getPath() + separator + filename)

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        try {
            val fileReader = ByteArray(4096)

            val fileSize = body.contentLength()
            var fileSizeDownloaded: Long = 0

            inputStream = body.byteStream()
            outputStream = FileOutputStream(futureStudioIconFile)

            while (true) {
                val read = inputStream!!.read(fileReader)

                if (read == -1) {
                    break
                }

                outputStream.write(fileReader, 0, read)

                fileSizeDownloaded += read.toLong()
            }
            outputStream.flush()
            return true
        } catch (e: IOException) {
            return false
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    } catch (e: IOException) {
        return false
    }
}

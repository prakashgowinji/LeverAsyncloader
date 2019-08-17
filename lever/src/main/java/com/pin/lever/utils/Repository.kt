package com.pin.lever.utils

import com.google.gson.JsonElement
import io.reactivex.Observable
import okhttp3.ResponseBody

class Repository(val apiCallInterface: ApiCallInterface) {

    fun executeGetInfo():Observable<JsonElement>{
        return apiCallInterface.getInfo()
    }

    fun excuteDownloadFile(): Observable<ResponseBody> {
        return apiCallInterface.downloadFileWithFixedUrl()
    }

    fun excuteDownloadFile(fileurl: String): Observable<ResponseBody> {
        return apiCallInterface.downloadFileWithDynamicUrlSync(fileurl)
    }
}
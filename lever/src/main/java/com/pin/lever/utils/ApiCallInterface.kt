package com.pin.lever.utils

import com.google.gson.JsonElement
import io.reactivex.Observable
import retrofit2.http.GET
import okhttp3.ResponseBody
import retrofit2.http.Url


interface ApiCallInterface {

    @GET(Urls.GET_INFO)
    fun getInfo(): Observable<JsonElement>

    @GET("/resource/test.zip")
    fun downloadFileWithFixedUrl(): Observable<ResponseBody>

    @GET
    fun downloadFileWithDynamicUrlSync(@Url fileUrl: String): Observable<ResponseBody>
}
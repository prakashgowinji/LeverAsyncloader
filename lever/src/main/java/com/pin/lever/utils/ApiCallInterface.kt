package com.pin.lever.utils

import com.google.gson.JsonElement
import io.reactivex.Observable
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET

interface ApiCallInterface {

    @GET(Urls.GET_INFO)
    fun getInfo(): Observable<JsonElement>
}
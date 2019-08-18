package com.pin.lever.utils

import com.google.gson.JsonElement
import io.reactivex.annotations.NonNull
import io.reactivex.annotations.Nullable
import okhttp3.ResponseBody

class ApiResponse(val status: Status, @Nullable val data: JsonElement?, @Nullable val error: Throwable?, @Nullable val file: ResponseBody?) {
//    lateinit var status: Status
//
//    @Nullable
//    var data: JsonElement?
//
//    @Nullable
//    var file: ResponseBody?
//
//    @Nullable
//    var error: Throwable?

//    constructor(val status: Status, @Nullable val data: JsonElement?, @Nullable val error: Throwable?, @Nullable val file: ResponseBody?) {
//        this.status = status
//        this.data = data
//        this.error = error
//        this.file = file
//    }

    companion object {
        fun loading(): ApiResponse {
            return ApiResponse(Status.LOADING, null, null, null)
        }

        fun success(@NonNull data: JsonElement): ApiResponse {
            return ApiResponse(Status.SUCCESS, data, null, null)
        }

        fun complete(@NonNull data: ResponseBody): ApiResponse {
            return ApiResponse(Status.SUCCESS, null, null, data)
        }

        fun error(@NonNull error: Throwable): ApiResponse {
            return ApiResponse(Status.ERROR, null, error, null)
        }
    }
}
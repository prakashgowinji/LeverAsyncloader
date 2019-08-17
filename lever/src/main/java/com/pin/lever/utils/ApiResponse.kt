package com.pin.lever.utils

import com.google.gson.JsonElement
import io.reactivex.annotations.NonNull
import io.reactivex.annotations.Nullable

class ApiResponse {
    lateinit var status: Status

    @Nullable
    var data: JsonElement?

    @Nullable
    var error: Throwable?

    constructor(status: Status, @Nullable data: JsonElement?, @Nullable error: Throwable?) {
        this.status = status
        this.data = data
        this.error = error
    }

    companion object {
        fun loading(): ApiResponse {
            return ApiResponse(Status.LOADING, null, null)
        }

        fun success(@NonNull data: JsonElement): ApiResponse {
            return ApiResponse(Status.SUCCESS, data, null)
        }

        fun error(@NonNull error: Throwable): ApiResponse {
            return ApiResponse(Status.ERROR, null, error)
        }
    }
}
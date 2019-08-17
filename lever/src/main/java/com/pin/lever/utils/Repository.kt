package com.pin.lever.utils

import com.google.gson.JsonElement
import io.reactivex.Observable

class Repository(val apiCallInterface: ApiCallInterface) {

    fun executeGetInfo():Observable<JsonElement>{
        return apiCallInterface.getInfo()
    }
}
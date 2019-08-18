package com.pin.lever

interface Callback {
    fun onSuccess()

    fun onError(e: Exception)

    class EmptyCallback : Callback {

        override fun onSuccess() {}

        override fun onError(e: Exception) {}
    }
}
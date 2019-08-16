package com.pin.lever

interface Callback {
    abstract fun onSuccess()

    abstract fun onError(e: Exception)

    class EmptyCallback : Callback {

        override fun onSuccess() {}

        override fun onError(e: Exception) {}
    }
}
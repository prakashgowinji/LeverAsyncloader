package com.pinload

import android.app.ProgressDialog
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.pin.lever.utils.ApiResponse
import com.pin.lever.utils.getProgressDialog

abstract class BaseActivity: AppCompatActivity() {
    lateinit var progressDialog: ProgressDialog
    abstract fun consumeResponse(apiResponse: ApiResponse)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progressDialog = getProgressDialog(this, "Please wait...")
    }
}
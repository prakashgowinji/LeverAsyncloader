package com.pinload.app

import android.app.Application
import android.content.Context
import com.pin.lever.di.ApiModule
import com.pinload.di.AppComponent
import com.pinload.di.AppModule
import com.pinload.di.DaggerAppComponent
import com.pinload.di.LiveDataModule

class PinLoadApplication: Application() {
    private lateinit var applicationComponent: AppComponent
    private lateinit var context: Context

    override fun onCreate() {
        super.onCreate()
        context = this
        applicationComponent = DaggerAppComponent.builder().appModule(AppModule(this)).apiModule(ApiModule(context)).liveDataModule(
            LiveDataModule()).build()
    }

    fun getAppComponent(): AppComponent {
        return applicationComponent
    }
}
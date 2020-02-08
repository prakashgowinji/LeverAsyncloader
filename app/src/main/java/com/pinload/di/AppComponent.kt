package com.pinload.di

import com.pin.lever.di.ApiModule
import com.pinload.MainActivity
import dagger.Component
import javax.inject.Singleton

@Component(modules = [AppModule::class, LiveDataModule::class, ApiModule::class])
@Singleton
interface AppComponent {
    fun doInjection(mainActivity: MainActivity)
}
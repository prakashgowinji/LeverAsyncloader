package com.pinload.di

import android.content.Context
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class AppModule(context: Context) {
    var context: Context = context

    @Provides
    @Singleton
    internal fun provideContext(): Context {
        return context
    }
}
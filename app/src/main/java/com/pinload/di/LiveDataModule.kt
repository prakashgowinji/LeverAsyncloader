package com.pinload.di

import android.arch.lifecycle.ViewModelProvider
import com.pin.lever.utils.ApiCallInterface
import com.pin.lever.utils.Repository
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class LiveDataModule {

    @Provides
    @Singleton
    internal fun getRepository(apiCallInterface: ApiCallInterface): Repository {
        return Repository(apiCallInterface)
    }

    @Provides
    @Singleton
    internal fun getViewModelFactory(myRepository: Repository): ViewModelProvider.Factory {
        return ViewModelFactory(myRepository)
    }
}
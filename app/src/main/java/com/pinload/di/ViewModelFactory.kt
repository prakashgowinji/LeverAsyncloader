package com.pinload.di

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import com.pin.lever.utils.Repository
import com.pinload.MainViewModel
import javax.inject.Inject

class ViewModelFactory: ViewModelProvider.Factory {
    lateinit var repository: Repository

    @Inject
    constructor(repository: Repository){
        this.repository = repository
    }

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if(modelClass.isAssignableFrom(MainViewModel::class.java)){
           return MainViewModel(repository = repository) as T
        }
        throw IllegalArgumentException("unknown Class name!")
    }
}
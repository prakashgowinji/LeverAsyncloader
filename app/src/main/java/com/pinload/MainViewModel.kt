package com.pinload

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.pin.lever.utils.ApiResponse
import com.pin.lever.utils.Repository
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class MainViewModel(val repository: Repository) : ViewModel() {

    private val disposables = CompositeDisposable()
    private val responseLiveData = MutableLiveData<ApiResponse>()

    fun mainResponse(): MutableLiveData<ApiResponse> {
        return responseLiveData
    }

    /*
     * method to call normal GET method of Retrofit
     *
     * */
    fun hitMainPageContent() {
        disposables.add(repository.executeGetInfo()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { d -> responseLiveData.setValue(ApiResponse.loading()) }
            .subscribe(
                { result -> responseLiveData.setValue(ApiResponse.success(result)) },
                { throwable -> responseLiveData.setValue(ApiResponse.error(throwable)) }
            ))

    }


    /**
     * To download a static file, where the file path could be edited in ApiCallInterface
     */
    fun hitDownloadFile(){
        disposables.add(repository.excuteDownloadFile()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { d -> responseLiveData.setValue(ApiResponse.loading()) }
            .subscribe(
                { result -> responseLiveData.setValue(ApiResponse.complete(result)) },
                { throwable -> responseLiveData.setValue(ApiResponse.error(throwable)) }
            ))
    }

    /**
     * To download a file with a specific file path
     */
    fun hitDownloadFile(fileUrl: String){
        disposables.add(repository.excuteDownloadFile(fileUrl)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { d -> responseLiveData.setValue(ApiResponse.loading()) }
            .subscribe(
                { result -> responseLiveData.setValue(ApiResponse.complete(result)) },
                { throwable -> responseLiveData.setValue(ApiResponse.error(throwable)) }
            ))
    }

    override fun onCleared() {
        disposables.clear()
    }
}
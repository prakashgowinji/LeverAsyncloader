package com.pinload

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.widget.LinearLayoutManager
import android.widget.LinearLayout
import com.google.gson.Gson
import com.pin.lever.utils.ApiResponse
import com.pin.lever.utils.Status
import com.pin.lever.utils.ToastUtils
import com.pinload.app.PinLoadApplication
import com.pinload.datamodel.ItemInfo
import com.pinload.di.ViewModelFactory

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import javax.inject.Inject

class MainActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        (application as PinLoadApplication).getAppComponent().doInjection(this)
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(MainViewModel::class.java)
        viewModel.mainResponse().observe(this, Observer { apiResponse ->
            if (apiResponse != null) {
                consumeResponse(apiResponse)
            }
        })

        loadContent()
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
    }

    private fun updateList(items: List<ItemInfo>){
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayout.VERTICAL, false)
        val adapter = ItemListAdapter(items)
        recyclerView.adapter = adapter
    }

    /**
     * Since Cache is enabled, internet check is commented out
     */
    private fun loadContent() {
//        if (!isOnline(this))
//            ToastUtils.showShortToast(this, resources.getString(R.string.network_error))
//        else
            viewModel.hitMainpageContent()
    }

    /**
     * Method that update with the response
     */
    override fun consumeResponse(apiResponse: ApiResponse) {
        when (apiResponse.status) {
            Status.LOADING -> progressDialog.show()
            Status.SUCCESS -> {
                progressDialog.dismiss()
                val gson = Gson()
                val  items = gson.fromJson(apiResponse.data, Array<ItemInfo>::class.java).toList()
                updateList(items)
                ToastUtils.showLongToast(this, "Total items: ${items.size}")
            }
            Status.ERROR -> {
                progressDialog.dismiss()
                print(apiResponse.error!!.localizedMessage)
            }
            else -> {
                println("Completed! ${apiResponse.status}")
            }
        }
    }
}

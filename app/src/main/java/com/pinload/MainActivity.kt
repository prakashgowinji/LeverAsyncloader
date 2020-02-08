package com.pinload

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.support.design.widget.Snackbar
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.LinearLayout
import com.google.gson.Gson
import com.pin.lever.utils.*
import com.pinload.app.PinLoadApplication
import com.pinload.datamodel.ItemInfo
import com.pinload.di.ViewModelFactory
import com.pinload.utils.Constants.KEY_IS_DATA_LOADED
import com.pinload.utils.ListItemClickListener

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import javax.inject.Inject

class MainActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    private lateinit var viewModel: MainViewModel
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        sharedPreferences = PreferenceHelper.defaultPrefs(this)

        (application as PinLoadApplication).getAppComponent().doInjection(this)
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(MainViewModel::class.java)
        viewModel.mainResponse().observe(this, Observer { apiResponse ->
            if (apiResponse != null) {
                consumeResponse(apiResponse)
            }
        })

        if(sharedPreferences.getBoolean(KEY_IS_DATA_LOADED, false)){
            viewModel.hitMainPageContent()
        } else {
            loadContent()
        }

        btnRetry.setOnClickListener {
            loadContent()
        }
    }

    private fun updateList(items: MutableList<ItemInfo>) {
        sharedPreferences.edit().putBoolean(KEY_IS_DATA_LOADED, true).apply()
        groupAlert.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayout.VERTICAL, false)
        val adapter = ListItemsAdapter(items, object: ListItemClickListener {
            override fun onItemClick(index: Int) {
                ToastUtils.showShortToast(applicationContext, items[index].user.name)
            }
        })
        recyclerView.adapter = adapter
    }

    /**
     * Hit the list items to show or alert if there is no connectivity
     */
    private fun loadContent() {
        if (!isOnline(this)) {
            groupAlert.visibility = View.VISIBLE
            textNoItem.text = getString(R.string.no_network)
            recyclerView.visibility = View.GONE
            Snackbar.make(parentView, getString(R.string.no_network), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.lbl_connect)) {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }.show()
            ToastUtils.showShortToast(this, resources.getString(R.string.network_error))
        } else {
            viewModel.hitMainPageContent()
        }
    }

    /**
     * Method that update with the response
     */
    override fun consumeResponse(apiResponse: ApiResponse) {
        when (apiResponse.status) {
            Status.LOADING -> progressDialog.show()
            Status.SUCCESS -> {
                progressDialog.dismiss()
                val items = Gson().fromJson(apiResponse.data, Array<ItemInfo>::class.java).toList()
                updateList(items as MutableList)
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

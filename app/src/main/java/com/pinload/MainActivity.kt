package com.pinload

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity;
import android.view.Menu
import android.view.MenuItem
import com.pin.lever.Lever
import com.pin.lever.utils.ApiResponse
import com.pin.lever.utils.Status
import com.pin.lever.utils.ToastUtils
import com.pin.lever.utils.isOnline
import com.pinload.app.PinLoadApplication
import com.pinload.di.ViewModelFactory

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import javax.inject.Inject

class MainActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    lateinit var viewModel: MainViewModel

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

        Lever.get().load("http://i.imgur.com/DvpvklR.png").into(imageView);
        loadContent()
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
    }

    fun loadContent() {
        if (!isOnline(this))
            ToastUtils.showShortToast(this, resources.getString(R.string.network_error))
        else
            viewModel.hitMainpageContent()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun consumeResponse(apiResponse: ApiResponse) {
        when (apiResponse.status) {
            Status.LOADING -> progressDialog.show()
            Status.SUCCESS -> {
                progressDialog.dismiss()
                ToastUtils.showLongToast(this, apiResponse.data.toString())
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

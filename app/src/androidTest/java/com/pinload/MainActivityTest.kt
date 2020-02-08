package com.pinload

import android.content.Context
import android.net.wifi.WifiManager
import android.support.test.InstrumentationRegistry
import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.action.ViewActions.click
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.matcher.ViewMatchers.*
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import com.pinload.CustomAssertions.Companion.hasItemCount
import com.pinload.CustomMatchers.Companion.withItemCount
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    lateinit var context: Context
    @Rule
    @JvmField
    var activityRule = ActivityTestRule<MainActivity>(MainActivity::class.java)

    @Before
    fun setUp(){
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    companion object {
        fun withRecyclerView(recyclerViewId: Int): RecyclerViewMatcher {
            return RecyclerViewMatcher(recyclerViewId)
        }
    }

    @Test
    fun countPrograms() {
        onView(withId(R.id.recyclerView))
            .check(matches(withItemCount(10)))
    }

    @Test
    fun countProgramsWithViewAssertion() {
        onView(withId(R.id.recyclerView))
            .check(hasItemCount(10))
    }

    @Test
    fun clickAtPosition(){
        onView(withRecyclerView(R.id.recyclerView).atPosition(4)).noActivity()
    }

    private fun setNetworkState(isEnabled: Boolean){
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        wifi!!.isWifiEnabled = isEnabled
    }

}
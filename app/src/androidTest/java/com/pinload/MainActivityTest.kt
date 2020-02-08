package com.pinload

import android.content.Context
import android.net.wifi.WifiManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pinload.CustomAssertions.Companion.hasItemCount
import com.pinload.CustomMatchers.Companion.withItemCount
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    lateinit var context: Context

    @Before
    fun setUp() {
        ActivityScenario.launch(MainActivity::class.java)
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
    fun clickAtPosition() {
        onView(withRecyclerView(R.id.recyclerView).atPosition(4)).noActivity()
    }

}
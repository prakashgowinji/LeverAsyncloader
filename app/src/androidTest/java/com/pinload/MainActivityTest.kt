package com.pinload

import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.action.ViewActions.click
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.matcher.ViewMatchers.*
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import com.pinload.CustomAssertions.Companion.hasItemCount
import com.pinload.CustomMatchers.Companion.withItemCount
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Rule
    @JvmField
    var activityRule = ActivityTestRule<MainActivity>(MainActivity::class.java)

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

    /*@Test
    fun checkPositionAt(){
        onView(withRecyclerView(R.id.recyclerView).atPositionOnView(9, R.id.datetime)).check(matches(withText("May 29, 2016")));
    }*/

    @Test
    fun clickAtPosition(){
        onView(withRecyclerView(R.id.recyclerView).atPosition(4)).noActivity()
    }

}
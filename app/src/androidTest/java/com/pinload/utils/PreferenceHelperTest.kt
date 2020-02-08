package com.pinload.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.pinload.utils.Constants.PREFERENCE_NAME
import org.junit.After
import org.junit.Before

import org.junit.Assert.*
import org.junit.Test

class PreferenceHelperTest {
    private val context: Context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var sharedPreferences: SharedPreferences
    private val preferenceName = PREFERENCE_NAME

    @Before
    fun setUp() {
        sharedPreferences = PreferenceHelper.customPrefs(context, preferenceName)
    }

    @Test
    fun testStoreBooleanValues_AssertSavedItemRetrievingSavedItem() {
        sharedPreferences.edit().putBoolean(Constants.KEY_IS_DATA_LOADED, true).apply()
        assertTrue(sharedPreferences.getBoolean(Constants.KEY_IS_DATA_LOADED, false))
        assertFalse(!sharedPreferences.getBoolean(Constants.KEY_IS_DATA_LOADED, false))
    }


    @After
    fun tearDown() {
        sharedPreferences.edit().clear().apply()
    }
}
package com.example.infotest.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HardwareUtilsTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun getIsRooted_robolectricEnvironment_returnsZeroOrOne() {
        assertTrue(context.getIsRooted() in 0..1)
    }

    @Test
    fun getIsSimulator_robolectricEnvironment_returnsOne() {
        assertEquals(1, context.getIsSimulator())
    }
}

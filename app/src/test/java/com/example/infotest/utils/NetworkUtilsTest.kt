package com.example.infotest.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkUtilsTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun isNetworkAvailable_connectedDefaultNetwork_returnsTrue() {
        // Robolectric's default active network reports as connected
        assertTrue(context.isNetworkAvailable())
    }

    @Test
    fun getNetworkType_defaultEnvironment_returnsNone() {
        assertEquals("none", context.getNetworkType())
    }

    @Test
    fun unknownSsid_matchesFrameworkValue() {
        assertEquals("<unknown ssid>", UNKNOWN_SSID)
    }
}

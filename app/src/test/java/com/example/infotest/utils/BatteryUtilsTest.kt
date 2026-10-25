package com.example.infotest.utils

import android.content.Context
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class BatteryUtilsTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun getBatteryStatus_validContext_returnsNumericCapacity() {
        val status = context.getBatteryStatus()
        assertNotNull(status.batteryMax)
        assertNotNull(status.batteryMax!!.toDoubleOrNull())
    }

    @Test
    fun getBatteryStatus_chargingBattery_reportsCharging() {
        shadowOf(context.getSystemService(BatteryManager::class.java)).setIsCharging(true)
        assertEquals(1, context.getBatteryStatus().isCharging)
    }

    @Test
    fun getBatteryStatus_dischargingBattery_doesNotReportCharging() {
        shadowOf(context.getSystemService(BatteryManager::class.java)).setIsCharging(false)
        assertEquals(0, context.getBatteryStatus().isCharging)
    }

    @Test
    fun getBatteryStatus_validContext_returnsChargeFlags() {
        val status = context.getBatteryStatus()
        assertTrue((status.isAcCharge ?: 0) in 0..1)
        assertTrue((status.isUsbCharge ?: 0) in 0..1)
        assertTrue((status.isCharging ?: 0) in 0..1)
    }

    @Test
    fun getBatteryStatus_validContext_returnsLevelString() {
        assertNotNull(context.getBatteryStatus().batteryLevel)
    }
}

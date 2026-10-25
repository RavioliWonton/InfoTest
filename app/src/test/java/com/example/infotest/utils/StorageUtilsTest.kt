package com.example.infotest.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageUtilsTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun getStorageInfo_validContext_returnsAppMemoryFields() {
        val storage = context.getStorageInfo()
        assertNotNull(storage.appFreeMemory)
        assertNotNull(storage.appFreeMemory!!.toLongOrNull())
        assertNotNull(storage.appMaxMemory)
        assertNotNull(storage.appMaxMemory!!.toLongOrNull())
        assertNotNull(storage.appTotalMemory)
        assertNotNull(storage.appTotalMemory!!.toLongOrNull())
    }

    @Test
    fun getStorageInfo_validContext_returnsSdFlags() {
        val storage = context.getStorageInfo()
        assertTrue(storage.containSd == "0" || storage.containSd == "1")
        assertTrue(storage.extraSd == "0" || storage.extraSd == "1")
    }

    @Test
    fun getStorageInfo_validContext_returnsNonNegativeInternalStorage() {
        val storage = context.getStorageInfo()
        assertNotNull(storage.internalStorageTotal)
        assertTrue(storage.internalStorageTotal!! >= 0)
        assertNotNull(storage.internalStorageUsable)
        assertTrue(storage.internalStorageUsable!! >= 0)
    }

    @Test
    fun getStoragePair_validContext_returnsNonNegativePair() {
        val (total, free) = context.getStoragePair()
        assertTrue(total >= 0)
        assertTrue(free >= 0)
    }

    @Test
    fun getSDCardPair_noRemovableVolume_returnsNumericPair() {
        val (size, usable) = context.getSDCardPair()
        assertNotNull(size.toLongOrNull())
        assertNotNull(usable.toLongOrNull())
    }
}

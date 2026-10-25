package com.example.infotest.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UniqueIdUtilsTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // region ifNotValidAndroidID

    @Test
    fun ifNotValidAndroidID_validId_returnsId() {
        assertEquals("abc123def456", "abc123def456".ifNotValidAndroidID { "fallback" })
    }

    @Test
    fun ifNotValidAndroidID_nullValue_returnsFallback() {
        assertEquals("fallback", (null as String?).ifNotValidAndroidID { "fallback" })
    }

    @Test
    fun ifNotValidAndroidID_blankValue_returnsFallback() {
        assertEquals("fallback", "".ifNotValidAndroidID { "fallback" })
        assertEquals("fallback", "   ".ifNotValidAndroidID { "fallback" })
    }

    @Test
    fun ifNotValidAndroidID_buggyDeviceId_returnsFallback() {
        assertEquals("fallback", "9774d56d682e549c".ifNotValidAndroidID { "fallback" })
        assertEquals("fallback", "9774D56D682E549C".ifNotValidAndroidID { "fallback" })
    }

    @Test
    fun ifNotValidAndroidID_emulatorId_returnsFallback() {
        assertEquals("fallback", "dead00beef".ifNotValidAndroidID { "fallback" })
        assertEquals("fallback", "DEAD00BEEF".ifNotValidAndroidID { "fallback" })
    }

    @Test
    fun ifNotValidAndroidID_fallback_isEvaluatedAtMostOnce() {
        var calls = 0
        val result = "9774d56d682e549c".ifNotValidAndroidID {
            calls++
            "fallback"
        }
        assertEquals("fallback", result)
        assertEquals(1, calls)
    }

    @Test
    fun ifNotValidAndroidID_validId_doesNotEvaluateFallback() {
        var calls = 0
        val result = "0123456789abcdef".ifNotValidAndroidID {
            calls++
            "fallback"
        }
        assertEquals("0123456789abcdef", result)
        assertEquals(0, calls)
    }

    @Test
    fun ifNotValidAndroidID_similarButDifferentIds_areKept() {
        assertEquals("dead00beef00", "dead00beef00".ifNotValidAndroidID { "fallback" })
        assertEquals("9774d56d682e549d", "9774d56d682e549d".ifNotValidAndroidID { "fallback" })
    }

    // endregion

    // region context based helpers (Robolectric)

    @Test
    fun isGoogleServiceAvailable_withoutPlayServices_returnsFalse() {
        assertFalse(context.isGoogleServiceAvailable())
    }

    @Test
    fun getAndroidID_defaultEnvironment_returnsNonNullString() {
        assertNotNull(context.contentResolver.getAndroidID())
    }

    // endregion
}

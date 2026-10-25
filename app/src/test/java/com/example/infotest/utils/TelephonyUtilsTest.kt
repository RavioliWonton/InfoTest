package com.example.infotest.utils

import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelephonyUtilsTest {

    // region isIMEIValid

    @Test
    fun isIMEIValid_fifteenDigits_returnsTrue() {
        assertTrue("356938035643809".isIMEIValid())
    }

    @Test
    fun isIMEIValid_fourteenOrSixteenDigits_returnsTrue() {
        assertTrue("35693803564380".isIMEIValid())
        assertTrue("3569380356438090".isIMEIValid())
    }

    @Test
    fun isIMEIValid_blankValue_returnsFalse() {
        assertFalse("".isIMEIValid())
        assertFalse("   ".isIMEIValid())
    }

    @Test
    fun isIMEIValid_nullLiteral_returnsFalse() {
        assertFalse("null".isIMEIValid())
    }

    @Test
    fun isIMEIValid_allZeros_returnsFalse() {
        assertFalse("000000000000000".isIMEIValid())
    }

    @Test
    fun isIMEIValid_nonDigit_returnsFalse() {
        assertFalse("35693803564380a".isIMEIValid())
    }

    @Test
    fun isIMEIValid_wrongLength_returnsFalse() {
        assertFalse("3569380356438".isIMEIValid())
        assertFalse("35693803564380901".isIMEIValid())
    }

    // endregion

    // region determineDataNetworkType

    @Test
    fun determineDataNetworkType_2gTypes_returns2g() {
        assertEquals("2g", TelephonyManager.NETWORK_TYPE_GSM.determineDataNetworkType())
        assertEquals("2g", TelephonyManager.NETWORK_TYPE_GPRS.determineDataNetworkType())
        assertEquals("2g", TelephonyManager.NETWORK_TYPE_EDGE.determineDataNetworkType())
        assertEquals("2g", TelephonyManager.NETWORK_TYPE_CDMA.determineDataNetworkType())
        assertEquals("2g", TelephonyManager.NETWORK_TYPE_1xRTT.determineDataNetworkType())
        assertEquals("2g", TelephonyManager.NETWORK_TYPE_IDEN.determineDataNetworkType())
    }

    @Test
    fun determineDataNetworkType_3gTypes_returns3g() {
        assertEquals("3g", TelephonyManager.NETWORK_TYPE_UMTS.determineDataNetworkType())
        assertEquals("3g", TelephonyManager.NETWORK_TYPE_EVDO_0.determineDataNetworkType())
        assertEquals("3g", TelephonyManager.NETWORK_TYPE_EVDO_A.determineDataNetworkType())
        assertEquals("3g", TelephonyManager.NETWORK_TYPE_EVDO_B.determineDataNetworkType())
        assertEquals("3g", TelephonyManager.NETWORK_TYPE_HSDPA.determineDataNetworkType())
        assertEquals("3g", TelephonyManager.NETWORK_TYPE_HSUPA.determineDataNetworkType())
        assertEquals("3g", TelephonyManager.NETWORK_TYPE_HSPA.determineDataNetworkType())
        assertEquals("3g", TelephonyManager.NETWORK_TYPE_HSPAP.determineDataNetworkType())
        assertEquals("3g", TelephonyManager.NETWORK_TYPE_EHRPD.determineDataNetworkType())
        assertEquals("3g", TelephonyManager.NETWORK_TYPE_TD_SCDMA.determineDataNetworkType())
    }

    @Test
    fun determineDataNetworkType_4gTypes_returns4g() {
        assertEquals("4g", TelephonyManager.NETWORK_TYPE_LTE.determineDataNetworkType())
        assertEquals("4g", TelephonyManager.NETWORK_TYPE_IWLAN.determineDataNetworkType())
    }

    @Test
    fun determineDataNetworkType_5gType_returns5g() {
        assertEquals("5g", TelephonyManager.NETWORK_TYPE_NR.determineDataNetworkType())
    }

    @Test
    fun determineDataNetworkType_knownSubtypeName_returns3g() {
        assertEquals("3g", (null as Int?).determineDataNetworkType("WCDMA"))
        assertEquals("3g", (null as Int?).determineDataNetworkType("td-scdma"))
        assertEquals("3g", (null as Int?).determineDataNetworkType("CDMA2000"))
    }

    @Test
    fun determineDataNetworkType_unknownType_returnsNone() {
        assertEquals("none", (null as Int?).determineDataNetworkType())
        assertEquals("none", (null as Int?).determineDataNetworkType("NR"))
        assertEquals("none", 9999.determineDataNetworkType("LTE"))
    }

    // endregion

    // region context based helpers (Robolectric)

    @Test
    fun getDbmCompat_noCellInfo_returnsMinusOne() {
        assertEquals(-1, ApplicationProvider.getApplicationContext<android.content.Context>().getDbmCompat())
    }

    // endregion
}

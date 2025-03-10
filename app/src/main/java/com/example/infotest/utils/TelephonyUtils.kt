@file:Suppress("DEPRECATION")

package com.example.infotest.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CarrierConfigManager
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import androidx.collection.objectListOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.PermissionChecker
import androidx.core.content.getSystemService
import androidx.core.telephony.TelephonyManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

@Suppress("unused")
@RequiresApi(Build.VERSION_CODES.Q)
fun ComponentActivity.getCellInfoAsync(callback: (MutableList<CellInfo>) -> Unit) {
    if (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PermissionChecker.PERMISSION_GRANTED) return
    lifecycle.addObserver(LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START && atLeastQ)
            getSystemService<TelephonyManager>()?.requestCellInfoUpdate(Dispatchers.IO.asExecutor(), object : TelephonyManager.CellInfoCallback() {
                override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                    callback.invoke(cellInfo)
                }
            })
    })
}
@Composable
@RequiresApi(Build.VERSION_CODES.Q)
fun GetCellInfoComposeAsync(callback: (MutableList<CellInfo>) -> Unit) {
    val lifecycleOwner = rememberUpdatedState(newValue = LocalLifecycleOwner.current)
    val context = LocalContext.current
    if (PermissionChecker.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PermissionChecker.PERMISSION_GRANTED) return
    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && atLeastQ)
                context.getSystemService<TelephonyManager>()?.requestCellInfoUpdate(Dispatchers.IO.asExecutor(), object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                        callback.invoke(cellInfo)
                    }
                })
        }
        lifecycleOwner.value.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.value.lifecycle.removeObserver(observer)
        }
    }
}

@RequiresPermission(Manifest.permission.READ_PHONE_STATE)
fun Context.getDefaultSubscriptionId() = getSystemService<TelephonyManager>()?.let(TelephonyManagerCompat::getSubscriptionId)?.takeIf { it != Int.MAX_VALUE } ?:
    if (atLeastN) { { SubscriptionManager.getDefaultSubscriptionId() }.catchReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID) }
    else if (atLeastLM) getSystemService<SmsManager>()?.subscriptionId ?: -1
    else -1

@Suppress("HardwareIds")
@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
@Throws(SecurityException::class)
fun TelephonyManager.getImeiCompat(slotIndex: Int): String? =
    if (atLeastQ) getImei(slotIndex)
    else if (atLeastM) getDeviceId(slotIndex)
    else {// TelephonyManagerCompat.getImei
        { javaClass.getDeclaredAccessibleMethod("getDeviceId", Int.Companion::class.java)
            ?.invoke(this, slotIndex) as String }.catchReturnNull(null, SecurityException::class.java)
    }

@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
fun Context.getDeviceIdCompat(): String = {
    getSystemService<TelephonyManager>()?.let {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_GSM) ||
            !atLeastQ && packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CDMA))
            TelephonyManagerCompat.getImei(it)
        else if (atLeastQ && packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CDMA)) it.meid
        else null
    }
}.catchEmpty()

@Suppress("HardwareIds")
@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS], conditional = true)
fun Context.getPhoneNumber() = {
    (if (atLeastT) getSystemService<SubscriptionManager>()?.getPhoneNumber(getDefaultSubscriptionId())
    else if (atLeastLM) getSystemService<SubscriptionManager>()?.getActiveSubscriptionInfo(getDefaultSubscriptionId())?.number
    else getSystemService<TelephonyManager>()?.line1Number)?.takeIf { getSystemService<TelephonyManager>()?.simState?.equals(TelephonyManager.SIM_STATE_READY) == true }
}.catchEmpty()

@WorkerThread
@RequiresPermission(Manifest.permission.READ_PHONE_STATE)
fun Context.getNetworkOperatorNameCompat() = { if (atLeastQ) getSystemService<CarrierConfigManager>()?.let { manager ->
    (if (atLeastU) manager.getConfig(CarrierConfigManager.KEY_CARRIER_NAME_STRING) else manager.config)
        ?.takeIf { CarrierConfigManager.isConfigForIdentifiedCarrier(it) && it.getBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL) }
        ?.getString(CarrierConfigManager.KEY_CARRIER_NAME_STRING)
    } else getSystemService<TelephonyManager>()?.let { manager ->
        if (atLeastO) manager.carrierConfig?.takeIf { it.getBoolean("carrier_name_override_bool") }?.getString("carrier_name_string")
        else manager.networkOperatorName
    }
}.catchReturnNull(getSystemService<TelephonyManager>()?.networkOperatorName.orEmpty())

fun Int?.determineDataNetworkType(subNetworkTypeName: String? = null): String = when (this) {
    TelephonyManager.NETWORK_TYPE_GSM, TelephonyManager.NETWORK_TYPE_GPRS,
    TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_EDGE,
    TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> "2g"
    TelephonyManager.NETWORK_TYPE_TD_SCDMA, TelephonyManager.NETWORK_TYPE_EVDO_A,
    TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0,
    TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
    TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD,
    TelephonyManager.NETWORK_TYPE_HSPAP -> "3g"
    TelephonyManager.NETWORK_TYPE_IWLAN, TelephonyManager.NETWORK_TYPE_LTE -> "4g"
    TelephonyManager.NETWORK_TYPE_NR -> "5g"
    else -> objectListOf("TD-SCDMA", "WCDMA", "CDMA2000").any {
        subNetworkTypeName.contentEquals(it, true)
    }.then("3g") ?: "none"
}

@RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
fun Context.getDbmCompat() = { getSystemService<TelephonyManager>()?.allCellInfo?.lastDbmCompat }.catchReturn(-1)

val Collection<CellInfo?>.lastDbmCompat: Int
    get() = { mapNotNull {
        when {
            atLeastR -> it?.cellSignalStrength
            it is CellInfoGsm -> it.cellSignalStrength
            it is CellInfoCdma -> it.cellSignalStrength
            atLeastQ && it is CellInfoTdscdma -> it.cellSignalStrength
            it is CellInfoWcdma -> it.cellSignalStrength
            it is CellInfoLte -> it.cellSignalStrength
            atLeastQ && it is CellInfoNr -> it.cellSignalStrength
            else -> null
        }?.dbm
    }.lastOrNull { it != if (atLeastQ) CellInfo.UNAVAILABLE else Int.MIN_VALUE }
}.catchReturn(-1)
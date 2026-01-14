@file:Suppress("DEPRECATION")

package com.example.infotest.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Parcel
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

fun String.isIMEIValid() = isNotBlank() && !contentEquals("null") && !contains('0')

// https://github.com/happylishang/AntiFakerAndroidChecker/tree/master/antifake/src/main/java/com/snail/antifake/deviceid/deviceid
@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
fun Context.getDeviceIdCompat(): String = {
    getDeviceIdViaTransaction().ifBlank { getDeviceIdViaTransaction(false).ifBlank {
        getDeviceIdViaBinder().ifBlank { getDeviceIdViaBinder(false).ifBlank {
            getDeviceIdViaReflection().ifBlank { getDeviceIdViaReflection(false).ifBlank {
                getSystemService<TelephonyManager>()?.let {
                    if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_GSM) ||
                        !atLeastQ && packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CDMA))
                        TelephonyManagerCompat.getImei(it).takeIf { imei -> imei?.isIMEIValid() == true }
                    else if (atLeastQ && packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CDMA)) it.meid
                    else null
                } }
            } }
        } }
    }
}.catchEmpty()
@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
fun Context.getDeviceIdViaTransaction(isViaTelephony: Boolean = true) = {
    (getClassOrNull(if (isViaTelephony) $$"com.android.internal.telephony.ITelephony$Stub" else $$"com.android.internal.telephony.IPhoneSubInfo$Stub")
        ?.getDeclaredAccessibleMethod("asInterface", IBinder::class.java)
        ?.invoke(null, getClassOrNull("android.os.ServiceManager")?.getDeclaredAccessibleMethod("getService",
            String::class.java)?.invoke(null, if (isViaTelephony) Context.TELEPHONY_SERVICE else "iphonesubinfo")) as IBinder?)?.let {
                if (it.javaClass.hasMethod(true, "getDeviceId", String::class.java)) {
                    val data = Parcel.obtain()
                    val reply = Parcel.obtain()
                    try {
                        data.writeInterfaceToken(it.interfaceDescriptor ?: it.getInterfaceDescriptorViaReflection())
                        data.writeString(packageName)
                        it.transact(it.getTransactionIdViaReflection("TRANSACTION_getDeviceId"), data, reply, 0)
                        reply.readString()
                    } finally {
                        data.recycle()
                        reply.recycle()
                    }
                } else if (it.javaClass.hasMethod(true, "getDeviceId")) {
                    val data = Parcel.obtain()
                    val reply = Parcel.obtain()
                    try {
                        data.writeInterfaceToken(it.interfaceDescriptor ?: it.getInterfaceDescriptorViaReflection())
                        it.transact(it.getTransactionIdViaReflection("TRANSACTION_getDeviceId"), data, reply, 0)
                        reply.readString()
                    } finally {
                        data.recycle()
                        reply.recycle()
                    }
                } else null

        }
}.catchEmpty()
@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
fun Context.getDeviceIdViaBinder(isViaTelephony: Boolean = true) = {
    getClassOrNull(if (isViaTelephony) $$"com.android.internal.telephony.ITelephony$Stub" else $$"com.android.internal.telephony.IPhoneSubInfo$Stub")
        ?.getDeclaredAccessibleMethod("asInterface", IBinder::class.java)
        ?.invoke(null, getClassOrNull("android.os.ServiceManager")?.getDeclaredAccessibleMethod("getService",
            String::class.java)?.invoke(null, if (isViaTelephony) Context.TELEPHONY_SERVICE else "iphonesubinfo"))?.javaClass?.let {
            (it.getDeclaredAccessibleMethod("getDeviceId", String::class.java)?.invoke(null, packageName) as? String).orEmpty()
                .ifBlank { it.getDeclaredAccessibleMethod("getDeviceId")?.invoke(null, emptyArray<Any>()) as String? }
        }
}.catchEmpty()
@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
fun Context.getDeviceIdViaReflection(isViaTelephony: Boolean = true) = {
    TelephonyManager::class.java.getDeclaredAccessibleMethod(if (isViaTelephony) "getITelephony" else "getSubscriberInfo")
        ?.invoke(getSystemService<TelephonyManager>())
        ?.takeIf { it.javaClass.hasMethod(true, "asBinder") }?.let {
            (it.javaClass.getDeclaredAccessibleMethod("getDeviceId", String::class.java)
            ?.invoke(it, packageName) as? String).orEmpty().ifBlank {
                it.javaClass.getDeclaredAccessibleMethod("getDeviceId")?.invoke(it, emptyArray<Any>()) as String?
            }
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
fun Context.getNetworkOperatorNameCompat() = {
    if (atLeastQ) getSystemService<CarrierConfigManager>()?.let { manager ->
        (if (atLeastU) manager.getConfig(CarrierConfigManager.KEY_CARRIER_NAME_STRING) else manager.config)
            ?.takeIf { CarrierConfigManager.isConfigForIdentifiedCarrier(it) && it.getBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL) }
            ?.getString(CarrierConfigManager.KEY_CARRIER_NAME_STRING)
    } else getSystemService<TelephonyManager>()?.let { manager ->
        if (atLeastO) manager.carrierConfig?.takeIf { it.getBoolean("carrier_name_override_bool") }?.getString("carrier_name_string")
        else manager.simOperatorName.takeIf { manager.phoneType == TelephonyManager.PHONE_TYPE_CDMA } ?: manager.networkOperatorName
    }
}.catchReturn(getSystemService<TelephonyManager>()?.networkOperatorName.orEmpty())

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
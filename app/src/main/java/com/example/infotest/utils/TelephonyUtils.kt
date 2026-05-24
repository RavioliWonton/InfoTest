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
import androidx.core.telephony.SubscriptionManagerCompat
import androidx.core.telephony.TelephonyManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import splitties.init.appCtx
import kotlin.collections.listOf

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

private fun getTelephonyStubClass(isViaTelephony: Boolean) = getClassOrNull(
    if (isViaTelephony) $$"com.android.internal.telephony.ITelephony$Stub"
    else $$"com.android.internal.telephony.IPhoneSubInfo$Stub")
private fun getTelephonyServiceName(isViaTelephony: Boolean) = if (isViaTelephony) Context.TELEPHONY_SERVICE else "iphonesubinfo"
private fun getTelephonyTransactionCode(isViaTelephony: Boolean, methodSignatureAndActions: List<Triple<String, List<Class<*>>?, (Parcel.() -> Unit)?>>) =
    { methodSignatureAndActions.mapNotNull { (methodName, paramTypes, action) -> Triple((getTelephonyStubClass(isViaTelephony)?.getAccessibleField("TRANSACTION_$methodName")?.get(null) as? Int)
        ?.takeIf { getTelephonyServiceBinder(isViaTelephony)?.javaClass?.hasMethod(true, methodName, *paramTypes?.toTypedArray().orEmpty()) == true } ?: return@mapNotNull null, paramTypes, action) } }.catchReturnNull()
private fun getTelephonyServiceBinder(isViaTelephony: Boolean): IBinder? = { getClassOrNull("android.os.ServiceManager")
        ?.getDeclaredAccessibleMethod("getService", String::class.java)
        ?.invoke(null, getTelephonyServiceName(isViaTelephony)) as? IBinder }.catchReturnNull()
private fun getTelephonyFieldViaTransaction(isViaTelephony: Boolean, methodSignatureAndActions: List<Triple<String, List<Class<*>>?, (Parcel.() -> Unit)?>>): String? = {
    getTelephonyServiceBinder(isViaTelephony)?.let {
        getTelephonyTransactionCode(isViaTelephony, methodSignatureAndActions)?.firstNotNullOfOrNull { (code, type, action) ->
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(it.interfaceDescriptor ?: it.getInterfaceDescriptorViaReflection())
                if (type?.isNotEmpty() == true && action != null) data.action()
                it.transact(code, data, reply, 0)
                reply.readException()
                reply.readString().orEmpty()
            } catch (_: Exception) { null } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }}.catchReturnNull()
private fun getTelephonyFieldViaBinder(isViaTelephony: Boolean, methodSignatureAndActions: List<Triple<String, List<Class<*>>?, (String) -> Array<Any?>>>): String? =
    { getTelephonyStubClass(isViaTelephony)?.getDeclaredAccessibleMethod("asInterface", IBinder::class.java)
        ?.invoke(null, getTelephonyServiceBinder(isViaTelephony))?.let {
            methodSignatureAndActions.firstNotNullOfOrNull { (methodName, paramTypes, argsSupplier) ->
                runCatching { it.javaClass.getDeclaredAccessibleMethod(methodName, *paramTypes?.toTypedArray().orEmpty())
                    .invoke(it, *argsSupplier(methodName)) as? String
                }.getOrNull()
            }
        } }.catchReturnNull()
private fun Context.getTelephonyFieldViaReflection(isViaTelephony: Boolean, methodSignatureAndActions: List<Triple<String, List<Class<*>>?, (String) -> Array<Any?>>>): String? = {
    getSystemService<TelephonyManager>()?.let { TelephonyManager::class.java.getDeclaredAccessibleMethod(
        if (isViaTelephony) "getITelephony" else "getSubscriberInfo").invoke(it)
        .takeIf { method -> method.javaClass.hasMethod(true, "asBinder") } }?.let {
        methodSignatureAndActions.firstNotNullOfOrNull { (methodName, paramTypes, argsSupplier) ->
            runCatching {
                it.javaClass.getDeclaredAccessibleMethod(methodName, *paramTypes?.toTypedArray().orEmpty())
                    .invoke(it, *argsSupplier(methodName)) as? String
                }.getOrNull()
            }
        }
    }.catchReturnNull()
private fun getTelephonyFieldThroughThreeTierFallback(context: Context = appCtx, methodSignature: List<Pair<String, List<Any?>>>): String? =
    getTelephonyFieldViaTransaction(true, methodSignature.mapNotNull { Triple(it.first.takeIf { name -> name.isNotBlank() } ?: return@mapNotNull null, it.second.paramTypes()) { it.second.forEach { value -> writeAny(value) } } }).takeIf { it?.isNotBlank() == true }
        ?: getTelephonyFieldViaTransaction(false, methodSignature.mapNotNull { Triple(it.first.takeIf { name -> name.isNotBlank() } ?: return@mapNotNull null, it.second.paramTypes()) { it.second.forEach { value -> writeAny(value) } } }).takeIf { it?.isNotBlank() == true }
        ?: getTelephonyFieldViaBinder(true, methodSignature.mapNotNull { Triple(it.first.takeIf { name -> name.isNotBlank() } ?: return@mapNotNull null, it.second.paramTypes()) { _ -> it.second.toTypedArray() } }).takeIf { it?.isNotBlank() == true }
        ?: getTelephonyFieldViaBinder(false, methodSignature.mapNotNull { Triple(it.first.takeIf { name -> name.isNotBlank() } ?: return@mapNotNull null, it.second.paramTypes()) { _ -> it.second.toTypedArray() } }).takeIf { it?.isNotBlank() == true }
        ?: context.getTelephonyFieldViaReflection(true, methodSignature.mapNotNull { Triple(it.first.takeIf { name -> name.isNotBlank() } ?: return@mapNotNull null, it.second.paramTypes()) { _ -> it.second.toTypedArray() } }).takeIf { it?.isNotBlank() == true }
        ?: context.getTelephonyFieldViaReflection(false, methodSignature.mapNotNull { Triple(it.first.takeIf { name -> name.isNotBlank() } ?: return@mapNotNull null, it.second.paramTypes()) { _ -> it.second.toTypedArray() } }).takeIf { it?.isNotBlank() == true }

private fun getSubscriptionStubClass() = getClassOrNull($$"com.android.internal.telephony.ISub$Stub")
private fun getSubscriptionServiceName() = "isub"
private fun getSubscriptionServiceBinder(): IBinder? = {
    getClassOrNull("android.os.ServiceManager")
        ?.getDeclaredAccessibleMethod("getService", String::class.java)
        ?.invoke(null, getSubscriptionServiceName()) as? IBinder
}.catchReturnNull()

private fun getSubscriptionTransactionCode(methodSignatureAndActions: List<Triple<String, List<Class<*>>?, (Parcel.() -> Unit)?>>) =
    { methodSignatureAndActions.mapNotNull { (methodName, paramTypes, action) ->
        Triple(
            (getSubscriptionStubClass()?.getAccessibleField("TRANSACTION_$methodName")?.get(null) as? Int)
                ?.takeIf { getSubscriptionServiceBinder()?.javaClass?.hasMethod(true, methodName, *paramTypes?.toTypedArray().orEmpty()) == true }
                ?: return@mapNotNull null,
            paramTypes, action
        )
    } }.catchReturnNull()
private fun getSubscriptionFieldViaTransaction(methodSignatureAndActions: List<Triple<String, List<Class<*>>?, (Parcel.() -> Unit)?>>): Int? = {
    getSubscriptionServiceBinder()?.let { binder ->
        getSubscriptionTransactionCode(methodSignatureAndActions)?.firstNotNullOfOrNull { (code, type, action) ->
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(binder.interfaceDescriptor ?: binder.getInterfaceDescriptorViaReflection())
                if (type?.isNotEmpty() == true && action != null) data.action()
                binder.transact(code, data, reply, 0)
                reply.readException()
                reply.readInt()
            } catch (_: Exception) { null } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}.catchReturnNull()
private fun getSubscriptionFieldViaBinder(methodSignatureAndActions: List<Triple<String, List<Class<*>>?, (String) -> Array<Any?>>>): Int? =
    { getSubscriptionStubClass()?.getDeclaredAccessibleMethod("asInterface", IBinder::class.java)
        ?.invoke(null, getSubscriptionServiceBinder())?.let { service ->
            methodSignatureAndActions.firstNotNullOfOrNull { (methodName, paramTypes, argsSupplier) ->
                runCatching {
                    service.javaClass
                        .getDeclaredAccessibleMethod(methodName, *paramTypes?.toTypedArray().orEmpty())
                        .invoke(service, *argsSupplier(methodName)) as? Int
                }.getOrNull()
            }
        } }.catchReturnNull()
private fun Context.getSubscriptionFieldViaReflection(methodSignatureAndActions: List<Triple<String, List<Class<*>>?, (String) -> Array<Any?>>>): Int? = {
    val service = runCatching {
        getSystemService<TelephonyManager>()?.let { tm ->
            TelephonyManager::class.java.getDeclaredAccessibleMethod("getISub").invoke(tm)
        }
    }.getOrNull() ?: getSubscriptionServiceBinder()

    service?.let { obj ->
        methodSignatureAndActions.firstNotNullOfOrNull { (methodName, paramTypes, argsSupplier) ->
            runCatching {
                obj.javaClass
                    .getDeclaredAccessibleMethod(methodName, *paramTypes?.toTypedArray().orEmpty())
                    .invoke(obj, *argsSupplier(methodName)) as? Int
            }.getOrNull()
        }
    }
}.catchReturnNull()
private fun getDefaultSubscriptionIdThroughThreeFallback() =
    getSubscriptionFieldViaTransaction(listOf(Triple("getDefaultSubIdAsUser", listOf(Int::class.java)) { writeInt(getUserIdByReflection()) }, Triple("getDefaultSubId", listOf()) {}))
        ?: getSubscriptionFieldViaBinder(listOf(Triple("getDefaultSubIdAsUser", listOf(Int::class.java)) { arrayOf(getUserIdByReflection()) }, Triple("getDefaultSubId", listOf()) { emptyArray() }))
        ?: appCtx.getSubscriptionFieldViaReflection(listOf(Triple("getDefaultSubIdAsUser", listOf(Int::class.java)) { arrayOf(getUserIdByReflection()) }, Triple("getDefaultSubId", listOf()) { emptyArray() }))

@RequiresPermission(Manifest.permission.READ_PHONE_STATE)
fun Context.getDefaultSubscriptionId() = getSystemService<TelephonyManager>()?.let(TelephonyManagerCompat::getSubscriptionId)?.takeIf { it != Int.MAX_VALUE } ?:
    if (atLeastN) { { SubscriptionManager.getDefaultSubscriptionId() }.catchReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID) }
    else if (atLeastLM) getSystemService<SmsManager>()?.subscriptionId ?: getDefaultSubscriptionIdThroughThreeFallback() ?: -1
    else getDefaultSubscriptionIdThroughThreeFallback() ?: -1

@Suppress("HardwareIds")
@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
@Throws(SecurityException::class)
fun TelephonyManager.getImeiCompat(slotIndex: Int): String? =
    if (atLeastQ) getImei(slotIndex)
    else if (atLeastM) getDeviceId(slotIndex)
    else if (javaClass.hasMethod(true, "getDeviceId", Int::class.java)) {// TelephonyManagerCompat.getImei
        { javaClass.getDeclaredAccessibleMethod("getDeviceId", Int::class.java)
            .invoke(this, slotIndex) as String }.catchReturnNull(null, SecurityException::class.java)
    } else getTelephonyFieldThroughThreeTierFallback(methodSignature = listOf("getDeviceIdForPhone" to listOf(slotIndex, appCtx.packageName)))
@Suppress("HardwareIds")
@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
@Throws(SecurityException::class)
fun TelephonyManager.getImeiCompatViaSubscriptionId(subscriptionId: Int): String? =
    if (atLeastQ) getImei(SubscriptionManagerCompat.getSlotIndex(subscriptionId))
    else if (atLeastM) getDeviceId(SubscriptionManagerCompat.getSlotIndex(subscriptionId))
    else {// TelephonyManagerCompat.getImei
        { appCtx.getImeiWithSubId(subscriptionId) ?: javaClass.getDeclaredAccessibleMethod("getDeviceId", Int::class.java)
            .invoke(this, SubscriptionManagerCompat.getSlotIndex(subscriptionId)) as String }.catchReturnNull(null, SecurityException::class.java)
    }
@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
fun Context.getImeiWithSubId(subscriptionId: Int) = getTelephonyFieldThroughThreeTierFallback(methodSignature = listOf("getImeiForSubscriber" to listOf(subscriptionId, packageName)))

fun String.isIMEIValid() = isNotBlank() && !contentEquals("null") && !all { it == '0' } && length in 14..16

// https://github.com/happylishang/AntiFakerAndroidChecker/tree/master/antifake/src/main/java/com/snail/antifake/deviceid/deviceid
@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
fun Context.getDeviceIdCompat(): String = {
    getTelephonyFieldThroughThreeTierFallback(this, listOf("getDeviceId" to listOf(packageName), "getDeviceId" to listOf())).orEmpty().ifBlank {
        getDeviceIdViaSystemProperty().ifBlank { getSystemService<TelephonyManager>()?.let {
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_GSM) ||
                !atLeastQ && packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CDMA))
                TelephonyManagerCompat.getImei(it).takeIf { imei -> imei?.isIMEIValid() == true }
            else if (atLeastQ && packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CDMA)) it.meid
            else null
        } } }
}.catchEmpty()
private fun Context.getDeviceIdViaSystemProperty() = {
    listOf("ro.telephony.imei", "ro.ril.IMEI", "persist.radio.imei", "ril.IMEI",
        "ro.product.imei", "ro.boot.imei", "persist.vendor.radio.imei")
        .filter { hasSystemProperty(it) }
        .map { getSystemPropertyWithFallback(it, "") }
        .firstOrNull { it.isNotBlank() }
}.catchEmpty()

@Suppress("HardwareIds")
@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS], conditional = true)
fun Context.getPhoneNumber() = {
    (if (atLeastT) getSystemService<SubscriptionManager>()?.getPhoneNumber(getDefaultSubscriptionId())
    else if (atLeastLM) getSystemService<SubscriptionManager>()?.getActiveSubscriptionInfo(getDefaultSubscriptionId())?.number
    else getSystemService<TelephonyManager>()?.line1Number)?.takeIf { getSystemService<TelephonyManager>()?.simState?.equals(TelephonyManager.SIM_STATE_READY) == true }
        ?: getTelephonyFieldThroughThreeTierFallback(this, listOf("getLine1NumberForSubscriber" to listOf(getDefaultSubscriptionId()),
            "getLine1Number" to listOf(packageName), "getMsisdnForSubscriber" to listOf(getDefaultSubscriptionId()), "getMsisdn" to listOf(packageName)))
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
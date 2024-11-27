@file:Suppress("DEPRECATION", "unused")

package com.example.infotest.utils

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.collection.mutableObjectListOf
import androidx.collection.objectListOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.getSystemService
import androidx.core.os.HandlerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.infotest.GlobalApplication
import com.example.infotest.Network
import com.example.infotest.Wifi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import splitties.init.appCtx
import java.net.NetworkInterface
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.Path
import kotlin.io.path.exists

@delegate:RequiresApi(Build.VERSION_CODES.R)
private val emptyWifiScanCallback by lazy {
    object : WifiManager.ScanResultsCallback() {
        override fun onScanResultsAvailable() = Unit
    }
}
private val wifiNetworkRequest by lazy {
    NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .apply { if (atLeastS) setIncludeOtherUidNetworks(true) }
        .build()
}

@RequiresPermission(Manifest.permission.ACCESS_WIFI_STATE)
fun ComponentActivity.startWifiScan() {
    val wifiManager = applicationContext.getSystemService<WifiManager>()
    lifecycle.addObserver(LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START) {
            if (atLeastR) wifiManager?.registerScanResultsCallback(Dispatchers.IO.asExecutor(), emptyWifiScanCallback)
            else wifiManager?.startScan()
        }
        if (event == Lifecycle.Event.ON_STOP && atLeastR) wifiManager?.unregisterScanResultsCallback(emptyWifiScanCallback)
    })
}
@Composable
@RequiresPermission(Manifest.permission.ACCESS_WIFI_STATE)
fun StartWifiScan() {
    val lifecycleOwner = rememberUpdatedState(newValue = LocalLifecycleOwner.current)
    val context = LocalContext.current
    val wifiManager = context.getSystemService<WifiManager>()
    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START)  {
                if (atLeastR) wifiManager?.registerScanResultsCallback(Dispatchers.IO.asExecutor(), emptyWifiScanCallback)
                else wifiManager?.startScan()
            }
            if (event == Lifecycle.Event.ON_STOP && atLeastR) wifiManager?.unregisterScanResultsCallback(emptyWifiScanCallback)
        }
        lifecycleOwner.value.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.value.lifecycle.removeObserver(observer)
        }
    }
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
fun ComponentActivity.registerWifiCallback(onCapabilitiesChanged: (network: android.net.Network, networkCapabilities: NetworkCapabilities) -> Unit,
                                           onLinkPropertiesChanged: (network: android.net.Network, linkProperties: LinkProperties) -> Unit) =
    lifecycle.addObserver(processWifiCallBackObserver(applicationContext.getSystemService<ConnectivityManager>(), onCapabilitiesChanged, onLinkPropertiesChanged))
@Composable
@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
fun RegisterWifiCallback(onCapabilitiesChanged: (network: android.net.Network, networkCapabilities: NetworkCapabilities) -> Unit,
                         onLinkPropertiesChanged: (network: android.net.Network, linkProperties: LinkProperties) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    lifecycleOwner.lifecycle.addObserver(processWifiCallBackObserver(context.applicationContext.getSystemService<ConnectivityManager>(), onCapabilitiesChanged, onLinkPropertiesChanged))
}
@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
private fun processWifiCallBackObserver(manager: ConnectivityManager?, onCapabilitiesChanged: (network: android.net.Network, networkCapabilities: NetworkCapabilities) -> Unit,
                                        onLinkPropertiesChanged: (network: android.net.Network, linkProperties: LinkProperties) -> Unit) = LifecycleEventObserver { _, event ->
    val callback = if (atLeastS) object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            onCapabilitiesChanged.invoke(network, networkCapabilities)
        }
        override fun onLinkPropertiesChanged(network: android.net.Network, linkProperties: LinkProperties) {
            super.onLinkPropertiesChanged(network, linkProperties)
            onLinkPropertiesChanged.invoke(network, linkProperties)
        }
    } else object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            onCapabilitiesChanged.invoke(network, networkCapabilities)
        }
        override fun onLinkPropertiesChanged(network: android.net.Network, linkProperties: LinkProperties) {
            super.onLinkPropertiesChanged(network, linkProperties)
            onLinkPropertiesChanged.invoke(network, linkProperties)
        }
    }
    if (event == Lifecycle.Event.ON_START) {
        if (atLeastT) manager?.registerBestMatchingNetworkCallback(wifiNetworkRequest, callback,
            HandlerCompat.createAsync(Looper.myLooper()?.takeIf { it != Looper.getMainLooper() } ?: Looper.getMainLooper()))
        else manager?.registerNetworkCallback(wifiNetworkRequest, callback)
    }
    if (event == Lifecycle.Event.ON_STOP) manager?.unregisterNetworkCallback(callback)
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
fun Context.isNetworkAvailable(): Boolean = { applicationContext.getSystemService<ConnectivityManager>()?.let { manager ->
    (atLeastM && listOf(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        .all { manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(it) == true })
            || manager.activeNetworkInfo?.isConnected == true
}}.catchFalse()
fun NetworkCapabilities.isValidateNetwork(@IntRange(NetworkCapabilities.TRANSPORT_CELLULAR.toLong(), 9L) type: Int) =
    mutableObjectListOf(NetworkCapabilities.NET_CAPABILITY_INTERNET).apply { if (atLeastM) add(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }.all { hasCapability(it) }
            && hasTransport(type)

@RequiresPermission(anyOf = [Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.READ_PHONE_STATE], conditional = true)
fun Context.getNetworkType() = {
    val connectivityManager = applicationContext.getSystemService<ConnectivityManager>()
    if (atLeastN) {
        val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) "other"//"ethernet"
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) "bluetooth"
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) "wifi"
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                getSystemService<TelephonyManager>()?.dataNetworkType.determineDataNetworkType()
            else "other"
        } else "none"
    } else {
        if (connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET)
                ?.state in listOf(NetworkInfo.State.CONNECTED, NetworkInfo.State.CONNECTING)
        ) "other"//"ethernet"
        else if (connectivityManager?.activeNetworkInfo?.detailedState == NetworkInfo.DetailedState.CONNECTED) {
            when (connectivityManager.activeNetworkInfo?.type) {
                ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_WIMAX -> "wifi"
                ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_MOBILE_DUN,
                ConnectivityManager.TYPE_MOBILE_HIPRI, ConnectivityManager.TYPE_MOBILE_MMS,
                ConnectivityManager.TYPE_MOBILE_SUPL -> connectivityManager.activeNetworkInfo?.subtype
                    .determineDataNetworkType(connectivityManager.activeNetworkInfo?.subtypeName)
                ConnectivityManager.TYPE_BLUETOOTH -> "bluetooth"
                else -> "other"
            }
        } else "none"
    }
}.catchReturn("none")

val currentNetworkTimeInstant: Instant = {
    if (atLeastT && systemHasNetworkTime)
        SystemClock.currentNetworkTimeClock().instant()
    else if (GlobalApplication.trueTime.hasTheTime())
        GlobalApplication.trueTime.nowTrueOnly().toInstant()
    else if (atLeastQ && appCtx.getSystemService<LocationManager>()
            ?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true)
        SystemClock.currentGnssTimeClock().instant()
    else Clock.systemUTC().instant()
}.catchReturn(Instant.now())

private val systemHasNetworkTime = { atLeastT && SystemClock.currentNetworkTimeClock().instant().isSupported(ChronoUnit.MILLIS) }.catchFalse()

@RequiresPermission(allOf = [Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_NETWORK_STATE])
fun Context.getNetworkInfo(): Network {
    val wifiManager = applicationContext.getSystemService<WifiManager>()
    val connectivityManager = applicationContext.getSystemService<ConnectivityManager>()
    if (atLeastR) wifiManager?.registerScanResultsCallback(Dispatchers.IO.asExecutor(), emptyWifiScanCallback)
    else wifiManager?.startScan()
    return Network(
        ip = { getWifiLinkInetAddresses()?.firstOrNull()?.hostAddress }.catchEmpty(),
        configuredWifi = wifiManager?.scanResults?.mapNotNull { Wifi(
            bssid = it.BSSID, mac = it.BSSID,
            name = if (atLeastT) it.wifiSsid?.toString().orEmpty() else it.SSID,
            ssid = if (atLeastT) it.wifiSsid?.toString().orEmpty() else it.SSID
        ) } ?: emptyList(),
        currentWifi = {
            val extraInfo = connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.extraInfo
            Wifi(bssid = getWifiConnectionInfo()?.bssid.orEmpty(), mac = getWifiConnectionInfo()?.bssid.orEmpty(),
                ssid = if (atLeastQ && extraInfo?.isNotBlank() == true) extraInfo else getWifiConnectionInfo()?.ssid.orEmpty(),
                name = if (atLeastQ && extraInfo?.isNotBlank() == true) extraInfo else getWifiConnectionInfo()?.ssid.orEmpty())
        }.catchReturnNull(),
        wifiCount = wifiManager?.scanResults?.count { it != null }
    )
}

@RequiresPermission(allOf = [Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_NETWORK_STATE], conditional = true)
private fun Context.getWifiConnectionInfo() = {
    if (atLeastS) GlobalApplication.currentWifiCapabilities
    else if (atLeastQ) applicationContext.getSystemService<ConnectivityManager>()?.let { manager ->
        manager.allNetworks.mapNotNull { manager.getNetworkCapabilities(it) }
            .firstOrNull { it.isValidateNetwork(NetworkCapabilities.TRANSPORT_WIFI) }?.transportInfo as WifiInfo?
    }
    else applicationContext.getSystemService<WifiManager>()?.connectionInfo
}.catchReturnNull(applicationContext.getSystemService<WifiManager>()?.connectionInfo)

@Suppress("NewApi")
@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
private fun Context.getWifiLinkInetAddresses() = (GlobalApplication.currentWifiLinkProperties ?: applicationContext.getSystemService<ConnectivityManager>()?.let { manager ->
    manager.getLinkProperties(manager.allNetworks.firstOrNull { manager.getNetworkCapabilities(it)?.isValidateNetwork(NetworkCapabilities.TRANSPORT_WIFI) == true })
})?.linkAddresses?.mapNotNull { it.address }?.filter { !it.isLoopbackAddress }.takeIf { !it.isNullOrEmpty() }?.asObjectList()
    ?: getWifiConnectionInfo()?.let { info -> Formatter.formatIpAddress(info.ipAddress).takeIf { InetAddresses.isNumericAddress(it) }
        ?.let { objectListOf(InetAddresses.parseNumericAddress(it)) } }

@Suppress("HardwareIds")
@RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.READ_EXTERNAL_STORAGE, "android.permission.LOCAL_MAC_ADDRESS"], conditional = true)
fun Context.getWifiMac(): String = {(
        if (atLeastN) (NetworkInterface.getNetworkInterfaces().asSequence().firstOrNull { it.name.equals("wlan0", true) }
            ?: NetworkInterface.getByInetAddress(getWifiLinkInetAddresses()?.firstOrNull()))?.hardwareAddress.formatMac()
        else if (atLeastM) listOf("/sys/class/net/wlan0/address", "/sys/class/net/eth0/address").filter { Path(it).exists() }
            .firstNotNullOfOrNull { address -> execCommand("cat", address)?.bufferedReader()?.use { it.readLine() } }
        else getWifiConnectionInfo()?.macAddress)?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }
}.catchEmpty()

@OptIn(ExperimentalStdlibApi::class)
private fun ByteArray?.formatMac(): String = {
    takeIf { this?.size == 6 }?.toHexString(HexFormat { bytes.byteSeparator = ":" })
}.catchEmpty()

val UNKNOWN_SSID = if(atLeastR) WifiManager.UNKNOWN_SSID else "<unknown ssid>"
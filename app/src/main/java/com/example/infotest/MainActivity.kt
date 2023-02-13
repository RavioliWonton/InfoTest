@file:Suppress("BlockingMethodInNonBlockingContext")

package com.example.infotest

import android.Manifest
import android.annotation.SuppressLint
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.*
import android.net.Network
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.telephony.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.core.os.HandlerCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.infotest.ui.theme.InfoTestTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
    companion object {
        var text by mutableStateOf("申请权限")
    }
    private var isStartingFetch by mutableStateOf(false)
    private val permissionArray = mutableListOf(
        Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CONTACTS, Manifest.permission.READ_SMS,
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALENDAR
    )
    private val qPermissionArray = permissionArray.plusElement(Manifest.permission.ACCESS_MEDIA_LOCATION)
        .minusElement(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private val sPermissionArray = qPermissionArray.plus(arrayOf(Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)).minusElement(
        Manifest.permission.READ_EXTERNAL_STORAGE)
    private val locationServices by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val listener by lazy {
        object : LocationListenerCompat {
            override fun onLocationChanged(location: Location) {
                //Log.d("TAG", "LocationData :$location")
                GlobalApplication.gps = GPS(location.latitude.toString(),
                    location.longitude.toString(),
                    LocationCompat.getElapsedRealtimeNanos(location))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Geocoder(this@MainActivity).getFromLocation(location.latitude, location.longitude, 1) {
                        GlobalApplication.address = it.firstOrNull()
                    }
            }

            override fun onProviderDisabled(provider: String) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            @Deprecated("Deprecated in Java", ReplaceWith("Unit"))
            override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) = Unit
        }
    }
    private val callback by lazy {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                result.locations.maxWithOrNull(compareBy { LocationCompat.getElapsedRealtimeNanos(it) })
                    ?.let { location ->
                        //Log.d("TAG", "LocationData :${location.toString()}")
                        GlobalApplication.gps = GPS(location.latitude.toString(),
                            location.longitude.toString(),
                            LocationCompat.getElapsedRealtimeNanos(location))
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            Geocoder(this@MainActivity).getFromLocation(location.latitude, location.longitude, 1) {
                                GlobalApplication.address = it.firstOrNull()
                            }
                    }
            }
        }
    }
    private val wifiCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && networkCapabilities.transportInfo is WifiInfo)
                    GlobalApplication.currentWifiCapabilities = networkCapabilities.transportInfo as WifiInfo
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                if (getSystemService<ConnectivityManager>()?.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
                    GlobalApplication.currentWifiLinkProperties = linkProperties
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val systemUiController = rememberSystemUiController()
            systemUiController.setSystemBarsColor(Color.Transparent, darkIcons = isSystemInDarkTheme().not())

            InfoTestTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Greeting(name = text)
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) sPermissionArray
                        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) qPermissionArray
                        else permissionArray
                    val permission = rememberMultiplePermissionsState(permissions = permissions) { result ->
                        if (result.isNotEmpty()) {
                            if (result.all { it.value }) startFetch()
                            else ActivityCompat.finishAffinity(this)
                        }
                    }
                    if (permission.allPermissionsGranted) {
                        startFetch()
                    } else SideEffect {
                        permission.launchMultiplePermissionRequest()
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun startFetch() {
        if (!isStartingFetch) {
            isStartingFetch = true
            text = "开始抓取数据，先等待五秒钟以获得地理位置和GAID"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getSystemService<WifiManager>()?.registerScanResultsCallback(Dispatchers.IO.asExecutor(), object : WifiManager.ScanResultsCallback() {
                    override fun onScanResultsAvailable() = Unit
                })
            } else getSystemService<WifiManager>()?.startScan()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getSystemService<TelephonyManager>()?.requestCellInfoUpdate(Dispatchers.IO.asExecutor(),
                    object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                            GlobalApplication.dbm = cellInfo.mapNotNull {
                                (when {
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> it.cellSignalStrength
                                    it is CellInfoGsm -> it.cellSignalStrength
                                    it is CellInfoCdma -> it.cellSignalStrength
                                    it is CellInfoTdscdma -> it.cellSignalStrength
                                    it is CellInfoWcdma -> it.cellSignalStrength
                                    it is CellInfoLte -> it.cellSignalStrength
                                    it is CellInfoNr -> it.cellSignalStrength
                                    else -> null
                                })?.dbm
                            }.lastOrNull { it != CellInfo.UNAVAILABLE } ?: -1
                        }
                    })
            }
            getSystemService<ConnectivityManager>()?.let {
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setIncludeOtherUidNetworks(true) }
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    it.registerBestMatchingNetworkCallback(request, wifiCallback, HandlerCompat.createAsync(Looper.myLooper() ?: Looper.getMainLooper()))
                else it.registerNetworkCallback(request, wifiCallback)
            }
            getSystemService<LocationManager>()?.let {
                if (!LocationManagerCompat.isLocationEnabled(it)) {
                    startActionCompat(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                } else {
                    GoogleApiAvailability.getInstance().checkApiAvailability(locationServices)
                        .addOnSuccessListener {
                            locationServices.locationAvailability.addOnSuccessListener { availability ->
                                if (availability.isLocationAvailable) locationServices.lastLocation.addOnSuccessListener { lastKnown ->
                                    //Log.d("TAG", "onStart: $lastKnown")
                                    if (lastKnown != null && (GlobalApplication.gps?.time ?: -1L) < LocationCompat.getElapsedRealtimeNanos(lastKnown)) {
                                        GlobalApplication.gps = GPS(
                                            lastKnown.latitude.toString(),
                                            lastKnown.longitude.toString(),
                                            LocationCompat.getElapsedRealtimeNanos(lastKnown)
                                        )
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                            Geocoder(this@MainActivity).getFromLocation(lastKnown.latitude, lastKnown.longitude, 1) { list ->
                                                GlobalApplication.address = list.firstOrNull()
                                            }
                                    }
                                }
                            }
                            locationServices.requestLocationUpdates(LocationRequest.Builder(5000L)
                                .build(), Dispatchers.IO.asExecutor(), callback)
                        }
                    if (it.getProviders(true).contains(LocationManager.GPS_PROVIDER)) {
                        LocationManagerCompat.getCurrentLocation(it, LocationManager.GPS_PROVIDER, null, Dispatchers.IO.asExecutor()) { gpsLastKnown ->
                            if (gpsLastKnown != null && (GlobalApplication.gps?.time ?: -1L) < LocationCompat.getElapsedRealtimeNanos(gpsLastKnown)) {
                                GlobalApplication.gps = GPS(
                                    gpsLastKnown.latitude.toString(),
                                    gpsLastKnown.longitude.toString(),
                                    LocationCompat.getElapsedRealtimeNanos(gpsLastKnown)
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                    Geocoder(this@MainActivity).getFromLocation(gpsLastKnown.latitude, gpsLastKnown.longitude, 1) { list ->
                                        GlobalApplication.address = list.firstOrNull()
                                    }
                            }
                        }
                        /*val gpsLastKnown = it.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (gpsLastKnown != null && GlobalApplication.gps?.time ?: -1L < LocationCompat.getElapsedRealtimeNanos(gpsLastKnown)) GlobalApplication.gps =
                        GPS(
                            gpsLastKnown.latitude.toString(),
                            gpsLastKnown.longitude.toString(),
                            LocationCompat.getElapsedRealtimeNanos(gpsLastKnown)
                        )*/
                        LocationManagerCompat.requestLocationUpdates(it, LocationManager.GPS_PROVIDER, LocationRequestCompat.Builder(5000L).build(), Dispatchers.IO.asExecutor(), listener)
                        //it.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 0f, listener)
                    } else if (it.getProviders(true).contains(LocationManager.NETWORK_PROVIDER)) {
                        LocationManagerCompat.getCurrentLocation(it, LocationManager.NETWORK_PROVIDER, null, Dispatchers.IO.asExecutor()) { networkLastKnown ->
                            if (networkLastKnown != null && (GlobalApplication.gps?.time ?: -1L) < LocationCompat.getElapsedRealtimeNanos(networkLastKnown)) {
                                GlobalApplication.gps = GPS(
                                    networkLastKnown.latitude.toString(),
                                    networkLastKnown.longitude.toString(),
                                    LocationCompat.getElapsedRealtimeNanos(networkLastKnown)
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                    Geocoder(this@MainActivity).getFromLocation(networkLastKnown.latitude, networkLastKnown.longitude, 1) { list ->
                                        GlobalApplication.address = list.firstOrNull()
                                    }
                            }
                        }
                        /*val networkLastKnown = it.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (networkLastKnown != null && GlobalApplication.gps?.time ?: -1L < LocationCompat.getElapsedRealtimeNanos(networkLastKnown)) GlobalApplication.gps =
                        GPS(
                            networkLastKnown.latitude.toString(),
                            networkLastKnown.longitude.toString(),
                            LocationCompat.getElapsedRealtimeNanos(networkLastKnown)
                        )*/
                        LocationManagerCompat.requestLocationUpdates(it, LocationManager.NETWORK_PROVIDER, LocationRequestCompat.Builder(5000L).build(), Dispatchers.IO.asExecutor(), listener)
                        //it.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, listener)
                    } else Unit
                }
            }
            lifecycleScope.launch(extensionCreationContext) {
                delay(5.seconds)
                text = "正在抓取……"
                createExtensionModel().toJson()
                    .saveFileToDownload("model-${Instant.now().toEpochMilli()}.txt", contentResolver)
                text = "抓取完成！信息已经保存在Download文件夹，程序将在五秒钟之内关闭"
                delay(5.seconds)
                ActivityCompat.finishAffinity(this@MainActivity)
                isStartingFetch = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStop() {
        super.onStop()
        if (isStartingFetch) {
            getSystemService<LocationManager>()?.let { LocationManagerCompat.removeUpdates(it, listener) }
            //getSystemService<LocationManager>()?.removeUpdates(listener)
            GoogleApiAvailability.getInstance().checkApiAvailability(locationServices)
                .addOnSuccessListener { locationServices.removeLocationUpdates(callback) }
            getSystemService<ConnectivityManager>()?.unregisterNetworkCallback(wifiCallback)
        }
        if (isFinishing) onFinish()
    }

    override fun onDestroy() {
        super.onDestroy()
        onFinish()
    }

    private fun onFinish() {
        GlobalApplication.lastLoginTime = Instant.now().toEpochMilli()
    }
}

@Composable
fun Greeting(name: String) {
    Column(verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = name,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center)
    }
}
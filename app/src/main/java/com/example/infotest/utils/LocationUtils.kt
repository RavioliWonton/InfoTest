@file:Suppress("unused")

package com.example.infotest.utils

import android.Manifest
import android.app.Activity
import android.location.Address
import android.location.Geocoder
import android.location.GnssMeasurementsEvent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.collection.objectListOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.PermissionChecker
import androidx.core.content.getSystemService
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.infotest.GlobalApplication
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.tencent.map.geolocation.TencentLocation
import com.tencent.map.geolocation.TencentLocationListener
import com.tencent.map.geolocation.TencentLocationManager
import com.tencent.map.geolocation.TencentLocationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.Locale

@delegate:RequiresApi(Build.VERSION_CODES.N)
private val gnssCallback by lazy {
    object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent?) {
            super.onGnssMeasurementsReceived(eventArgs)
            eventArgs?.clock?.timeNanos?.let { GlobalApplication.gnssTimeNano = it }
        }
    }
}

fun ComponentActivity.getLocationAsync(isAccuracy: Boolean = false, isUsingGoogleService: Boolean = true, isUsingTencent: Boolean = true, callback: (Location?) -> Unit) {
    if (PermissionChecker.checkSelfPermission(this, if (isAccuracy) Manifest.permission.ACCESS_FINE_LOCATION
        else Manifest.permission.ACCESS_COARSE_LOCATION) != PermissionChecker.PERMISSION_GRANTED) return
    lifecycle.addObserver(LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START) setup(isAccuracy, isUsingGoogleService, isUsingTencent, callback)
        if (event == Lifecycle.Event.ON_STOP) unSetUp(callback)
    })
}

@Composable
fun GetLocationAsyncComposable(isAccuracy: Boolean = false, isUsingGoogleService: Boolean = true, isUsingTencent: Boolean = true, callback: (Location?) -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    if (PermissionChecker.checkSelfPermission(context, if (isAccuracy) Manifest.permission.ACCESS_FINE_LOCATION
        else Manifest.permission.ACCESS_COARSE_LOCATION) != PermissionChecker.PERMISSION_GRANTED) return
    val lifecycleOwner = rememberUpdatedState(newValue = LocalLifecycleOwner.current)
    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) activity?.setup(isAccuracy, isUsingGoogleService, isUsingTencent, callback)
            if (event == Lifecycle.Event.ON_STOP) activity?.unSetUp(callback)
        }
        lifecycleOwner.value.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.value.lifecycle.removeObserver(observer)
        }
    }
}

@RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION], conditional = false)
private fun Activity.setup(isAccuracy: Boolean = false, isUsingGoogleService: Boolean = true, isUsingTencent: Boolean = true, callback: (Location?) -> Unit) {
    if (isUsingGoogleService) {
        val locationServices = LocationServices.getFusedLocationProviderClient(this)
        GoogleApiAvailability.getInstance().checkApiAvailability(locationServices).addOnSuccessListener {
            val request = LocationRequest.Builder(5000L).setWaitForAccurateLocation(isAccuracy).build()
            LocationServices.getSettingsClient(this).checkLocationSettings(
                LocationSettingsRequest.Builder().setNeedBle(atLeastM).addLocationRequest(request).build()
            ).addOnCompleteListener { result ->
                try {
                    val response = result.getResult(ApiException::class.java).locationSettingsStates
                    if (response?.isLocationUsable == false) {
                        startActionCompat(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    } else {
                        if (response?.isLocationPresent == true || response?.isBlePresent == true || locationServices.locationAvailability.getResult(ApiException::class.java).isLocationAvailable)
                            locationServices.lastLocation.addOnSuccessListener { callback.invoke(it) }
                        locationServices.requestLocationUpdates(request, Dispatchers.IO.asExecutor(), callback)
                    }
                } catch (e: ApiException) {
                    if (e.statusCode == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) emitException {
                        val resolver = e as ResolvableApiException
                        resolver.startResolutionForResult(this, 114514)
                    }
                }
            }
        }
    }
    if (isUsingTencent) {
        TencentLocationManager.getInstance(applicationContext).applyEmitException {
            setDeviceID(applicationContext, GlobalApplication.gaid)
            coordinateType = TencentLocationManager.COORDINATE_TYPE_WGS84
        }.requestLocationUpdates(TencentLocationRequest.create()
            .setFirstLocationNeedAddress(true)
            //.setGnssSource(TencentLocationRequest.GNSS_SOURCE_BEIDOU_FIRST)
            .setEnableAntiMock(true).setIndoorLocationMode(true).setRequestLevel(
                TencentLocationRequest.REQUEST_LEVEL_NAME), object : TencentLocationListener {
            override fun onLocationChanged(location: TencentLocation?, error: Int, reason: String?) {
                if (error == TencentLocation.ERROR_OK && location != null && location.provider != TencentLocation.FAKE) {
                    callback.invoke(Location("ten_${location.provider}").apply {
                        latitude = location.latitude
                        longitude = location.longitude
                        altitude = location.altitude
                        time = location.time
                        elapsedRealtimeNanos = location.elapsedRealtime * 1000
                        if (location.provider == TencentLocation.GPS_PROVIDER) {
                            speed = location.speed
                            bearing = location.bearing
                            accuracy = location.accuracy
                            LocationCompat.setMock(this, location.isMockGps == 1)
                            LocationCompat.setVerticalAccuracyMeters(this, location.accuracy)
                        }
                    })
                    if (location.address.isNotBlank())
                        GlobalApplication.address = Address(Locale.CHINA).apply {
                            latitude = location.latitude
                            longitude = location.longitude
                            objectListOf(location.nation, location.province,
                                location.city, location.district,
                                location.street.takeIf { it.isNotBlank() } ?: location.town,
                                location.village, location.address).forEachReversed { if (it.isNotBlank())
                                setAddressLine(maxAddressLineIndex + 1, it) }
                            adminArea = location.province
                            subAdminArea = location.city
                            // location.getNationCode return 0 without key,
                            // but address won't get valid data outside China,
                            // so just use China's nation code
                            countryCode = "86"
                            countryName = location.nation
                        }
                }
            }

            override fun onStatusUpdate(p0: String?, p1: Int, p2: String?) = Unit
        })
    }
    getSystemService<LocationManager>()?.let {
        if (!LocationManagerCompat.isLocationEnabled(it)) {
            startActionCompat(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        } else {
            if (it.getProviders(true).contains(LocationManager.GPS_PROVIDER)) {
                LocationManagerCompat.getCurrentLocation(it, LocationManager.GPS_PROVIDER, null as CancellationSignal?, Dispatchers.IO.asExecutor(), callback)
                LocationManagerCompat.requestLocationUpdates(it, LocationManager.GPS_PROVIDER, LocationRequestCompat.Builder(5000L)
                    .setQuality(if (isAccuracy) LocationRequestCompat.QUALITY_HIGH_ACCURACY else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY).build(), Dispatchers.IO.asExecutor(), callback)
                if (atLeastN) LocationManagerCompat.registerGnssMeasurementsCallback(it, Dispatchers.IO.asExecutor(), gnssCallback)
            } else if (it.getProviders(true).contains(LocationManager.NETWORK_PROVIDER)) {
                LocationManagerCompat.getCurrentLocation(it, LocationManager.NETWORK_PROVIDER, null as CancellationSignal?, Dispatchers.IO.asExecutor(), callback)
                LocationManagerCompat.requestLocationUpdates(it, LocationManager.NETWORK_PROVIDER, LocationRequestCompat.Builder(5000L)
                    .setQuality(if (isAccuracy) LocationRequestCompat.QUALITY_HIGH_ACCURACY else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY).build(), Dispatchers.IO.asExecutor(), callback)
            } else Unit
        }
    }
}

@RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION], conditional = false)
private fun Activity.unSetUp(callback: (Location?) -> Unit) {
    emitException {
        getSystemService<LocationManager>()?.let {
            LocationManagerCompat.removeUpdates(it, callback)
            if (atLeastN) LocationManagerCompat.unregisterGnssMeasurementsCallback(it, gnssCallback)
        }
    }
    emitException {
        val locationServices = LocationServices.getFusedLocationProviderClient(this)
        GoogleApiAvailability.getInstance().checkApiAvailability(locationServices)
            .addOnSuccessListener { locationServices.removeLocationUpdates(callback) }
    }
    emitException {
        TencentLocationManager.getInstance(applicationContext).removeUpdates(null)
    }
}

fun Location.couldFetchAddress() =
    { Geocoder.isPresent() && provider?.startsWith("ten_")?.not() == true }.catchFalse()
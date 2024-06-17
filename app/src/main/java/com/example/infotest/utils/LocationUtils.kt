@file:Suppress("unused")

package com.example.infotest.utils

import android.Manifest
import android.app.Activity
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.PermissionChecker
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

fun ComponentActivity.getLocationAsync(isAccuracy: Boolean = false, isUsingGoogleService: Boolean = true, callback: (Location) -> Unit) {
    if (PermissionChecker.checkSelfPermission(this, if (isAccuracy) Manifest.permission.ACCESS_FINE_LOCATION
        else Manifest.permission.ACCESS_COARSE_LOCATION) != PermissionChecker.PERMISSION_GRANTED) return
    lifecycle.addObserver(LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START) setup(isAccuracy, isUsingGoogleService, callback)
        if (event == Lifecycle.Event.ON_STOP) unSetUp(isUsingGoogleService, callback)
    })
}

@Composable
fun GetLocationAsyncComposable(isAccuracy: Boolean = false, isUsingGoogleService: Boolean = true, callback: (Location) -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    if (PermissionChecker.checkSelfPermission(context, if (isAccuracy) Manifest.permission.ACCESS_FINE_LOCATION
        else Manifest.permission.ACCESS_COARSE_LOCATION) != PermissionChecker.PERMISSION_GRANTED) return
    val lifecycleOwner = rememberUpdatedState(newValue = LocalLifecycleOwner.current)
    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) activity?.setup(isAccuracy, isUsingGoogleService, callback)
            if (event == Lifecycle.Event.ON_STOP) activity?.unSetUp(isUsingGoogleService, callback)
        }
        lifecycleOwner.value.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.value.lifecycle.removeObserver(observer)
        }
    }
}

@RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION], conditional = false)
private fun Activity.setup(isAccuracy: Boolean = false, isUsingGoogleService: Boolean = true, callback: (Location) -> Unit) {
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
                        if (response?.isLocationPresent == true || response?.isBlePresent == true ||
                            locationServices.locationAvailability.getResult(ApiException::class.java).isLocationAvailable) {
                            locationServices.lastLocation.addOnSuccessListener { callback.invoke(it) }
                        }
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
    getSystemService<LocationManager>()?.let {
        if (!LocationManagerCompat.isLocationEnabled(it)) {
            startActionCompat(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        } else {
            if (it.getProviders(true).contains(LocationManager.GPS_PROVIDER)) {
                LocationManagerCompat.getCurrentLocation(it, LocationManager.GPS_PROVIDER, null as CancellationSignal?, Dispatchers.IO.asExecutor(), callback)
                LocationManagerCompat.requestLocationUpdates(it, LocationManager.GPS_PROVIDER, LocationRequestCompat.Builder(5000L)
                    .setQuality(if (isAccuracy) LocationRequestCompat.QUALITY_HIGH_ACCURACY else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY).build(), Dispatchers.IO.asExecutor(), callback)
            } else if (it.getProviders(true).contains(LocationManager.NETWORK_PROVIDER)) {
                LocationManagerCompat.getCurrentLocation(it, LocationManager.NETWORK_PROVIDER, null as CancellationSignal?, Dispatchers.IO.asExecutor(), callback)
                LocationManagerCompat.requestLocationUpdates(it, LocationManager.NETWORK_PROVIDER, LocationRequestCompat.Builder(5000L)
                    .setQuality(if (isAccuracy) LocationRequestCompat.QUALITY_HIGH_ACCURACY else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY).build(), Dispatchers.IO.asExecutor(), callback)
            } else Unit
        }
    }
}

@RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION], conditional = false)
private fun Activity.unSetUp(isUsingGoogleService: Boolean = true, callback: (Location) -> Unit) {
    getSystemService<LocationManager>()?.let { LocationManagerCompat.removeUpdates(it, callback) }
    if (isUsingGoogleService) {
        val locationServices = LocationServices.getFusedLocationProviderClient(this)
        GoogleApiAvailability.getInstance().checkApiAvailability(locationServices)
            .addOnSuccessListener { locationServices.removeLocationUpdates(callback) }
    }
}
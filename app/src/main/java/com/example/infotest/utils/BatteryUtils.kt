package com.example.infotest.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.example.infotest.BatteryStatus
import splitties.init.appCtx

fun Context.getBatteryStatus(): BatteryStatus {
    val manager = getSystemService<BatteryManager>()
    val chargeStatus = ContextCompat.registerReceiver(this, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    val batteryCapacity = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it != Int.MIN_VALUE }?.div(1000f)?.toDouble() ?: getBatteryCapacityByHook()
    return BatteryStatus(
        batteryLevel = (manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?: chargeStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0))
            ?.let { (batteryCapacity * it / 100).toString() }.orEmpty(),
        batteryMax = batteryCapacity.toString(),
        batteryPercent = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1,
        isAcCharge = (chargeStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                == BatteryManager.BATTERY_PLUGGED_AC).toInt(),
        isUsbCharge = (chargeStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                == BatteryManager.BATTERY_PLUGGED_USB).toInt(),
        isCharging = ((atLeastM && manager?.isCharging == true) ||
                chargeStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) == BatteryManager.BATTERY_STATUS_CHARGING).toInt()
    )
}

@SuppressLint("PrivateApi")
private fun getBatteryCapacityByHook() = {
    getClassOrNull("com.android.internal.os.PowerProfile")?.let {
        it.getAccessibleMethod("getBatteryCapacity")?.invoke(it.getAccessibleConstructor(true,
            Context::class.java)?.newInstance(appCtx), emptyArray<Any>()) as Double
    }
}.catchZero()
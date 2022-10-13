package com.example.infotest

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Address
import android.net.LinkProperties
import android.net.wifi.WifiInfo
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.getkeepsafe.relinker.ReLinker
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import splitties.init.appCtx

class GlobalApplication: Application() {

    @DelicateCoroutinesApi
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(applicationContext, filesDir.absolutePath + "/mmkv") {
            ReLinker.recursively().loadLibrary(applicationContext, it)
        }
        if (gaid.isNullOrBlank() && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {
            GlobalScope.launch(Dispatchers.IO) {
                val id = AdvertisingIdClient
                    .getAdvertisingIdInfo(applicationContext).id
                gaid = id
            }
        }
    }

    companion object {
        private val mmkv by lazy { MMKV.defaultMMKV(MMKV.MULTI_PROCESS_MODE, null) }
        val appVersion by lazy { PackageInfoCompat.getLongVersionCode(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                appCtx.packageManager.getPackageInfo(appCtx.packageName, PackageManager.PackageInfoFlags
                    .of(PackageManager.GET_CONFIGURATIONS.toLong()))
            else appCtx.packageManager.getPackageInfo(
                appCtx.packageName, PackageManager.GET_CONFIGURATIONS)) }
        val appVersionName: String by lazy { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            appCtx.packageManager.getPackageInfo(appCtx.packageName,
                PackageManager.PackageInfoFlags.of(0L)).versionName
            else appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionName }
        val isDebug by lazy { appCtx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
        var lastLoginTime: Long
            get() = mmkv.decodeLong("lastLogin", 0)
            set(value) { mmkv.encode("lastLogin", value) }
        var gaid: String?
            get() = mmkv.decodeString(Constants.gaidTag, "")
            set(value) { mmkv.encode(Constants.gaidTag, value) }
        var gps: GPS?
            get() = mmkv.decodeParcelable("gps", GPS::class.java)
            set(value) { mmkv.encode("gps", value) }
        var dbm: Int
            get() = mmkv.decodeInt("dbm", -1)
            set(value) { mmkv.encode("dbm", value) }
        var currentWifiCapabilities: WifiInfo?
            get() = mmkv.decodeParcelable("wifi", WifiInfo::class.java)
            set(value) { mmkv.encode("wifi", value) }
        var currentWifiLinkProperties: LinkProperties?
            get() = mmkv.decodeParcelable("wifi", LinkProperties::class.java)
            set(value) { mmkv.encode("wifi", value) }
        var address: Address?
            get() = mmkv.decodeParcelable("address", Address::class.java)
            set(value) { mmkv.encode("address", value) }
    }
}
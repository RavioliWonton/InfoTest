package com.example.infotest

import android.app.Application
import android.app.GrammaticalInflectionManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Address
import android.net.LinkProperties
import android.net.wifi.WifiInfo
import android.os.Build
import androidx.core.content.getSystemService
import androidx.core.content.pm.PackageInfoCompat
import com.getkeepsafe.relinker.ReLinker
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.appset.AppSet
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.security.ProviderInstaller
import com.instacart.truetime.time.TrueTimeImpl
import com.instacart.truetime.time.TrueTimeParameters
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.conscrypt.Conscrypt
import splitties.init.appCtx
import java.security.Security

class GlobalApplication: Application() {

    @DelicateCoroutinesApi
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(applicationContext, filesDir.absolutePath + Constants.mmkvDefaultRoute) {
            ReLinker.recursively().loadLibrary(applicationContext, it)
        }
        // Light is enough for checking for gms availability according to so/57902978
        if (GoogleApiAvailabilityLight.getInstance()
                .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {
            ProviderInstaller.installIfNeededAsync(this, object : ProviderInstaller.ProviderInstallListener {
                override fun onProviderInstallFailed(errorCode: Int, recoveryIntent: Intent?) {
                    GoogleApiAvailability.getInstance().showErrorNotification(this@GlobalApplication, errorCode)
                    if (Conscrypt.isAvailable()) Security.insertProviderAt(Conscrypt.newProviderBuilder().provideTrustManager(true).build(), 0)
                }

                override fun onProviderInstalled() = Unit
            })
            if (gaid.isNullOrBlank()) GlobalScope.launch(noExceptionContext) {
                gaid = AdvertisingIdClient.getAdvertisingIdInfo(applicationContext).id
            }
            // Always change, not usable
            if (appSetId.isNullOrBlank()) GlobalScope.launch(noExceptionContext) {
                appSetId = AppSet.getClient(applicationContext).appSetIdInfo.await().id
            }
        } else if (Conscrypt.isAvailable())
            Security.insertProviderAt(Conscrypt.newProviderBuilder().provideTrustManager(true).build(), 0)
        if (isNetworkAvailable()) trueTime.sync()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            getSystemService<GrammaticalInflectionManager>()?.setRequestedApplicationGrammaticalGender(Configuration.GRAMMATICAL_GENDER_NEUTRAL)
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
            get() = mmkv.decodeLong(Constants.lastLoginTag)
            set(value) { mmkv.encode(Constants.lastLoginTag, value) }
        var gaid: String?
            get() = mmkv.decodeString(Constants.gaidTag)
            set(value) { mmkv.encode(Constants.gaidTag, value) }
        var appSetId: String?
            get() = mmkv.decodeString(Constants.appSetIdTag)
            set(value) { mmkv.encode(Constants.appSetIdTag, value) }
        var gps: GPS?
            get() = mmkv.decodeParcelable(Constants.gpsTag, GPS::class.java)
            set(value) { mmkv.encode(Constants.gpsTag, value) }
        var dbm: Int
            get() = mmkv.decodeInt(Constants.dbmTag, -1)
            set(value) { mmkv.encode(Constants.dbmTag, value) }
        var currentWifiCapabilities: WifiInfo?
            get() = mmkv.decodeParcelable(Constants.wifiCapabilitiesTag, WifiInfo::class.java)
            set(value) { mmkv.encode(Constants.wifiCapabilitiesTag, value) }
        var currentWifiLinkProperties: LinkProperties?
            get() = mmkv.decodeParcelable(Constants.wifiPropertiesTag, LinkProperties::class.java)
            set(value) { mmkv.encode(Constants.wifiPropertiesTag, value) }
        var address: Address?
            get() = mmkv.decodeParcelable(Constants.addressTag, Address::class.java)
            set(value) { mmkv.encode(Constants.addressTag, value) }
        val trueTime by lazy { TrueTimeImpl(
            params = TrueTimeParameters.Builder()
                .returnSafelyWhenUninitialized(true)
                .ntpHostPool(arrayListOf("ntp1.nim.ac.cn", "ntp.ntsc.ac.cn",
                    "ntp.sjtu.edu.cn", "time-e-g.nist.gov"))
                .buildParams())
        }
    }
}
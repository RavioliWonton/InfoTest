package com.example.infotest

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Address
import android.net.LinkProperties
import android.net.wifi.WifiInfo
import android.os.StrictMode
import androidx.core.app.GrammaticalInflectionManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.privacysandbox.ads.adservices.adid.AdIdManager
import androidx.privacysandbox.ads.adservices.appsetid.AppSetIdManager
import com.example.infotest.utils.Constants
import com.example.infotest.utils.getGAIDFallback
import com.example.infotest.utils.getPackageInfoCompat
import com.example.infotest.utils.isGoogleServiceAvailable
import com.example.infotest.utils.isNetworkAvailable
import com.example.infotest.utils.noExceptionContext
import com.getkeepsafe.relinker.ReLinker
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.appset.AppSet
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.net.CronetProviderInstaller
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.installations.FirebaseInstallations
import com.instacart.truetime.time.TrueTimeImpl
import com.instacart.truetime.time.TrueTimeParameters
import com.tencent.map.geolocation.TencentLocationManagerOptions
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CancellationException
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
        if (isDebug) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().permitDiskReads().permitDiskWrites().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
        super.onCreate()
        MMKV.initialize(applicationContext, ContextCompat.getNoBackupFilesDir(this)?.absolutePath + Constants.mmkvDefaultRoute) {
            ReLinker.recursively().loadLibrary(applicationContext, it)
        }
        TencentLocationManagerOptions.setDebuggable(isDebug)
        if (isGoogleServiceAvailable()) {
            ProviderInstaller.installIfNeededAsync(this, object : ProviderInstaller.ProviderInstallListener {
                override fun onProviderInstallFailed(errorCode: Int, recoveryIntent: Intent?) {
                    if (GoogleApiAvailabilityLight.getInstance().isUserResolvableError(errorCode))
                        GoogleApiAvailability.getInstance().showErrorNotification(this@GlobalApplication, errorCode)
                    if (Conscrypt.isAvailable()) Security.insertProviderAt(Conscrypt.newProviderBuilder().provideTrustManager(true).build(), 0)
                }

                override fun onProviderInstalled() = Unit
            })
            if (gaid.isNullOrBlank()) GlobalScope.launch(noExceptionContext) {
                gaid = AdIdManager.obtain(applicationContext)?.getAdId()?.adId ?: AdvertisingIdClient.getAdvertisingIdInfo(applicationContext).id
            }.invokeOnCompletion {
                if (it !is CancellationException && gaid.isNullOrBlank()) GlobalScope.launch(noExceptionContext) {
                    gaid = FirebaseInstallations.getInstance().id.await()
                }.invokeOnCompletion { if (it !is CancellationException && gaid.isNullOrBlank()) getGAIDFallback() }
            }
            // Always change, not usable
            if (appSetId.isNullOrBlank()) GlobalScope.launch(noExceptionContext) {
                appSetId = AppSetIdManager.obtain(applicationContext)?.getAppSetId()?.id ?: AppSet.getClient(applicationContext).appSetIdInfo.await().id
            }
            CronetProviderInstaller.installProvider(this)
        } else {
            getGAIDFallback()
            if (Conscrypt.isAvailable()) Security.insertProviderAt(Conscrypt.newProviderBuilder().provideTrustManager(true).build(), 0)
        }
        if (isNetworkAvailable()) trueTime.sync()
        GrammaticalInflectionManagerCompat.setRequestedApplicationGrammaticalGender(this, GrammaticalInflectionManagerCompat.GRAMMATICAL_GENDER_NEUTRAL)
    }

    companion object {
        private val mmkv by lazy { MMKV.defaultMMKV(MMKV.MULTI_PROCESS_MODE, null) }
        val appVersion by lazy { appCtx.packageManager.getPackageInfoCompat(appCtx.packageName,
            PackageManager.GET_CONFIGURATIONS)?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
        }
        val appVersionName: String by lazy { appCtx.packageManager.getPackageInfoCompat(appCtx.packageName)?.versionName.orEmpty() }
        val isDebug by lazy { appCtx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
        var lastLoginTime: Long
            get() = mmkv.decodeLong(Constants.LASTLOGINTAG)
            set(value) { mmkv.encode(Constants.LASTLOGINTAG, value) }
        var gaid: String?
            get() = mmkv.decodeString(Constants.GAIDTAG)
            set(value) { mmkv.encode(Constants.GAIDTAG, value) }
        var appSetId: String?
            get() = mmkv.decodeString(Constants.APPSETIDTAG)
            set(value) { mmkv.encode(Constants.APPSETIDTAG, value) }
        var gps: GPS?
            get() = mmkv.decodeParcelable(Constants.GPSTAG, GPS::class.java)
            set(value) { mmkv.encode(Constants.GPSTAG, value) }
        var dbm: Int
            get() = mmkv.decodeInt(Constants.DBMTAG, -1)
            set(value) { mmkv.encode(Constants.DBMTAG, value) }
        var currentWifiCapabilities: WifiInfo?
            get() = mmkv.decodeParcelable(Constants.WIFICAPABILITIESTAG, WifiInfo::class.java)
            set(value) { mmkv.encode(Constants.WIFICAPABILITIESTAG, value) }
        var currentWifiLinkProperties: LinkProperties?
            get() = mmkv.decodeParcelable(Constants.WIFIPROPERTIESTAG, LinkProperties::class.java)
            set(value) { mmkv.encode(Constants.WIFIPROPERTIESTAG, value) }
        var address: Address?
            get() = mmkv.decodeParcelable(Constants.ADDRESSTAG, Address::class.java)
            set(value) { mmkv.encode(Constants.ADDRESSTAG, value) }
        val trueTime by lazy { TrueTimeImpl(
            params = TrueTimeParameters.Builder()
                .returnSafelyWhenUninitialized(true)
                .ntpHostPool(arrayListOf("ntp1.nim.ac.cn", "ntp.ntsc.ac.cn",
                    "ntp.sjtu.edu.cn", "time-e-g.nist.gov"))
                .buildParams())
        }
    }
}
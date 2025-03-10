@file:Suppress("unused")

package com.example.infotest.utils

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.media.MediaCrypto
import android.media.MediaDrm
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.collection.objectListOf
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.core.util.ObjectsCompat
import com.example.infotest.GlobalApplication
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(DelicateCoroutinesApi::class)
fun Context.getGAIDFallback() {
    if (GlobalApplication.gaid.isNullOrBlank())
        GlobalScope.launch(noExceptionContext) { GlobalApplication.gaid = getOAID() }.invokeOnCompletion { cancel2 ->
            if (cancel2 !is CancellationException && GlobalApplication.gaid.isNullOrBlank()) GlobalScope.launch(noExceptionContext) {
                // http://www.cnadid.cn/guide.html
                GlobalApplication.gaid = Settings.System.getString(contentResolver, "ZHVzY2Lk").ifBlank {
                    // https://github.com/shuzilm-open-source/Get_Oaid_CNAdid/blob/master/CNAdid_source/CNAdidHelper.java#L48
                    getSharedPreferences("${packageName}_dna", Context.MODE_PRIVATE).getString("ZHVzY2Lk", null) ?:
                    (if(atLeastQ) Path(filesDir.path, "ZHVzY2Lk") else Path(Environment.getExternalStorageDirectory().path, "Android/ZHVzY2Lk"))
                        .takeIf { it.exists() }?.bufferedReader()?.use { it.readLine() }
                }
            }.invokeOnCompletion { cancel3 -> if (cancel3 !is CancellationException && GlobalApplication.gaid.isNullOrBlank())
                emitException { GlobalApplication.gaid = Settings.Secure.getString(contentResolver, "advertising_id") }
            }
        }
}

@OptIn(ExperimentalUuidApi::class)
@Suppress("HardwareIds", "DEPRECATION")
fun Context.getUniquePseudoId(): String = {
    val szDevIdShort = "35" + Build.BOARD.length % 10 + Build.BRAND.length % 10 + Build.CPU_ABI.length % 10 +
            Build.DEVICE.length % 10 + Build.DISPLAY.length % 10 + Build.HOST.length % 10 +
            Build.ID.length % 10 + Build.MANUFACTURER.length % 10 + Build.MODEL.length % 10 +
            Build.PRODUCT.length % 10 + Build.TAGS.length % 10 + Build.TYPE.length % 10 +
            Build.USER.length % 10
    val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    val id = if (Build.SERIAL != Build.UNKNOWN) androidId + Build.SERIAL else androidId
    val serial: String = {
        // api >= 9 reflect to get serial
        Build::class.java.getField("SERIAL").get(null)?.toString()
    // must init serial, use itself
    }.catchReturn("serial")

    Uuid.fromLongs(ObjectsCompat.hashCode(szDevIdShort).toLong(), ObjectsCompat.hashCode(serial).toLong()).toString() + id
}.catchEmpty()

@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class, ExperimentalUuidApi::class)
fun Context.getUniqueMediaDrmID(): String = {
    // https://github.com/androidx/media/blob/release/libraries/common/src/main/java/androidx/media3/common/C.java#L1086
    Uuid.fromLongs(-0x121074568629b532L, -0x5c37d8232ae2de13L).takeIf { MediaCrypto.isCryptoSchemeSupported(it.toJavaUuid()) }?.let { uuid ->
        MediaDrm(uuid.toJavaUuid()).use {
            val md = getMessageDigestInstanceOrNull("SHA-256")
            md?.update(it.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID))
            md?.digest()?.asUByteArray()?.joinToString("") { ubyte -> ubyte.toHexString() }.orEmpty()
        }
    }
}.catchEmpty()

// Light is enough for checking for gms availability according to so/57902978
fun Context.isGoogleServiceAvailable() = GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS

@OptIn(ExperimentalStdlibApi::class)
@RequiresPermission("com.google.android.providers.gsf.permission.READ_GSERVICES")
fun Context.getGSFId(): String = {
    if (isGoogleServiceAvailable())
        contentResolver.queryAll("content://com.google.android.gsf.gservices".toUri(), projection = arrayOf("android_id")).use {
            it?.getSingleLong("android_id")?.toULong()?.toHexString().orEmpty()
        }
    else ""
}.catchEmpty()

suspend fun Context.getOAID(): String? = when {
    Build.MANUFACTURER.equals("ASUS", true) -> withContext(noExceptionContext) {
        packageManager.getPackageInfoCompat("com.asus.msa.SupplementaryDID")?.let {
            processIBinderFromOAIDService(Intent("com.asus.msa.action.ACCESS_DID").setClassName("com.asus.msa.SupplementaryDID", "com.asus.msa.SupplementaryDID.SupplementaryDIDService")) {
                val iter = OAIDInterface.ASUSID(it)
                iter.getID().takeIf { iter.isSupport }
            }
        }
    }
    Build.MANUFACTURER.equals("COOLPAD", true) -> withContext(noExceptionContext) {
        packageManager.getPackageInfoCompat("com.coolpad.deviceidsupport")?.let {
            processIBinderFromOAIDService(Intent().setClassName("com.coolpad.deviceidsupport", "com.coolpad.deviceidsupport.DeviceIdService")) {
                OAIDInterface.COOLID.newInstance(it).getID(1, packageName)
            }
        }
    }
    getSystemProperty("ro.odm.manufacturer").equals("PRIZE", true) -> withContext(noExceptionContext) {
        getSystemService<KeyguardManager>()?.let { (KeyguardManager::class.java.getDeclaredAccessibleMethod("obtainOaid")?.invoke(it, emptyArray<Any>()) as String?)
            .takeIf { KeyguardManager::class.java.getDeclaredAccessibleMethod("isSupported")?.invoke(it, emptyArray<Any>()) == true } }
    }
    getSystemProperty("ro.build.uiversion")?.isNotBlank() == true -> withContext(noExceptionContext) {
        packageManager.getPackageInfoCompat("com.qiku.id")?.let {
            processIBinderFromOAIDService(Intent("qiku.service.action.id").setPackage(it.packageName)) {
                val iter = OAIDInterface.QikuID(it)
                iter.getID().takeIf { iter.isSupport }
            }
        }?.takeIf { it.isNotBlank() } ?: getSystemProperty("ro.build.uiversion").takeIf { it?.contains("360UI", true) == true }?.let {
            getClassOrNull("android.os.ServiceManager")?.let { clazz ->
                (clazz.getDeclaredAccessibleMethod("getService", String::class.java)?.invoke(null, "qikuid") as IBinder?).let { ibinder ->
                    OAIDInterface.QikuID(ibinder, true).let { iter -> iter.getID().takeIf { iter.isSupport } }
                }
            }
        }
    }
    objectListOf("LENOVO", "MOTOLORA").any { Build.MANUFACTURER.equals(it,true) } -> withContext(noExceptionContext) {
        processIBinderFromOAIDService(Intent().setClassName("com.zui.deviceidservice", "com.zui.deviceidservice.DeviceidService")) {
            val iter = OAIDInterface.LENID(it)
            iter.getID(1, packageName).takeIf { iter.isSupport }
        }
    }
    Build.MANUFACTURER.equals("MEIZU", true) || Build.DISPLAY.contains("FLYME", true) -> withContext(noExceptionContext) {
        packageManager.resolveContentProvider("com.meizu.flyme.openidsdk", 0)?.let { info ->
            contentResolver.queryAll(Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(info.authority).build()).use {
                it.getSingleString("value")
            }
        }
    }
    Build.MANUFACTURER.equals("NUBIA", true) && atLeastQ -> withContext(noExceptionContext) {
        contentResolver.acquireContentProviderClient("content://cn.nubia.identity/identity".toUri())?.use { client ->
            client.call("getOAID", null, null)?.takeIf { it.getInt("code", -1) == 0 }?.getString("id")
        }
    }
    objectListOf("ONEPLUS", "OPPO").any { Build.MANUFACTURER.equals(it, true) } || getSystemProperty("ro.build.version.opporom")?.isNotBlank() == true -> withContext(noExceptionContext) {
        packageManager.getPackageInfoCompat("com.heytap.openid")?.let { info ->
            PackageInfoCompat.getLongVersionCode(info).takeIf { it >= -1 }?.let {
                processIBinderFromOAIDService(Intent("action.com.heytap.openid.OPEN_ID_SERVICE").setClassName("com.heytap.openid", "com.heytap.openid.IdentifyService")) { binder ->
                    delay(3.seconds)
                    OAIDInterface.OPPOID.newInstance(binder).getID(3, packageName, packageManager.getPackageInfoCompat(packageName)?.getPrimarySignatureDigest().orEmpty(), "OUID")
                }
            }
        } ?: packageManager.getPackageInfoCompat("com.coloros.mcs")?.let {
            processIBinderFromOAIDService(Intent("action.com.oplus.stdid.ID_SERVICE").setClassName("com.coloros.mcs", "com.oplus.stdid.IdentifyService")) { binder ->
                delay(3.seconds)
                OAIDInterface.OPPOID.newInstance(binder, true).getID(3, packageName, packageManager.getPackageInfoCompat(packageName)?.getPrimarySignatureDigest().orEmpty(), "OUID")
            }
        }
    }
    Build.MANUFACTURER.equals("SAMSUNG", true) -> withContext(noExceptionContext) {
        packageManager.getPackageInfoCompat("com.samsung.android.deviceidservice")?.let {
            processIBinderFromOAIDService(Intent().setClassName("com.samsung.android.deviceidservice", "com.samsung.android.deviceidservice.DeviceIdService")) {
                OAIDInterface.SAMID(it).getID()
            }
        }
    }
    Build.MANUFACTURER.equals("VIVO", true) || getSystemProperty("ro.vivo.os.version")?.isNotBlank() == true -> withContext(noExceptionContext) {
        contentResolver.queryAll("content://com.vivo.vms.IdProvider/IdentifierId/OAID".toUri()).use { it.getSingleString("value") }
    }
    objectListOf("XIAOMI", "BLACKSHARK").any { Build.MANUFACTURER.equals(it, true) } || getSystemProperty("ro.miui.ui.version.name")?.isNotBlank() == true -> withContext(noExceptionContext) {
        getClassOrNull("com.android.id.impl.IdProviderImpl")?.let {
            it.getAccessibleMethod("getOAID", Context::class.java)?.invoke(it.getAccessibleConstructor(true)?.newInstance(), this@getOAID) as String?
        }
    }
    Build.MANUFACTURER.equals("ZTE", true) -> getOAIDOfficially()
    // perfer HMS SDK
    com.huawei.hms.ads.identifier.AdvertisingIdClient.isAdvertisingIdAvailable(this@getOAID) -> withContext(noExceptionContext) {
        com.huawei.hms.ads.identifier.AdvertisingIdClient.getAdvertisingIdInfo(this@getOAID).id
        /*packageManager.getPackageInfoCompat("com.huawei.hwid")?.let {
            Intent("com.uodis.opendevice.OPENIDS_SERVICE").setPackage("com.huawei.hwid").takeIf { packageManager.queryIntentServices(it, 0).isNotEmpty() }?.let { intent ->
                // if Settings.Global.getString(contentResolver, "pps_oaid") and Settings.Global.getString(context.getContentResolver(), "pps_track_limit") all non null, id will be all zero.
                getOAIDThroughIBinder(intent) { OAIDInterface.HWID(it).getID() }
            }
        }*/
    }
    com.hihonor.ads.identifier.AdvertisingIdClient.isAdvertisingIdAvailable(this@getOAID) -> withContext(noExceptionContext) {
        com.hihonor.ads.identifier.AdvertisingIdClient.getAdvertisingIdInfo(this@getOAID).id
    }
    (Build.MANUFACTURER.equals("FERRMEOS", ignoreCase = true) ||
            getSystemProperty("ro.build.freeme.label").equals("FREEMEOS", ignoreCase = true)) -> getOAIDOfficially()
    (Build.MANUFACTURER.equals("SSUI", ignoreCase = true) ||
            !getSystemProperty("ro.ssui.product").equals("unknown", ignoreCase = true)) -> getOAIDOfficially()
    else -> getOAIDOfficially()
}

private suspend fun Context.getOAIDOfficially() = withContext(noExceptionContext) {
    packageManager.getPackageInfoCompat("com.mdid.msa")?.let {
        ContextCompat.startForegroundService(this@getOAIDOfficially, Intent("com.bun.msa.action.start.service")
            .setClassName("com.mdid.msa", "com.mdid.msa.service.MsaKlService")
            .putExtra("com.bun.msa.param.pkgname", packageName)
            .putExtra("com.bun.msa.param.runinset", true)).let {
                processIBinderFromOAIDService(Intent("com.bun.msa.action.bindto.service")
                    .setClassName("com.mdid.msa", "com.mdid.msa.service.MsaIdService")
                    .putExtra("com.bun.msa.param.pkgname", packageName)) {
                    val iter = OAIDInterface.GENERALID(it)
                    iter.getID(1, packageName).takeIf { iter.isSupport }
                }
        }
    }
}

private suspend fun Context.processIBinderFromOAIDService(intent: Intent, iface: suspend (IBinder?) -> String?): String? {
    val queue = Channel<IBinder?>(Channel.CONFLATED)
    val connection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) { queue.trySendBlocking(service) }
        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }
    if (bindService(intent, connection, Context.BIND_AUTO_CREATE)) try {
        return iface.invoke(queue.tryReceive().getOrThrow())
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        unbindService(connection)
        queue.close()
    }
    return null
}

// https://github.com/shuzilm-open-source/Get_Oaid_CNAdid/tree/master/OAID_source
interface OAIDInterface : IInterface {
    val descriptor: String
    val idOffset: Int
    val supportOffset: Int

    val isSupport: Boolean
        get() {
            if (supportOffset < 0) return true
            val obtain = Parcel.obtain()
            val obtain2 = Parcel.obtain()
            try {
                obtain.writeInterfaceToken(descriptor)
                asBinder()?.transact(IBinder.FIRST_CALL_TRANSACTION + supportOffset, obtain, obtain2, 0)
                obtain2.readException()
                return obtain2.readInt() != 0
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                obtain2.recycle()
                obtain.recycle()
            }
            return true
        }

    fun getID(length: Int = 0, vararg parameters: String = emptyArray()): String? {
        val obtain = Parcel.obtain()
        val obtain2 = Parcel.obtain()
        try {
            obtain.writeInterfaceToken(descriptor)
            if (length > 0 && parameters.size == length) {
                parameters.forEach { obtain.writeString(it) }
            }
            asBinder().transact(IBinder.FIRST_CALL_TRANSACTION + idOffset, obtain, obtain2, 0)
            obtain2.readException()
            return obtain2.readString()
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            obtain.recycle()
            obtain2.recycle()
        }
        return null
    }

    class ASUSID(private val iBinder: IBinder?) : OAIDInterface {
        override val descriptor: String
            get() = "com.asus.msa.SupplementaryDID.IDidAidlInterface"
        override val idOffset: Int
            get() = 2
        override val supportOffset: Int
            get() = 0

        override fun asBinder() = iBinder
    }

    class HWID(private val iBinder: IBinder?) : OAIDInterface {
        override val descriptor: String
            get() = "com.uodis.opendevice.aidl.OpenDeviceIdentifierService"
        override val idOffset: Int
            get() = 0
        override val supportOffset: Int
            get() = 0

        override fun asBinder() = iBinder
    }

    class LENID(private val iBinder: IBinder?) : OAIDInterface {
        override val descriptor: String
            get() = "com.zui.deviceidservice.IDeviceidInterface"
        override val idOffset: Int
            get() = 0
        override val supportOffset: Int
            get() = 2

        override fun asBinder() = iBinder
    }

    class OPPOID private constructor(private val iBinder: IBinder?, private val isOnePlus: Boolean = false) : OAIDInterface {
        companion object {
            fun newInstance(iBinder: IBinder?, isOnePlus: Boolean = false): OPPOID =
                iBinder?.queryLocalInterface(if (isOnePlus) "com.oplus.stdid.IStdID" else "com.heytap.openid.IOpenID")?.takeIf { it is OPPOID } as OPPOID? ?: OPPOID(iBinder, isOnePlus)
        }
        override val descriptor: String
            get() = if (isOnePlus) "com.oplus.stdid.IStdID" else "com.heytap.openid.IOpenID"
        override val idOffset: Int
            get() = 0
        override val supportOffset: Int
            get() = -1

        override fun asBinder() = iBinder
    }

    class SAMID(private val iBinder: IBinder?): OAIDInterface {
        override val descriptor: String
            get() = "com.samsung.android.deviceidservice.IDeviceIdService"
        override val idOffset: Int
            get() = 0
        override val supportOffset: Int
            get() = -1

        override fun asBinder() = iBinder
    }

    class COOLID private constructor(private val iBinder: IBinder?): OAIDInterface {
        companion object {
            fun newInstance(iBinder: IBinder?): COOLID =
                iBinder?.queryLocalInterface("com.coolpad.deviceidsupport.IDeviceIdManager")?.takeIf { it is COOLID } as COOLID? ?: COOLID(iBinder)
        }
        override val descriptor: String
            get() = "com.coolpad.deviceidsupport.IDeviceIdManager"
        override val idOffset: Int
            get() = 1
        override val supportOffset: Int
            get() = -1

        override fun asBinder() = iBinder
    }

    class QikuID(private val iBinder: IBinder?, private val isFromManager: Boolean = false): OAIDInterface {
        override val descriptor: String
            get() = "com.qiku.id.IOAIDInterface"
        override val idOffset: Int
            get() = if (isFromManager) 3 else 2
        override val supportOffset: Int
            get() = if (isFromManager) 1 else 0

        override fun asBinder() = iBinder
    }

    class GENERALID(private val iBinder: IBinder?): OAIDInterface {
        override val descriptor: String
            get() = "com.bun.lib.MsaIdInterface"
        override val idOffset: Int
            get() = 2
        override val supportOffset: Int
            get() = 0

        override fun asBinder() = iBinder
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun PackageInfo.getPrimarySignatureDigest() = getMessageDigestInstanceOrNull("SHA-1")?.let {
    PackageInfoCompat.getSignatures(appCtx.packageManager, packageName).firstOrNull()?.toByteArray()?.let { bytes ->
        it.digest(bytes).joinToString("") { (it.toUByte().toInt() or 256).toHexString(HexFormat.Default).substring(1, 3) } }.orEmpty()
}.orEmpty()
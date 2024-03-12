@file:Suppress("UnusedReceiverParameter", "unused")

package com.example.infotest.utils

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.media.MediaDrm
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.example.infotest.GlobalApplication
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.huawei.hms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateCoroutinesApi::class)
fun Context.getGAIDFallback() {
    if (GlobalApplication.gaid.isNullOrBlank() && AdvertisingIdClient.isAdvertisingIdAvailable(applicationContext))
        GlobalScope.launch(noExceptionContext) {
            GlobalApplication.gaid = AdvertisingIdClient.getAdvertisingIdInfo(applicationContext).id
        }.invokeOnCompletion { cancel -> if (cancel !is CancellationException && GlobalApplication.gaid.isNullOrBlank())
            GlobalScope.launch(noExceptionContext) { GlobalApplication.gaid = getOAID() }.invokeOnCompletion { cancel2 ->
                if (cancel2 !is CancellationException && GlobalApplication.gaid.isNullOrBlank()) GlobalScope.launch(noExceptionContext) {
                    // http://www.cnadid.cn/guide.html
                    GlobalApplication.gaid = Settings.System.getString(contentResolver, "ZHVzY2Lk").ifBlank {
                        // https://github.com/shuzilm-open-source/Get_Oaid_CNAdid/blob/master/CNAdid_source/CNAdidHelper.java#L48
                        getSharedPreferences("${packageName}_dna", Context.MODE_PRIVATE).getString("ZHVzY2Lk", null) ?:
                        Path(Environment.getExternalStorageDirectory().path, "Android/ZHVzY2Lk").takeIf { it.exists() }?.bufferedReader()?.use { it.readLine() }
                    }
                }.invokeOnCompletion { cancel3 -> if (cancel3 !is CancellationException && GlobalApplication.gaid.isNullOrBlank())
                    emitException { GlobalApplication.gaid = Settings.Secure.getString(contentResolver, "advertising_id") }
                }
            }
        }
}

//@SuppressLint("HardwareIds")
fun Context.getUniquePseudoId(): String = UUID.randomUUID().toString()/*try {
    val szDevIdShort = "35" + Build.BOARD.length % 10 + Build.BRAND.length % 10 + Build.CPU_ABI.length % 10 +
            Build.DEVICE.length % 10 + Build.DISPLAY.length % 10 + Build.HOST.length % 10 +
            Build.ID.length % 10 + Build.MANUFACTURER.length % 10 + Build.MODEL.length % 10 +
            Build.PRODUCT.length % 10 + Build.TAGS.length % 10 + Build.TYPE.length % 10 +
            Build.USER.length % 10

    val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

    val id = if (Build.SERIAL != Build.UNKNOWN) androidId + Build.SERIAL else androidId
    val serial: String = try {
        // api >= 9 reflect to get serial
        Build::class.java.getField("SERIAL").get(null)?.toString() ?: "serial"
    } catch (e: Exception) {
        // must init serial, use itself
        "serial"
    }
    UUID(ObjectsCompat.hashCode(szDevIdShort).toLong(), ObjectsCompat.hashCode(serial).toLong()).toString() + id
} catch (e: Exception) {
    ""
}*/

@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)
fun Context.getUniqueMediaDrmID(): String = {
    MediaDrm(UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)).use {
        val widevineId = it.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
        val md = getMessageDigestInstanceOrNull("SHA-256")
        md?.update(widevineId)
        md?.digest()?.asUByteArray()?.joinToString("") { ubyte -> ubyte.toHexString() }.orEmpty()
    }
}.catchEmpty()

@OptIn(ExperimentalStdlibApi::class)
@RequiresPermission("com.google.android.providers.gsf.permission.READ_GSERVICES")
fun Context.getGSFId(): String = {
    if (GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS)
        contentResolver.queryAll("content://com.google.android.gsf.gservices".toUri(), projection = arrayOf("android_id")).use {
            it?.getSingleLong("android_id")?.toULong()?.toHexString().orEmpty()
        }
    else ""
}.catchEmpty()

suspend fun Context.getOAID(): String? = when (Build.MANUFACTURER.uppercase()) {
    "ASUS" -> withContext(noExceptionContext) {
        packageManager.getPackageInfoCompat("com.asus.msa.SupplementaryDID")?.let {
            getOAIDThroughIBinder(Intent("com.asus.msa.action.ACCESS_DID").setClassName("com.asus.msa.SupplementaryDID", "com.asus.msa.SupplementaryDID.SupplementaryDIDService")) {
                OAIDInterface.ASUSID(it).getID()
            }
        }
    }
    "LENOVO", "MOTOLORA" -> withContext(noExceptionContext) {
        getOAIDThroughIBinder(Intent().setClassName("com.zui.deviceidservice", "com.zui.deviceidservice.DeviceidService")) {
            OAIDInterface.LENID(it).getID()
        }
    }
    "MEIZU" -> withContext(noExceptionContext) {
        packageManager.resolveContentProvider("com.meizu.flyme.openidsdk", 0)?.let { info ->
            contentResolver.queryAll(Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(info.authority).build()).use {
                it.getSingleString("value")
            }
        }
    }
    "NUBIA" -> withContext(noExceptionContext) {
        contentResolver.acquireContentProviderClient("content://cn.nubia.identity/identity".toUri())?.let { client ->
            client.call("getOAID", null, null)?.takeIf { it.getInt("code", -1) == 0 }?.getString("id").also {
                @Suppress("DEPRECATION")
                if (atLeastN) client.close() else client.release()
            }
        }
    }
    "ONEPLUS", "OPPO" -> withContext(noExceptionContext) {
        packageManager.getPackageInfoCompat("com.heytap.openid")?.let { info ->
            PackageInfoCompat.getLongVersionCode(info).takeIf { it >= -1 }?.let {
                getOAIDThroughIBinder(Intent("action.com.heytap.openid.OPEN_ID_SERVICE").setClassName("com.heytap.openid", "com.heytap.openid.IdentifyService")) { binder ->
                    delay(3.seconds)
                    OAIDInterface.OPPOID.newInstance(binder).getID(packageName, packageManager.getPackageInfoCompat(packageName)?.getPrimarySignatureDigest().orEmpty(), "OUID")
                }
            }
        }
    }
    "SAMSUNG" -> withContext(noExceptionContext) {
        packageManager.getPackageInfoCompat("com.samsung.android.deviceidservice")?.let {
            getOAIDThroughIBinder(Intent().setClassName("com.samsung.android.deviceidservice", "com.samsung.android.deviceidservice.DeviceIdService")) {
                OAIDInterface.SAMID(it).getID()
            }
        }
    }
    "VIVO" -> withContext(noExceptionContext) {
        contentResolver.queryAll("content://com.vivo.vms.IdProvider/IdentifierId/OAID".toUri()).use {
            it.getSingleString("value")
        }
    }
    "XIAOMI", "BLACKSHARK" -> withContext(noExceptionContext) {
        getClassOrNull("com.android.id.impl.IdProviderImpl")?.let {
            it.getAccessibleMethod("getOAID", Context::class.java)?.invoke(it.getAccessibleConstructor(true)?.newInstance(), this@getOAID) as String
        }
    }
    "ZTE" -> getOAIDOfficially()
    // perfer HMS SDK
    "HUAWEI" -> withContext(noExceptionContext) {
        AdvertisingIdClient.getAdvertisingIdInfo(this@getOAID).id
        /*packageManager.getPackageInfoCompat("com.huawei.hwid")?.let {
            Intent("com.uodis.opendevice.OPENIDS_SERVICE").setPackage("com.huawei.hwid").takeIf { packageManager.queryIntentServices(it, 0).isNotEmpty() }?.let { intent ->
                // if Settings.Global.getString(contentResolver, "pps_oaid") and Settings.Global.getString(context.getContentResolver(), "pps_track_limit") all non null, id will be all zero.
                getOAIDThroughIBinder(intent) { OAIDInterface.HWID(it).getID() }
            }
        }*/
    }
    else -> {
        if (Build.MANUFACTURER.equals("FERRMEOS", ignoreCase = true) || getSystemProperty("ro.build.freeme.label").equals("FREEMEOS", ignoreCase = true))
            getOAIDOfficially()
        else if (Build.MANUFACTURER.equals("SSUI", ignoreCase = true) || !getSystemProperty("ro.ssui.product").equals("unknown", ignoreCase = true))
            getOAIDOfficially()
        else null
    }
}

private suspend fun Context.getOAIDOfficially() = withContext(noExceptionContext) {
    packageManager.getPackageInfoCompat("com.mdid.msa")?.let {
        startService(
            Intent("com.bun.msa.action.start.service")
            .setClassName("com.mdid.msa", "com.mdid.msa.service.MsaKlService")
            .putExtra("com.bun.msa.param.pkgname", packageName)
            .putExtra("com.bun.msa.param.runinset", true))?.let {
            getOAIDThroughIBinder(
                Intent("com.bun.msa.action.bindto.service")
                .setClassName("com.mdid.msa", "com.mdid.msa.service.MsaIdService")
                .putExtra("com.bun.msa.param.pkgname", packageName)) {
                OAIDInterface.GENERALID(it).getID()
            }
        }
    }
}

private suspend fun Context.getOAIDThroughIBinder(intent: Intent, iface: suspend (IBinder?) -> String?): String? {
    val queue = LinkedBlockingQueue<IBinder?>(1)
    val connection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) { queue.offer(service) }
        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }
    if (bindService(intent, connection, Context.BIND_AUTO_CREATE)) try {
        return iface.invoke(queue.poll())
    } finally {
        unbindService(connection)
        queue.clear()
    }
    return null
}

// https://github.com/shuzilm-open-source/Get_Oaid_CNAdid/tree/master/OAID_source
interface OAIDInterface : IInterface {
    fun getID(vararg parameters: String = emptyArray()): String?

    class ASUSID(private val iBinder: IBinder?) : OAIDInterface {
        override fun asBinder() = iBinder

        override fun getID(vararg parameters: String): String? {
            val obtain = Parcel.obtain()
            val obtain2 = Parcel.obtain()
            try {
                obtain.writeInterfaceToken("com.asus.msa.SupplementaryDID.IDidAidlInterface")
                iBinder?.transact(3, obtain, obtain2, 0)
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

        val isSupport: Boolean
            get() {
                var support = false
                val obtain = Parcel.obtain()
                val obtain2 = Parcel.obtain()
                try {
                    obtain.writeInterfaceToken("com.asus.msa.SupplementaryDID.IDidAidlInterface")
                    iBinder?.transact(1, obtain, obtain2, 0)
                    obtain2.readException()
                    if (obtain2.readInt() != 0) {
                        support = true
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                } finally {
                    obtain2.recycle()
                    obtain.recycle()
                }
                return support
            }
    }

    class HWID(private val iBinder: IBinder?) : OAIDInterface {
        override fun asBinder() = iBinder

        override fun getID(vararg parameters: String): String? {
            val obtain = Parcel.obtain()
            val obtain2 = Parcel.obtain()
            try {
                obtain.writeInterfaceToken("com.uodis.opendevice.aidl.OpenDeviceIdentifierService")
                iBinder?.transact(1, obtain, obtain2, 0)
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

        val boo: Boolean
            get() {
                var boo = false
                val obtain = Parcel.obtain()
                val obtain2 = Parcel.obtain()
                try {
                    obtain.writeInterfaceToken("com.uodis.opendevice.aidl.OpenDeviceIdentifierService")
                    iBinder?.transact(1, obtain, obtain2, 0)
                    obtain2.readException()
                    if (obtain2.readInt() != 0) {
                        boo = true
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                } finally {
                    obtain2.recycle()
                    obtain.recycle()
                }
                return boo
            }
    }

    class LENID(private val iBinder: IBinder?) : OAIDInterface {
        override fun asBinder() = iBinder
        override fun getID(vararg parameters: String): String? {
            val obtain = Parcel.obtain()
            val obtain2 = Parcel.obtain()
            try {
                obtain.writeInterfaceToken("com.zui.deviceidservice.IDeviceidInterface")
                iBinder?.transact(1, obtain, obtain2, 0)
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

        val isSupport: Boolean
            get() {
                var support = false
                val obtain = Parcel.obtain()
                val obtain2 = Parcel.obtain()
                try {
                    obtain.writeInterfaceToken("com.asus.msa.SupplementaryDID.IDidAidlInterface")
                    iBinder?.transact(3, obtain, obtain2, 0)
                    obtain2.readException()
                    if (obtain2.readInt() != 0) {
                        support = true
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                } finally {
                    obtain2.recycle()
                    obtain.recycle()
                }
                return support
            }
    }

    class OPPOID(private val iBinder: IBinder?) : OAIDInterface {
        companion object {
            fun newInstance(iBinder: IBinder?): OPPOID =
                iBinder?.queryLocalInterface("com.heytap.openid.IOpenID")?.takeIf { it is OPPOID } as OPPOID? ?: OPPOID(iBinder)
        }
        override fun asBinder() = iBinder

        @Suppress("ReplaceGetOrSet")
        override fun getID(vararg parameters: String): String? {
            parameters.takeIf { it.size == 3 }?.let {
                val obtain = Parcel.obtain()
                val obtain2 = Parcel.obtain()
                try {
                    obtain.writeInterfaceToken("com.heytap.openid.IOpenID")
                    obtain.writeString(it.get(0))
                    obtain.writeString(it.get(1))
                    obtain.writeString(it.get(2))
                    iBinder?.transact(1, obtain, obtain2, 0)
                    obtain2.readException()
                    return obtain2.readString()
                } catch (e: Throwable) {
                    e.printStackTrace()
                } finally {
                    obtain.recycle()
                    obtain2.recycle()
                }
            }
            return null
        }
    }

    class SAMID(private val iBinder: IBinder?): OAIDInterface {
        override fun asBinder() = iBinder
        override fun getID(vararg parameters: String): String? {
            val obtain = Parcel.obtain()
            val obtain2 = Parcel.obtain()
            try {
                obtain.writeInterfaceToken("com.samsung.android.deviceidservice.IDeviceIdService")
                iBinder?.transact(1, obtain, obtain2, 0)
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
    }

    class GENERALID(private val iBinder: IBinder?): OAIDInterface {
        override fun asBinder() = iBinder
        override fun getID(vararg parameters: String): String? {
            val obtain = Parcel.obtain()
            val obtain2 = Parcel.obtain()
            try {
                obtain.writeInterfaceToken("com.bun.lib.MsaIdInterface")
                iBinder?.transact(3, obtain, obtain2, 0)
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
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun PackageInfo.getPrimarySignatureDigest() = getMessageDigestInstanceOrNull("SHA-1")?.let {
    PackageInfoCompat.getSignatures(appCtx.packageManager, packageName).firstOrNull()?.toByteArray()?.let { bytes ->
        it.digest(bytes).joinToString("") { (it.toUByte().toInt() or 256).toHexString(HexFormat.Default).substring(1, 3) } }.orEmpty()
}.orEmpty()
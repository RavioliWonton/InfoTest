@file:Suppress("PrivateApi", "ObsoleteSdkInt", "MissingPermission", "HardwareIds",
    "NewApi", "DEPRECATION", "unused", "ConstPropertyName")
@file:OptIn(DelicateCoroutinesApi::class, ExperimentalPathApi::class, ExperimentalStdlibApi::class, ExperimentalWindowApi::class)

package com.example.infotest

import android.Manifest
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorManager
import android.inputmethodservice.InputMethodService
import android.location.Geocoder
import android.location.LocationManager
import android.media.MediaDrm
import android.net.*
import android.net.http.HttpEngine
import android.net.http.HttpException
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.*
import android.os.Build.VERSION_CODES.P
import android.os.Build.VERSION_CODES.Q
import android.os.ext.SdkExtensions
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.*
import android.telephony.*
import android.text.format.Formatter
import android.view.InputDevice
import androidx.activity.ComponentActivity
import androidx.annotation.CheckResult
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.IntRange
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import androidx.collection.MutableObjectList
import androidx.collection.ObjectList
import androidx.collection.emptyObjectList
import androidx.collection.mutableObjectListOf
import androidx.collection.objectListOf
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.LocaleManagerCompat
import androidx.core.content.ContentResolverCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.database.getBlobOrNull
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getFloatOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getShortOrNull
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import androidx.core.os.CancellationSignal
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.core.telephony.SubscriptionManagerCompat
import androidx.core.telephony.TelephonyManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import androidx.window.core.ExperimentalWindowApi
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.huawei.hms.ads.identifier.AdvertisingIdClient
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.rawType
import kotlinx.coroutines.*
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import okio.ByteString.Companion.toByteString
import splitties.init.appCtx
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.NetworkInterface
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.io.path.fileStore
import kotlin.io.path.inputStream
import kotlin.io.path.walk
import kotlin.math.hypot
import kotlin.system.exitProcess
import kotlin.text.HexFormat
import kotlin.time.Duration.Companion.seconds

object Constants {
    const val gaidTag = "gaid"
    const val appSetIdTag = "appSetId"
    const val lastLoginTag = "lastLogin"
    const val gpsTag = "gps"
    const val dbmTag = "dbm"
    const val wifiCapabilitiesTag = "wifiCapabilities"
    const val wifiPropertiesTag = "wifiProperties"
    const val addressTag = "address"
    val mmkvDefaultRoute = FileSystems.getDefault().separator + "mmkv"
    const val mmkvTimeId = "time"
}

@CheckResult
private fun buildBetween(@IntRange(Build.VERSION_CODES.BASE.toLong(), Build.VERSION_CODES.CUR_DEVELOPMENT.toLong()) floor: Int = appCtx.applicationInfo.minSdkVersion,
                         @IntRange(Build.VERSION_CODES.BASE.toLong(), Build.VERSION_CODES.CUR_DEVELOPMENT.toLong()) tail: Int = Build.VERSION_CODES.CUR_DEVELOPMENT) =
    Build.VERSION.SDK_INT in floor..tail
@ChecksSdkIntAtLeast
val atLeastLM = buildBetween(Build.VERSION_CODES.LOLLIPOP_MR1)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.M)
val atLeastM = buildBetween(Build.VERSION_CODES.M)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.N)
val atLeastN = buildBetween(Build.VERSION_CODES.N)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.O)
val atLeastO = buildBetween(Build.VERSION_CODES.O)
@ChecksSdkIntAtLeast(P)
val atLeastP = buildBetween(P)
@ChecksSdkIntAtLeast(Q)
val atLeastQ = buildBetween(Q)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.R)
val atLeastR = buildBetween(Build.VERSION_CODES.R)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
val atLeastS = buildBetween(Build.VERSION_CODES.S)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.TIRAMISU)
val atLeastT = buildBetween(Build.VERSION_CODES.TIRAMISU)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
val atLeastU = buildBetween(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

infix fun <T> Boolean.then(param: T): T? = if (this) param else null
fun Boolean?.toInt(): Int? = (this != null).then((this == true).then(1) ?: 0)
fun Int?.toBoolean(): Boolean? = (this != null).then((this!! > 0).then(true) ?: false)

@OptIn(ExperimentalContracts::class)
inline fun emitException(vararg neededThrowExceptions: Class<out Exception> = emptyArray(), block: () -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    try {
        block.invoke()
    } catch (e : Throwable) {
        if (neededThrowExceptions.any { e::class.java == it }) throw e
    }
}

//TODO: default value block also need to catch, need more elegant approach
@OptIn(ExperimentalContracts::class)
inline fun <reified T: Any> (() -> T?).catchReturn(defaultValue: T, vararg neededThrowExceptions: Class<out Exception> = emptyArray()): T {
    contract {
        callsInPlace(this@catchReturn, InvocationKind.EXACTLY_ONCE)
        returnsNotNull()
    }

    return try {
        invoke() ?: defaultValue
    } catch (e: Throwable) {
        if (neededThrowExceptions.any { e::class.java == it }) throw e
        defaultValue
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <reified T> (() -> T).catchReturnNull(defaultValue: T? = null, vararg neededThrowExceptions: Class<out Exception> = emptyArray()): T? {
    contract {
        callsInPlace(this@catchReturnNull, InvocationKind.EXACTLY_ONCE)
        returns(null) implies (defaultValue == null)
    }

    return try {
        invoke()
    } catch (e: Throwable) {
        if (neededThrowExceptions.any { e::class.java == it }) throw e
        defaultValue
    }
}
fun (() -> String?).catchEmpty() = catchReturn("")
inline fun <reified T> (() -> List<T>?).catchEmpty() = catchReturn(emptyList())
inline fun <reified T> (() -> ObjectList<T>?).catchEmpty() = catchReturn(emptyObjectList())
inline fun <reified T> (() -> Array<T>?).catchEmpty() = catchReturn(emptyArray())
fun (() -> Int?).catchZero() = catchReturn(0)
fun (() -> Long?).catchZero() = catchReturn(0L)
fun (() -> Double?).catchZero() = catchReturn(0.0)
fun (() -> Boolean?).catchFalse() = catchReturn(false)
inline fun <reified T> T.applyEmitException(block: T.() -> Unit): T = apply {
    emitException { block.invoke(this) }
}

private class ObjectListAdapter<T>(private val elementAdapter: JsonAdapter<T>) {
    @ToJson fun toJson(list: ObjectList<T>?) = list?.joinToString(separator = ", ", prefix = "[",
        postfix = "]", transform = { elementAdapter.toJson(it) }).orEmpty()
    @FromJson fun fromJson(list: Array<String?>?) = mutableObjectListOf<T>().plusAssign(list?.mapNotNull {
        element -> element?.let { elementAdapter.fromJson(it) } }.orEmpty())
}

private class ObjectListAdapterCompat<T>(private val elementAdapter: JsonAdapter<T>): JsonAdapter<ObjectList<T>>() {
    override fun fromJson(reader: JsonReader): ObjectList<T>? = {
        MutableObjectList<T>().apply {
            reader.beginArray()
            while (reader.hasNext()) {
                elementAdapter.fromJson(reader)?.let { add(it) }
            }
            reader.endArray()
        }
    }.catchReturnNull()

    override fun toJson(writer: JsonWriter, value: ObjectList<T>?) {
        writer.beginArray()
        value?.forEach { elementAdapter.toJson(writer, it) }
        writer.endArray()
    }

}

private class ObjectListAdapterFactory: JsonAdapter.Factory {
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? = {
        if (type.rawType == ObjectList::class.java && type is ParameterizedType)
            (type.actualTypeArguments.firstOrNull() as Class<*>?)
                ?.let { ObjectListAdapterCompat(moshi.adapter(it)) }
        else null
    }.catchReturnNull()
}

fun ExtensionModel.toJson(): String = Moshi.Builder().add(ObjectListAdapterFactory()).build()
    .adapter(ExtensionModel::class.java).nullSafe().lenient()
    .toJson(this)

@SuppressLint("IntentWithNullActionLaunch")
inline fun <reified T : Activity> Context.startActivityCompat(
    options: ActivityOptionsCompat? = null, intentBuilder: Intent.() -> Unit = {}
) = Intent(this, T::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    .apply(intentBuilder).takeIf { it.resolveActivity(packageManager) != null }
    ?.let { ContextCompat.startActivity(this, it, (options ?: ActivityOptionsCompat.makeBasic()).toBundle()) }

inline fun Context.startActionCompat(
    action: String, uri: Uri? = null, options: ActivityOptionsCompat? = null,
    intentBuilder: Intent.() -> Unit = {}
) = Intent(action, uri).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    .apply(intentBuilder).takeIf { it.resolveActivity(packageManager) != null }
    ?.let { ContextCompat.startActivity(this, it, (options ?: ActivityOptionsCompat.makeBasic()).toBundle()) }

val extensionCreationContext = Dispatchers.IO + CoroutineExceptionHandler { _, t ->
    if (GlobalApplication.isDebug) t.printStackTrace()
    MainActivity.text = "抓取数据错误，错误信息已经保存在Download文件夹，程序将在五秒后退出"
    GlobalScope.launch(noExceptionContext) {
        t.stackTraceToString().saveFileToDownload("modelError-${currentNetworkTimeInstant.toEpochMilli()}.txt")
        delay(5.seconds)
        exitProcess(-1)
    }
}
val noExceptionContext = Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }

val currentNetworkTimeInstant: Instant = {
    if (atLeastT && hasNetworkTime)
        SystemClock.currentNetworkTimeClock().instant()
    else if (GlobalApplication.trueTime.hasTheTime())
        GlobalApplication.trueTime.nowTrueOnly().toInstant()
    else if (atLeastQ && appCtx.getSystemService<LocationManager>()
            ?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true)
        SystemClock.currentGnssTimeClock().instant()
    else Instant.now()
}.catchReturn(Instant.now())

private val hasNetworkTime = {
    atLeastT && SystemClock.currentNetworkTimeClock().instant().isSupported(ChronoUnit.MILLIS)
}.catchFalse()

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
fun Context.isNetworkAvailable(): Boolean = { applicationContext.getSystemService<ConnectivityManager>()?.let { manager ->
    (atLeastM && listOf(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        .all { manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(it) == true })
    || manager.activeNetworkInfo?.isConnected == true
}}.catchFalse()

/*fun String?.retrace(mappingFile: File, isVerbose: Boolean = true) = run {
    Writer.nullWriter().buffered().use {
        ReTrace(ReTrace.REGULAR_EXPRESSION, ReTrace.REGULAR_EXPRESSION, false, isVerbose, mappingFile)
            .retrace(LineNumberReader(this.orEmpty().reader()), PrintWriter(it, true))
        it.toString()
    }
}*/

@RequiresPermission.Write(RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, conditional = true))
fun String?.saveFileToDownload(fileName: String, contentResolver: ContentResolver = appCtx.contentResolver) {
    val buffer = ByteBuffer.wrap(this.orEmpty().encodeToByteArray()).asReadOnlyBuffer()
    if (atLeastQ) {
        val model = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        contentResolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), model)?.let { uri ->
            (contentResolver.openOutputStream(uri) as? FileOutputStream)?.use { stream ->
                //stream.channel.write(ByteBuffer.allocate(1024).also { it.put(this.orEmpty().encodeToByteArray()) })
                stream.channel.write(buffer)
                stream.fd.sync()
            }
            model.clear()
            model.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, model, null, null)
        }
    } else FileChannel.open(Paths.get(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
        fileName), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.READ).use {
        it.write(buffer)
        it.force(false)
    }
}

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
                        Path.of(Environment.getExternalStorageDirectory().path, "Android/ZHVzY2Lk").takeIf { it.exists() }?.bufferedReader()?.use { it.readLine() }
                    }
                }.invokeOnCompletion { cancel3 -> if (cancel3 !is CancellationException && GlobalApplication.gaid.isNullOrBlank())
                    emitException { GlobalApplication.gaid = Settings.Secure.getString(contentResolver, "advertising_id") }
                }
            }
        }
}

suspend fun ComponentActivity.createExtensionModel() = ExtensionModel(
    device = withContext(extensionCreationContext) { getDeviceInfo() },
    apps = withContext(extensionCreationContext) { getAppList() },
    contacts = withContext(extensionCreationContext) { getAddressBook() },
    calendars = withContext(extensionCreationContext) { getCalenderList() },
    sms = withContext(extensionCreationContext) { getSmsList() },
    photos = withContext(extensionCreationContext) { getImageList() },
    callLogs = withContext(extensionCreationContext) { getCallLog() }
)

@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE], conditional = true)
private val imageInternalUri = if (atLeastQ) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_INTERNAL)
    else MediaStore.Images.Media.INTERNAL_CONTENT_URI
@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE], conditional = true)
private val imageExternalUri = if (atLeastQ) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE], conditional = true)
private val videoInternalUri = if (atLeastQ) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_INTERNAL)
    else MediaStore.Video.Media.INTERNAL_CONTENT_URI
@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE], conditional = true)
private val videoExternalUri = if (atLeastQ) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE], conditional = true)
private val audioInternalUri = if (atLeastQ) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_INTERNAL)
    else MediaStore.Audio.Media.INTERNAL_CONTENT_URI
@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE], conditional = true)
private val audioExternalUri = if (atLeastQ) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

@SafeVarargs
private fun Context.countContent(@RequiresPermission vararg contentUri: Uri): Int =
    contentUri.filterNot { it == Uri.EMPTY }.fold(0) { sum, uri -> {
        contentResolver.queryAll(contentUri = uri, projection = emptyArray())
            .use { sum + (it?.count ?: 0) }
    }.catchReturn(sum)
}

@RequiresPermission(Manifest.permission.READ_PHONE_STATE)
private fun Context.getDefaultSubscriptionId() = getSystemService<TelephonyManager>()?.let(TelephonyManagerCompat::getSubscriptionId)
    ?.takeIf { it != Int.MAX_VALUE } ?: if (atLeastM) SubscriptionManager.getDefaultSubscriptionId()
       else if (atLeastLM) getSystemService<SmsManager>()?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
       else -1

@RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO,
                            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE,
                            "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
private fun Activity.getDeviceInfo(): DeviceInfo {
    val wifiManager = applicationContext.getSystemService<WifiManager>()
    val telephonyManager = getSystemService<TelephonyManager>()
    val connectivityManager = applicationContext.getSystemService<ConnectivityManager>()
    val storageInfo = getStoragePair()
    val sdCardInfo = getSDCardPair()
    val metric = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this)
    val insets = (ViewCompat.getRootWindowInsets(window.decorView) ?:
        if (atLeastR) metric.getWindowInsets() else WindowInsetsCompat.CONSUMED)
            .getInsets(WindowInsetsCompat.Type.systemBars())
    val address = {
        if (atLeastT) GlobalApplication.address
        else if (Geocoder.isPresent()) GlobalApplication.gps?.let {
            Geocoder(this).getFromLocation(it.latitude.toDouble(), it.longitude.toDouble(), 1)
        }?.firstOrNull()
        else null
    }.catchReturnNull()
    val imeiHistoryFallback = {
        if (atLeastLM) SubscriptionManagerCompat.getSlotIndex(getDefaultSubscriptionId())
            .takeIf { it != SubscriptionManager.INVALID_SIM_SLOT_INDEX }?.let { defaultSlotIndex ->
                telephonyManager?.getImeiCompat(defaultSlotIndex)?.let { arrayOf(it) } }
        else if (telephonyManager != null) TelephonyManagerCompat.getImei(telephonyManager)?.let { arrayOf(it) }
        else emptyArray()
    }.catchEmpty()
    return DeviceInfo(
        albs = "",//getAlbs(),
        audioExternal = countContent(audioExternalUri), audioInternal = countContent(audioInternalUri),
        batteryStatus = getBatteryStatus(), generalData = getGeneralData(),
        hardware = getHardwareInfo(), network = getNetworkInfo(), otherData = getOtherData(),
        newStorage = getStorageInfo(), contactGroup = countContent(ContactsContract.Groups.CONTENT_URI),
        buildId = GlobalApplication.appVersion.toString(), buildName = GlobalApplication.appVersionName,
        createTime = currentNetworkTimeInstant.toEpochMilli().toString(),//.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)),
        downloadFiles = {
            if (atLeastQ) countContent(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_INTERNAL))
            else Paths.get(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath).normalize()
                    .walk(PathWalkOption.INCLUDE_DIRECTORIES, PathWalkOption.FOLLOW_LINKS).count()
        }.catchZero(),
        imagesExternal = countContent(imageExternalUri), imagesInternal = countContent(imageInternalUri),
        packageName = packageName, gpsAdid = GlobalApplication.gaid.orEmpty(),
        videoExternal = countContent(videoExternalUri), videoInternal = countContent(videoInternalUri),
        deviceId = {
            getDeviceIdCompat().ifBlank { getUniqueMediaDrmID().ifBlank { getGSFId().ifBlank {
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                    ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }.orEmpty()//getUniquePseudoId()
            } } }
        }.catchEmpty(),
        deviceInfo = Build.MODEL.takeIf { it != Build.UNKNOWN }.orEmpty(),
        osType = "android", osVersion = Build.VERSION.RELEASE.takeIf { it != Build.UNKNOWN }.orEmpty(),
        ip = {
            if (atLeastU || atLeastR && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 7)
                ByteBuffer.allocate(Int.MAX_VALUE).also {
                    HttpEngine.Builder(this).build().newUrlRequestBuilder("https://api.ipify.org", Dispatchers.IO.asExecutor(), object : UrlRequest.Callback {
                        override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) = Unit
                        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) = Unit
                        override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) = Unit

                        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                            request.read(it)
                        }

                        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: HttpException) = Unit
                        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) = Unit
                    }).build().start()
                }.compact().toByteString().utf8()
            else Scanner(URL("https://api.ipify.org").openStream(), StandardCharsets.UTF_8.name())
                .useDelimiter("\\A").use { it.hasNext().then(it.next()) }
        }.catchEmpty(),
        memory = {
            ActivityManager.MemoryInfo().apply {
                getSystemService<ActivityManager>()?.getMemoryInfo(this)
            }.totalMem.toString()
        }.catchReturn("-1"),
        storage = {
            /*Formatter.formatFileSize(this, */storageInfo.first.toString()//)
        }.catchReturn("-1"),
        unUseStorage = {
            /*Formatter.formatFileSize(this, */storageInfo.second.toString()//)
        }.catchReturn("-1"),
        gpsLatitude = GlobalApplication.gps?.latitude.orEmpty(),
        gpsLongitude = GlobalApplication.gps?.longitude.orEmpty(),
        gpsAddress = address?.getAddressLine(0).orEmpty(),
        addressInfo = address?.toString().orEmpty(),
        isWifi = (atLeastQ && connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                || connectivityManager?.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI).toInt() ?: 0,
        wifiName = {
            if (atLeastS) GlobalApplication.currentWifiCapabilities?.ssid
            else if (atLeastQ) connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.extraInfo
            else wifiManager?.connectionInfo?.ssid.takeIf { it?.isNotBlank() == true && !it.equals(WifiManager.UNKNOWN_SSID, true) } ?:
                wifiManager?.configuredNetworks?.firstOrNull { it.networkId == wifiManager.connectionInfo?.networkId }?.SSID
        }.catchEmpty(),
        isRoot = getIsRooted(), isSimulator = getIsSimulator(),
        battery = getSystemService<BatteryManager>()?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1,
        lastLoginTime = GlobalApplication.lastLoginTime.toString(),
        picCount = countContent(imageExternalUri, imageInternalUri),
        imsi = { telephonyManager?.subscriberId?.ifBlank { "-1" } }.catchReturn("-1"),
        mac = getWifiMac(), sdCard = sdCardInfo.first, unUseSdCard = sdCardInfo.second,
        resolution = "${metric.bounds.width() - insets.left - insets.right}x${metric.bounds.height() - insets.bottom - insets.top}",
        idfa = "", idfv = "", brand = Build.BRAND.takeIf { it != Build.UNKNOWN }.orEmpty(),
        ime = { telephonyManager?.let(TelephonyManagerCompat::getImei) }.catchReturn(Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()),
        imeiHistory = {(
            if (atLeastU) getSystemService<SubscriptionManager>()?.allSubscriptionInfoList
                ?.filter { SubscriptionManager.isValidSubscriptionId(it.subscriptionId) }
                ?.mapNotNull { telephonyManager?.getImeiCompat(it.simSlotIndex).orEmpty() }
            else if (atLeastLM) getSystemService<SubscriptionManager>()?.completeActiveSubscriptionInfoList
                ?.mapNotNull { telephonyManager?.getImeiCompat(it.simSlotIndex).orEmpty() }
            else imeiHistoryFallback.asList())?.filter { it.isBlank() }?.toTypedArray()
        }.catchReturn(imeiHistoryFallback)
    )
}

private fun Context.getSDCardPair(): Pair<String, String> = {
    getSDCardInfo().firstOrNull {
        it.state in objectListOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
                && Files.exists(it.path) && it.isRemovable
    }?.path?.let {
        StatFs(it.absolutePathString()).let { stat ->
            /*Formatter.formatFileSize(this, */(stat.blockSizeLong * stat.blockCountLong).toString()/*)*/ to
                /*Formatter.formatFileSize(this,*/ (stat.blockSizeLong * stat.availableBlocksLong).toString()
        }
    }
}.catchReturn("-1" to "-1")

@Deprecated(message = "using official mutableObjectListOf", replaceWith = ReplaceWith("mutableObjectListOf", "androidx.collection.mutableObjectListOf"), level = DeprecationLevel.HIDDEN)
@SafeVarargs
@JvmOverloads
inline fun <reified T> mutableObjectListOf(vararg content: T = emptyArray()) = MutableObjectList<T>(content.size).apply { plusAssign(content.asSequence()) }
inline fun <reified T> Iterable<T>.asMutableObjectList() = MutableObjectList<T>(count()).apply { plusAssign(this@asMutableObjectList) }
inline fun <reified T> Iterable<T>.asObjectList() = asMutableObjectList() as ObjectList<T>
// _Collection.kt#L1725
inline fun <reified T> ObjectList<T>.all(predicate: (T) -> Boolean): Boolean {
    if (isEmpty()) return true
    forEach { if (!predicate(it)) return false }
    return true
}

private fun Context.getSDCardInfo() = mutableObjectListOf<SDCardInfo>().applyEmitException {
    val sm = getSystemService<StorageManager>()
    if (atLeastN) sm?.storageVolumes?.mapNotNull { SDCardInfo(it.directoryCompat, it.state, it.isRemovable) }?.let(::addAll)
    else getClassOrNull("android.os.storage.StorageVolume")?.let { clazz ->
        val pathMethod = clazz.getAccessibleMethod("getPath")
        val isRemovableMethod = clazz.getAccessibleMethod("isRemovable")
        val volumeStateMethod = StorageManager::class.java.getAccessibleMethod("getVolumeState", String::class.java)
        StorageManager::class.java.getAccessibleMethod("getVolumeList").invoke(sm, emptyArray<Any>())?.let {
            val length = java.lang.reflect.Array.getLength(it)
            for (i in 0 until length) {
                val storageVolumeElement = java.lang.reflect.Array.get(it, i)
                val path = pathMethod.invoke(storageVolumeElement, emptyArray<Any>()) as String
                add(SDCardInfo(Paths.get(path), volumeStateMethod.invoke(sm, path) as String,
                    isRemovableMethod.invoke(storageVolumeElement, emptyArray<Any>()) as Boolean))
            }
        }
    }
}

@Parcelize
private data class SDCardInfo(
    val path: @WriteWith<PathParceler> Path?,
    val state: String,
    val isRemovable: Boolean
): Parcelable

object PathParceler: Parceler<Path?> {
    override fun create(parcel: Parcel): Path = Paths.get(parcel.readString())
    override fun Path?.write(parcel: Parcel, flags: Int) {
        parcel.writeString(this?.absolutePathString().orEmpty())
    }
}

@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE], conditional = true)
fun Context.getImageList(): List<ImageInfo> = {
    val projection = arrayOf(
        if (atLeastQ) MediaStore.Images.ImageColumns._ID else MediaStore.Images.ImageColumns.DATA,
        MediaStore.Images.ImageColumns.DISPLAY_NAME, MediaStore.Images.ImageColumns.DATE_TAKEN,
        MediaStore.Images.ImageColumns.DATE_ADDED, MediaStore.Images.ImageColumns.DATE_MODIFIED)
    mutableObjectListOf<ImageInfo>().apply {
        contentResolver.queryAll(contentUri = imageExternalUri, projection = projection)
            .use { processCursor(it, contentResolver, imageExternalUri) }
        contentResolver.queryAll(contentUri = imageInternalUri, projection = projection)
            .use { processCursor(it, contentResolver, imageInternalUri) }
    }.asList()
}.catchEmpty()

@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.ACCESS_MEDIA_LOCATION], conditional = true)
private fun MutableObjectList<ImageInfo>.processCursor(cursor: Cursor?, contentResolver: ContentResolver, originalUri: Uri): Unit = emitException {
    while (cursor?.moveToNext() == true) {
        cursor.getStringOrNull(cursor.getColumnIndex(if (atLeastQ) MediaStore.Images.ImageColumns._ID else MediaStore.Images.ImageColumns.DATA))?.let { id ->
            (if (atLeastQ) contentResolver.openInputStream(MediaStore.setRequireOriginal(ContentUris.withAppendedId(originalUri, id.toLong())))
            else Paths.get(id).inputStream())?.use { input ->
                val exif = input.toExifInterface()
                val size = {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(input, Rect(), options)
                    options.outHeight.toString() to options.outWidth.toString()
                }.catchReturn(
                    exif?.getAttribute(ExifInterface.TAG_IMAGE_LENGTH) to
                            exif?.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
                )
                add(ImageInfo(
                    name = cursor.getStringOrNull(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DISPLAY_NAME)).orEmpty(),
                    height = size.first?.takeIf { it != "-1" } ?: exif?.getAttribute(ExifInterface.TAG_IMAGE_LENGTH).orEmpty(),
                    width = size.second?.takeIf { it != "-1" } ?: exif?.getAttribute(ExifInterface.TAG_IMAGE_WIDTH).orEmpty(),
                    latitude = exif?.latLong?.getOrNull(0),
                    longitude = exif?.latLong?.getOrNull(1),
                    //gpsTimeStamp = exif?.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP),
                    model = exif?.getAttribute(ExifInterface.TAG_MODEL).orEmpty(),
                    saveTime = cursor.getLongOrNull(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_MODIFIED))?.toString().orEmpty(),
                    date = (cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN).takeIf { it != -1 }?.let(cursor::getLongOrNull) ?:
                        exif?.getAttribute(ExifInterface.TAG_DATETIME)?.let { time ->
                            LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                                .toInstant(ZonedDateTime.now().offset).toEpochMilli().toString()
                        })?.toString().orEmpty(),
                    createTime = currentNetworkTimeInstant.toEpochMilli().toString(),
                    orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)?.toString().orEmpty(),
                    xResolution = exif?.getAttribute(ExifInterface.TAG_X_RESOLUTION).orEmpty(),
                    yResolution = exif?.getAttribute(ExifInterface.TAG_Y_RESOLUTION).orEmpty(),
                    altitude = exif?.getAltitude(0.0)?.toString().orEmpty(),
                    author = exif?.getAttribute(ExifInterface.TAG_ARTIST).orEmpty(),
                    lensMake = exif?.getAttribute(ExifInterface.TAG_LENS_MAKE).orEmpty(),
                    lensModel = exif?.getAttribute(ExifInterface.TAG_LENS_MODEL).orEmpty(),
                    focalLength = exif?.getAttribute(ExifInterface.TAG_FOCAL_LENGTH).orEmpty(),
                    software = exif?.getAttribute(ExifInterface.TAG_SOFTWARE).orEmpty(),
                    gpsProcessingMethod = exif?.getAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD).orEmpty(),
                    flash = exif?.getAttribute(ExifInterface.TAG_FLASH).orEmpty(),
                    takeTime = cursor.getLongOrNull(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_ADDED))
                        ?.toString().orEmpty()
                ))
            }
        }
    }
}

/*private fun Context.getAlbs(): String = Moshi.Builder().build().adapter<List<ImageInfo>>(Types.newParameterizedType(List::class.java,
    ImageInfo::class.java)).toJson(getImageList())*/

@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_NUMBERS, "android.permission.READ_PRIVILEGED_PHONE_STATE", Manifest.permission.ACCESS_NETWORK_STATE], conditional = true)
private fun Context.getGeneralData(): GeneralData {
    val telephonyManager = getSystemService<TelephonyManager>()
    val connectivityManager = applicationContext.getSystemService<ConnectivityManager>()
    val locale = {
        (LocaleManagerCompat.getSystemLocales(this).takeIf { !it.isEmpty && it != LocaleListCompat.getEmptyLocaleList() }
            ?: ConfigurationCompat.getLocales(Resources.getSystem().configuration).takeIf { !it.isEmpty && it != LocaleListCompat.getEmptyLocaleList() }
            ?: LocaleListCompat.getDefault()).get(0)
    }.catchReturn(Resources.getSystem().configuration.locale)
    val networkType = {
        if (atLeastN) {
            val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) "other"//"ethernet"
                else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) "bluetooth"
                else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) "wifi"
                else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                    telephonyManager?.dataNetworkType.determineDataNetworkType()
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
    return GeneralData(
        androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty(),
        currentSystemTime = currentNetworkTimeInstant.toEpochMilli().toString(),
        elapsedRealtime = SystemClock.elapsedRealtime().toString(),
        gaid = GlobalApplication.gaid.orEmpty(),
        imei = { telephonyManager?.let(TelephonyManagerCompat::getImei) }
            .catchReturn(Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()),
        isUsbDebug = (Settings.Global.getInt(contentResolver,
            Settings.Global.ADB_ENABLED, 0) > 0).toString(),
        isUsingProxyPort = {
            atLeastR && connectivityManager?.defaultProxy?.isValid == true ||
            Settings.Global.getString(contentResolver, Settings.Global.HTTP_PROXY).isNotBlank() ||
            (!System.getProperty("http.proxyHost").isNullOrEmpty() &&
                    (System.getProperty("http.proxyPort")?.toInt() ?: -1) != -1)
        }.catchFalse().toString(),
        isUsingVpn = {
            NetworkInterface.getNetworkInterfaces().asSequence().filter { it?.isUp == true }.any { iter ->
                listOf("tun", "ppp", "pptp").any { iter.name.startsWith(it, ignoreCase = true) } } ||
            if (atLeastN) connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            else connectivityManager?.allNetworks?.mapNotNull { connectivityManager.getNetworkCapabilities(it) }
                ?.any { it.isValidateNetwork(NetworkCapabilities.TRANSPORT_VPN) } == true
        }.catchFalse().toString(),
        language = locale?.language.orEmpty(), localeDisplayLanguage = locale?.displayLanguage.orEmpty(),
        localeISO3Country = locale?.isO3Country.orEmpty(), localeISO3Language = locale?.isO3Language.orEmpty(),
        mac = getWifiMac(), networkType = networkType, networkTypeNew = networkType,
        networkOperatorName = { if (atLeastQ) getSystemService<CarrierConfigManager>()?.let { manager ->
                (if (atLeastU) manager.getConfig(CarrierConfigManager.KEY_CARRIER_NAME_STRING) else manager.config)
                    ?.takeIf { CarrierConfigManager.isConfigForIdentifiedCarrier(it) }?.getString(CarrierConfigManager.KEY_CARRIER_NAME_STRING)
            } else telephonyManager?.networkOperatorName
        }.catchReturnNull(telephonyManager?.networkOperatorName.orEmpty()),
        phoneNumber = { PhoneNumberUtils.formatNumber((
            if (atLeastT) getSystemService<SubscriptionManager>()?.getPhoneNumber(getDefaultSubscriptionId())
            else if (atLeastLM) getSystemService<SubscriptionManager>()?.getActiveSubscriptionInfo(getDefaultSubscriptionId())?.number
            else telephonyManager?.line1Number)?.takeIf { telephonyManager?.simOperator?.isNotBlank() == true }.orEmpty(), locale.country)
        }.catchEmpty(),
        phoneType = { telephonyManager?.phoneType }.catchReturn(TelephonyManager.PHONE_TYPE_NONE),
        sensor = {
            getSystemService<SensorManager>()?.getSensorList(Sensor.TYPE_ALL)?.mapNotNull {
                Sensor(
                    maxRange = it.maximumRange.toString(),
                    minDelay = it.minDelay.toString(),
                    name = it.name.orEmpty(),
                    power = it.power.toString(),
                    resolution = it.resolution.toString(),
                    type = it.type.toString(),
                    vendor = it.vendor.orEmpty(),
                    version = it.version.toString()
                )
            }
        }.catchEmpty(),
        timeZoneId = ZoneId.systemDefault().normalized().id,
        uptimeMillis = SystemClock.uptimeMillis().toString(),
        uuid = { getUniquePseudoId() }.catchEmpty()
    )
}

private fun Int?.determineDataNetworkType(subNetworkTypeName: String? = null): String = when (this) {
    TelephonyManager.NETWORK_TYPE_GSM, TelephonyManager.NETWORK_TYPE_GPRS,
    TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_EDGE,
    TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> "2g"
    TelephonyManager.NETWORK_TYPE_TD_SCDMA, TelephonyManager.NETWORK_TYPE_EVDO_A,
    TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0,
    TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
    TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD,
    TelephonyManager.NETWORK_TYPE_HSPAP -> "3g"
    TelephonyManager.NETWORK_TYPE_IWLAN, TelephonyManager.NETWORK_TYPE_LTE -> "4g"
    TelephonyManager.NETWORK_TYPE_NR -> "5g"
    else -> objectListOf("TD-SCDMA", "WCDMA", "CDMA2000").any {
        subNetworkTypeName.contentEquals(it, true)
    }.then("3g") ?: "none"
}

private fun Context.getBatteryStatus(): BatteryStatus {
    val manager = getSystemService<BatteryManager>()
    val chargeStatus = ContextCompat.registerReceiver(this, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED)
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

@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
@OptIn(ExperimentalWindowApi::class)
private fun Activity.getHardwareInfo(): Hardware {
    val metric = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this)
    val currentPoint = metric.bounds
    val insets = (ViewCompat.getRootWindowInsets(window.decorView) ?:
        if (atLeastR) metric.getWindowInsets() else WindowInsetsCompat.CONSUMED)
            .getInsets(WindowInsetsCompat.Type.systemBars())
    return Hardware(
        board = Build.BOARD.takeIf { it != Build.UNKNOWN }.orEmpty(),
        brand = Build.BRAND.takeIf { it != Build.UNKNOWN }.orEmpty(),
        cores = Runtime.getRuntime().availableProcessors(),
        deviceHeight = currentPoint.height() - insets.bottom - insets.top,
        deviceName = Build.DEVICE.takeIf { it != Build.UNKNOWN }.orEmpty()/*try {
            Settings.System.getString(contentResolver, "device_name")
                ?: Settings.Secure.getString(contentResolver, "bluetooth_name")
                ?: Build.MODEL
        } catch (e: Exception) { "" }*/,
        deviceWidth = currentPoint.width() - insets.left - insets.right,
        model = Build.MODEL.trim { it <= ' ' }.replace("\\s*".toRegex(), "").takeIf { it != Build.UNKNOWN }.orEmpty(),
        physicalSize = getPhysicalSize()?.toString(), productionDate = Build.TIME,
        release = Build.VERSION.RELEASE.takeIf { it != Build.UNKNOWN }.orEmpty(),
        sdkVersion = Build.VERSION.SDK_INT.toString(),
        serialNumber = {
            (if (atLeastO) Build.getSerial() else Build.SERIAL).takeIf { it != Build.UNKNOWN }
        }.catchEmpty()
    )
}

private fun Context.isUiContextCompat() = {
    if (atLeastR) isUiContext
    else unwrapUntil { listOf(Activity::class.java, InputMethodService::class.java).any { it.isInstance(this) } } != null
}.catchFalse()

private fun Context.unwrapUntil(condition: (Context) -> Boolean): Context? {
    var context = this
    if (condition.invoke(context)) return context
    else while (context is ContextWrapper) {
        context = context.baseContext
        if (condition.invoke(context)) return context
    }
    return null
}

private fun Context.getPhysicalSize() = {
    val displayMetrics = Resources.getSystem().displayMetrics
    val maximumPoint = takeIf { it.isUiContextCompat() }?.let { WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(it).bounds }
    hypot((maximumPoint?.width() ?: displayMetrics.widthPixels).toFloat() / displayMetrics.xdpi,
        (maximumPoint?.height() ?: displayMetrics.heightPixels).toFloat() / displayMetrics.ydpi)
}.catchReturnNull()

private fun Context.isPad() = (getPhysicalSize() ?: -1f) >= 7.0f ||
        (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE

private fun NetworkCapabilities.isValidateNetwork(@IntRange(NetworkCapabilities.TRANSPORT_CELLULAR.toLong(), 9L) type: Int) =
    mutableListOf(NetworkCapabilities.NET_CAPABILITY_INTERNET).apply { if (atLeastM) add(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }.all { hasCapability(it) } && hasTransport(type)

@RequiresPermission(allOf = [Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_NETWORK_STATE], conditional = true)
private fun Context.getWifiConnectionInfo() = {
    if (atLeastS) GlobalApplication.currentWifiCapabilities
    else if (atLeastQ) applicationContext.getSystemService<ConnectivityManager>()?.let { manager ->
        manager.allNetworks.mapNotNull { manager.getNetworkCapabilities(it) }
            .firstOrNull { it.isValidateNetwork(NetworkCapabilities.TRANSPORT_WIFI) }?.transportInfo as WifiInfo?
    }
    else applicationContext.getSystemService<WifiManager>()?.connectionInfo
}.catchReturnNull(applicationContext.getSystemService<WifiManager>()?.connectionInfo)

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
private fun Context.getWifiLinkInetAddresses() = (GlobalApplication.currentWifiLinkProperties ?: applicationContext.getSystemService<ConnectivityManager>()?.let { manager ->
        manager.getLinkProperties(manager.allNetworks.firstOrNull { manager.getNetworkCapabilities(it)?.isValidateNetwork(NetworkCapabilities.TRANSPORT_WIFI) == true })
    })?.linkAddresses?.mapNotNull { it.address }?.filter { !it.isLoopbackAddress }.takeIf { !it.isNullOrEmpty() }?.asObjectList()
?: getWifiConnectionInfo()?.let { objectListOf(InetAddresses.parseNumericAddress(Formatter.formatIpAddress(it.ipAddress))) }

@RequiresPermission(allOf = [Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_NETWORK_STATE])
private fun Context.getNetworkInfo(): Network {
    val wifiManager = applicationContext.getSystemService<WifiManager>()
    val connectivityManager = applicationContext.getSystemService<ConnectivityManager>()
    if (atLeastR) wifiManager?.registerScanResultsCallback(Dispatchers.IO.asExecutor(), MainActivity.emptyWifiScanCallback!!)
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

private fun Context.getStorageInfo(): Storage {
    val internalStatfs = { StatFs(Environment.getDataDirectory().absolutePath) }.catchReturnNull()
    val externalStatfs = { StatFs(Environment.getExternalStorageDirectory().absolutePath) }.catchReturnNull()
    val memoryInfo = ActivityManager.MemoryInfo().applyEmitException {
        getSystemService<ActivityManager>()?.getMemoryInfo(this)
    }
    return Storage(
        appFreeMemory = { Runtime.getRuntime().freeMemory() }.catchZero().toString(),
        appMaxMemory = { Runtime.getRuntime().maxMemory() }.catchZero().toString(),
        appTotalMemory = { Runtime.getRuntime().totalMemory() }.catchZero().toString(),
        containSd = {
            (Environment.getExternalStorageState() in objectListOf(Environment.MEDIA_MOUNTED,
                Environment.MEDIA_MOUNTED_READ_ONLY, Environment.MEDIA_SHARED)).toInt()
        }.catchZero().toString(),
        extraSd = {
            (ContextCompat.getExternalFilesDirs(this, null).filterNotNull()
                .any { it.absolutePath.contains("extra") }).toInt()
        }.catchZero().toString(),
        internalStorageTotal = {
            (internalStatfs?.blockCountLong?:0L) * (internalStatfs?.blockSizeLong?:0L)
        }.catchZero(),
        internalStorageUsable = {
            (internalStatfs?.availableBlocksLong?:0L) * (internalStatfs?.blockSizeLong?:0L)
        }.catchZero(),
        memoryCardSize = {
            externalStatfs?.let { it.blockCountLong * it.blockSizeLong }
        }.catchReturn(-1L),
        memoryCardFreeSize = {
            externalStatfs?.let { it.freeBlocksLong * it.blockSizeLong }
        }.catchReturn(-1L),
        memoryCardUsableSize = {
            externalStatfs?.let { it.availableBlocksLong * it.blockSizeLong }
        }.catchReturn(-1L),
        memoryCardUsedSize = {
            externalStatfs?.let { (it.blockCountLong - it.availableBlocksLong) * it.blockSizeLong }
        }.catchReturn(-1L),
        ramTotalSize = memoryInfo.totalMem.toString(), ramUsableSize = memoryInfo.availMem.toString()
    )
}

@RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE])
private fun Context.getOtherData(): OtherData = OtherData(
    dbm = (if (atLeastQ && GlobalApplication.dbm != -1) GlobalApplication.dbm else getDbmCompat()).toString(),
    keyboard = {
        InputDevice.getDeviceIds().map { InputDevice.getDevice(it) }.firstOrNull { it?.isVirtual == false }?.keyboardType
    }.catchZero(),
    lastBootTime = (currentNetworkTimeInstant.toEpochMilli() - SystemClock.elapsedRealtimeNanos() / 1000000L).toString(),
    isRoot = getIsRooted(), isSimulator = getIsSimulator()
)

private fun Context.getIsRooted() = {
    (Build.TAGS != Build.UNKNOWN) && Build.TAGS.contains("test-keys")
    || arrayOf("/system/bin/", "/system/xbin/", "/sbin/", "/system/sd/xbin/",
        "/system/bin/failsafe/", "/data/local/xbin/", "/data/local/bin/",
        "/data/local/", "/system/sbin/", "/usr/bin/", "/vendor/bin/"
        ).any { Paths.get(it, "su").exists(LinkOption.NOFOLLOW_LINKS) }
    || arrayOf("/data/adb/modules", "/system/app/Superuser.apk").any { Paths.get(it)
        .exists(LinkOption.NOFOLLOW_LINKS) }
    || execCommandSize("which", "su")?.toBoolean() == true
}.catchFalse().toInt()

@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_BASIC_PHONE_STATE])
private fun Context.getIsSimulator() = {
    (Build.FINGERPRINT.startsWith("google/sdk_gphone_")
            && Build.FINGERPRINT.endsWith(":user/release-keys")
            && Build.MANUFACTURER == "Google" && Build.PRODUCT.startsWith("sdk_gphone_") && Build.BRAND == "google"
            && Build.MODEL.startsWith("sdk_gphone_"))
        || getSystemService<TelephonyManager>()?.networkOperatorName?.equals("android", true) == true
        // https://stackoverflow.com/a/2923743
        || !isPad() && getSystemService<TelephonyManager>()?.deviceSoftwareVersion == null
        || (getSystemService<SensorManager>()?.getSensorList(Sensor.TYPE_ALL)?.size ?: -1) <= 7
        //
        || Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.startsWith("unknown")
        || Build.FINGERPRINT.contains("ttVM_Hdragon") || Build.FINGERPRINT.contains("vbox86p")
        || Build.FINGERPRINT == "robolectric"
        || Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu")
        || Build.MODEL.contains("google_sdk") || Build.MODEL.contains("Emulator")
        || Build.MODEL.contains("Android SDK built for x86")
        // bluestacks
        || "QC_Reference_Phone" == Build.BOARD && !"Xiaomi".equals(Build.MANUFACTURER, ignoreCase = true) //bluestacks
        || Build.MANUFACTURER.contains("Genymotion")
        || Build.HOST == "Build2" //MSI App Player
        || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
        || Build.PRODUCT == "google_sdk" || Build.PRODUCT == "sdk_gphone64_arm64"
        || Build.PRODUCT.contains("vbox86p") || Build.PRODUCT.contains("emulator")
        || Build.PRODUCT.contains("simulator")
        // https://stackoverflow.com/a/33558819
        || ContextCompat.registerReceiver(this, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED)
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) == 0
        || { arrayOf("/dev/socket/qemud", "/dev/qemu_pipe", "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace", "/system/bin/qemu-prop").any { Paths.get(it).exists(LinkOption.NOFOLLOW_LINKS) } }.catchFalse()
        || execCommandSize("cat", "/proc/self/cgroup").toBoolean() == true
        // another Android SDK emulator check
        || { getSystemProperty("ro.kernel.qemu").equals("1") }.catchFalse()
}.catchFalse().toInt()

@SafeVarargs
private fun execCommand(command: String, vararg arguments: String = emptyArray(), environment: Array<String> = emptyArray()) = {
    Runtime.getRuntime().exec(arrayOf("sh", command).plus(arguments), environment).inputStream
}.catchReturnNull()
@SafeVarargs
private fun execCommandSize(command: String, vararg arguments: String = emptyArray()) = {
    execCommand(command, *arguments).use { it?.available() }
}.catchReturnNull()

@RequiresPermission.Read(RequiresPermission(Manifest.permission.READ_CONTACTS))
private fun Context.getAddressBook(): List<Contact> = { mutableObjectListOf<Contact>().apply {
    contentResolver.queryAll(contentUri = ContactsContract.Contacts.CONTENT_URI, projection = arrayOf(
        ContactsContract.Contacts.LAST_TIME_CONTACTED, ContactsContract.Contacts.DISPLAY_NAME,
        ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP, ContactsContract.Contacts._ID,
        ContactsContract.Contacts.TIMES_CONTACTED, ContactsContract.Contacts.HAS_PHONE_NUMBER
    )).use { while (it?.moveToNext() == true) {
        val contact = Contact(
            name = it.getStringOrNull(it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)).orEmpty(),
            phoneNumber = "",
            lastTimeContacted = it.getLongOrNull(it.getColumnIndex(ContactsContract.Contacts.LAST_TIME_CONTACTED)),
            updateTime = it.getLongOrNull(it.getColumnIndex(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)),
            timesContacted = it.getIntOrNull(it.getColumnIndex(ContactsContract.Contacts.TIMES_CONTACTED))
        )
        if ((it.getIntOrNull(it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) ?: -1) > 0)
            contentResolver.queryAll(contentUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                selection = arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID),
                selectionArgs = arrayOf(it.getStringOrNull(it.getColumnIndex(ContactsContract.Contacts._ID)))
            ).use { phoneCursor -> while (phoneCursor?.moveToNext() == true) {
                add(contact.copy(phoneNumber = phoneCursor.getStringOrNull(
                    phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)).orEmpty())) }
            }
        else add(contact) }
        }
    }.asList()
}.catchEmpty()

@SuppressLint("QueryPermissionsNeeded")
private fun Context.getAppList(): List<App> = {
    (if (atLeastT) packageManager.getInstalledPackages(PackageManager.PackageInfoFlags
        .of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()))
    else packageManager.getInstalledPackages(if (atLeastN) PackageManager.MATCH_UNINSTALLED_PACKAGES
        else PackageManager.GET_UNINSTALLED_PACKAGES)).mapNotNull {
        App(
            name = { packageManager.getApplicationLabel(it.applicationInfo).toString() }.catchEmpty(),
            packageName = it.packageName, obtainTime = currentNetworkTimeInstant.epochSecond,
            versionCode = PackageInfoCompat.getLongVersionCode(it).toString(),
            appType = (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                it.applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0).toInt()?.toString() ?: "0",
            installTime = it.firstInstallTime, updateTime = it.lastUpdateTime, appVersion = it.versionName.orEmpty()
        )
    }
}.catchEmpty()

@RequiresPermission.Read(RequiresPermission(Manifest.permission.READ_SMS))
private fun Context.getSmsList(): List<Sms> = {
    mutableObjectListOf<Sms>().apply {
        contentResolver.queryAll(contentUri = Telephony.Sms.CONTENT_URI,
            projection = arrayOf(
                Telephony.Sms.ADDRESS, Telephony.Sms.PERSON, Telephony.Sms.BODY, Telephony.Sms.READ,
                Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.SEEN, Telephony.Sms.STATUS,
                Telephony.Sms.SUBJECT), sortOrder = Telephony.Sms.DEFAULT_SORT_ORDER
        ).use {
            while (it?.moveToNext() == true) {
                add(Sms(
                    otherPhone = it.getStringOrNull(it.getColumnIndex(Telephony.Sms.ADDRESS)).orEmpty(),
                    content = it.getStringOrNull(it.getColumnIndex(Telephony.Sms.BODY)).orEmpty(),
                    seen = it.getIntOrNull(it.getColumnIndex(Telephony.Sms.SEEN)) ?: -1,
                    status = when (it.getIntOrNull(it.getColumnIndex(Telephony.Sms.STATUS))) {
                        Telephony.Sms.STATUS_NONE -> -1
                        Telephony.Sms.STATUS_COMPLETE -> 0
                        Telephony.Sms.STATUS_PENDING -> 64
                        Telephony.Sms.STATUS_FAILED -> 128
                        else -> -1 },
                    time = it.getLongOrNull(it.getColumnIndex(Telephony.Sms.DATE)) ?: -1L,
                    type = if (it.getIntOrNull(it.getColumnIndex(Telephony.Sms.TYPE)) != Telephony.Sms.MESSAGE_TYPE_INBOX) 2 else 1,
                    packageName = packageName,//Telephony.Sms.getDefaultSmsPackage(appCtx).orEmpty(),
                    read = it.getIntOrNull(it.getColumnIndex(Telephony.Sms.READ)) ?: -1,
                    subject = it.getStringOrNull(it.getColumnIndex(Telephony.Sms.SUBJECT)).orEmpty()
                ))
            }
        }
    }.asList()
}.catchEmpty()

@RequiresPermission.Read(RequiresPermission(Manifest.permission.READ_CALENDAR))
@RequiresPermission(Manifest.permission.GET_ACCOUNTS)
private fun Context.getCalenderList(): List<Calendar> = {
    mutableObjectListOf<Calendar>().apply {
        AccountManager.get(applicationContext)?.accounts?.forEach { account ->
            contentResolver.queryAll(contentUri = CalendarContract.Calendars.CONTENT_URI, projection = arrayOf(CalendarContract.Calendars._ID),
                selection = arrayOf(CalendarContract.CALLER_IS_SYNCADAPTER, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE),
                selectionArgs = arrayOf(true.toString(), account.name, account.type), sortOrder = CalendarContract.Calendars.DEFAULT_SORT_ORDER
            ).use { calendar -> if (calendar?.moveToNext() == true) {
                contentResolver.queryAll(contentUri = CalendarContract.Events.CONTENT_URI,
                    projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events._ID, CalendarContract.Events.DTEND,
                        CalendarContract.Events.DTSTART, CalendarContract.Events.DESCRIPTION), selection = arrayOf(CalendarContract.Events.CALENDAR_ID),
                    selectionArgs = arrayOf(calendar.getLongOrNull(calendar.getColumnIndex(CalendarContract.Calendars._ID)).toString())
                ).use { cursor -> while (cursor?.moveToNext() == true)
                    cursor.getLongOrNull(cursor.getColumnIndex(CalendarContract.Events._ID))?.let { id -> add(Calendar(eventId = id,
                        eventTitle = cursor.getStringOrNull(cursor.getColumnIndex(CalendarContract.Events.TITLE)).orEmpty(),
                        endTime = cursor.getLongOrNull(cursor.getColumnIndex(CalendarContract.Events.DTEND)) ?: -1L,
                        startTime = cursor.getLongOrNull(cursor.getColumnIndex(CalendarContract.Events.DTSTART)) ?: -1L,
                        des = cursor.getStringOrNull(cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)).orEmpty(),
                        reminders = mutableListOf<Reminder>().apply {
                            CalendarContract.Reminders.query(contentResolver, id, arrayOf(CalendarContract.Reminders.METHOD,
                                CalendarContract.Reminders.MINUTES, CalendarContract.Reminders._ID)).use {
                                    while (it?.moveToNext() == true) add(Reminder(eventId = id,
                                        method = it.getIntOrNull(it.getColumnIndex(CalendarContract.Reminders.METHOD)),
                                        minutes = it.getIntOrNull(it.getColumnIndex(CalendarContract.Reminders.MINUTES)),
                                        reminderId = it.getLongOrNull(it.getColumnIndex(CalendarContract.Reminders._ID))))
                                }
                            }))
                        }
                    }
                }
            }
        }
        contentResolver.queryAll(contentUri = CalendarContract.Events.CONTENT_URI,
            projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events._ID, CalendarContract.Events.DTEND,
                CalendarContract.Events.DTSTART, CalendarContract.Events.DESCRIPTION)).use { cursor -> while (cursor?.moveToNext() == true)
            cursor.getLongOrNull(cursor.getColumnIndex(CalendarContract.Events._ID))?.let { id -> if (count { it.eventId == id } == 0) add(Calendar(eventId = id,
                eventTitle = cursor.getStringOrNull(cursor.getColumnIndex(CalendarContract.Events.TITLE)).orEmpty(),
                endTime = cursor.getLongOrNull(cursor.getColumnIndex(CalendarContract.Events.DTEND)) ?: -1L,
                startTime = cursor.getLongOrNull(cursor.getColumnIndex(CalendarContract.Events.DTSTART)) ?: -1L,
                des = cursor.getStringOrNull(cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)).orEmpty(),
                reminders = mutableListOf<Reminder>().apply {
                    CalendarContract.Reminders.query(contentResolver, id, arrayOf(CalendarContract.Reminders.METHOD,
                        CalendarContract.Reminders.MINUTES, CalendarContract.Reminders._ID)).use {
                        while (it?.moveToNext() == true) add(Reminder(eventId = id,
                            method = it.getIntOrNull(it.getColumnIndex(CalendarContract.Reminders.METHOD)),
                            minutes = it.getIntOrNull(it.getColumnIndex(CalendarContract.Reminders.MINUTES)),
                            reminderId = it.getLongOrNull(it.getColumnIndex(CalendarContract.Reminders._ID))))
                    }
                }))
            }
        }
    }.asList()
}.catchEmpty()

@RequiresPermission.Read(RequiresPermission(Manifest.permission.READ_CALL_LOG))
private fun Context.getCallLog(): List<CallRecords> = {
    mutableObjectListOf<CallRecords>().apply {
        contentResolver.queryAll(contentUri = CallLog.Calls.CONTENT_URI, projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER,
            CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.COUNTRY_ISO, CallLog.Calls.FEATURES, CallLog.Calls.TYPE, CallLog.Calls.VIA_NUMBER,
            CallLog.Calls.LOCATION, CallLog.Calls.GEOCODED_LOCATION), sortOrder = CallLog.Calls.DEFAULT_SORT_ORDER).use {
            while (it?.moveToNext() == true)
                add(CallRecords(
                    id = it.getLongOrNull(it.getColumnIndex(CallLog.Calls._ID)) ?: -1L,
                    number = it.getStringOrNull(it.getColumnIndex(CallLog.Calls.NUMBER)).orEmpty(),
                    date = it.getLongOrNull(it.getColumnIndex(CallLog.Calls.DATE)) ?: -1L,
                    duration = it.getLongOrNull(it.getColumnIndex(CallLog.Calls.DURATION)) ?: -1L,
                    features = it.getIntOrNull(it.getColumnIndex(CallLog.Calls.FEATURES)) ?: -1,
                    type = it.getIntOrNull(it.getColumnIndex(CallLog.Calls.TYPE)) ?: -1,
                    caller = it.getStringOrNull(it.getColumnIndex(CallLog.Calls.VIA_NUMBER)).orEmpty(),
                    countryIso = it.getStringOrNull(it.getColumnIndex(CallLog.Calls.COUNTRY_ISO)).orEmpty(),
                    geocodedLocation = it.getStringOrNull(it.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)).orEmpty()
                ).also { callRecord -> if (atLeastR)
                    contentResolver.queryAll(contentUri = Uri.parse(it.getStringOrNull(it.getColumnIndex(CallLog.Calls.LOCATION))).normalizeScheme(),
                        projection = arrayOf(CallLog.Locations.LATITUDE, CallLog.Locations.LONGITUDE)).use { location ->
                            callRecord.latitude = location.getSingleDouble(CallLog.Locations.LATITUDE)
                            callRecord.longitude = location.getSingleDouble(CallLog.Locations.LONGITUDE)
                        }
                })
        }
    }.asList()
}.catchEmpty()

@WorkerThread
@SuppressLint("DiscouragedPrivateApi")
private fun Context.getStoragePair(): Pair<Long, Long> {
    val storageManager = getSystemService<StorageManager>()
    var total = 0L
    var free = 0L
    try {
        if (atLeastN) {
            val validVolumes = storageManager?.storageVolumes
                ?.filter { it.state in objectListOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY) }
            if (atLeastO)
                validVolumes?.mapNotNull {
                    if (atLeastS) it.storageUuid else {
                        { it.directoryCompat?.toFile()?.let(storageManager::getUuidForPath) }.catchReturnNull(it.uuid.toUUIDorNull())
                    }
                }.takeIf { it?.isNotEmpty() == true }?.forEach {
                    total += getSystemService<StorageStatsManager>()?.getTotalBytes(it) ?: 0L
                    free += getSystemService<StorageStatsManager>()?.getFreeBytes(it) ?: 0L
                } ?: run {
                    total += getSystemService<StorageStatsManager>()?.getTotalBytes(StorageManager.UUID_DEFAULT) ?: 0L
                    free += getSystemService<StorageStatsManager>()?.getFreeBytes(StorageManager.UUID_DEFAULT) ?: 0L
                }
            else validVolumes?.forEach {
                total += it?.directoryCompat?.fileStore()?.totalSpace ?: 0L
                free += it?.directoryCompat?.fileStore()?.usableSpace ?: 0L
            }
        } else {
            val getPathMethod = getClassOrNull("android.os.storage.StorageVolume")?.getDeclaredAccessibleMethod("getPath")
            StorageManager::class.java.getDeclaredAccessibleMethod("getVolumeList").invoke(storageManager)?.let {
                for (i in 0 until java.lang.reflect.Array.getLength(it)) {
                    val volumeFile = Paths.get(getPathMethod?.invoke(java.lang.reflect.Array.get(it, i), emptyArray<Any>()) as String)
                    total += volumeFile.fileStore().totalSpace
                    free += volumeFile.fileStore().usableSpace
                }
            }
        }
    } catch (_: Exception) {
        StatFs(Environment.getExternalStorageDirectory().absolutePath).let {
            total = it.blockCountLong * it.blockSizeLong
            free = it.freeBlocksLong * it.blockSizeLong
        }
    }
    return total to free
}

private fun String?.toUUIDorNull() = { UUID.fromString(this) }.catchReturnNull()

private fun Context.getDbmCompat() = { getSystemService<TelephonyManager>()?.allCellInfo?.lastDbmCompat }.catchReturn(-1)

val Collection<CellInfo?>.lastDbmCompat: Int
    get() = { mapNotNull {
        (when {
            atLeastR -> it?.cellSignalStrength
            it is CellInfoGsm -> it.cellSignalStrength
            it is CellInfoCdma -> it.cellSignalStrength
            atLeastQ && it is CellInfoTdscdma -> it.cellSignalStrength
            it is CellInfoWcdma -> it.cellSignalStrength
            it is CellInfoLte -> it.cellSignalStrength
            atLeastQ && it is CellInfoNr -> it.cellSignalStrength
            else -> null
        })?.dbm
    }.lastOrNull { it != if (atLeastQ) CellInfo.UNAVAILABLE else Int.MIN_VALUE }
}.catchReturn(-1)

private val StorageVolume.directoryCompat: Path?
    @SuppressLint("DiscouragedPrivateApi")
    get() = Paths.get(if (atLeastR) directory?.absolutePath else {
        { javaClass.getDeclaredAccessibleMethod("getPath").invoke(this, emptyArray<Any>()) as String }.catchReturnNull()
    }).normalize()

private fun InputStream.toExifInterface() = { ExifInterface(this) }.catchReturnNull()

@SuppressLint("PrivateApi")
private fun getBatteryCapacityByHook() = {
    getClassOrNull("com.android.internal.os.PowerProfile")?.let {
        it.getAccessibleMethod("getBatteryCapacity").invoke(it.getAccessibleConstructor(true,
            Context::class.java).newInstance(appCtx), emptyArray<Any>()) as Double
    }
}.catchZero()

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

private fun ContentResolver.queryAll(@RequiresPermission contentUri: Uri, projection: Array<String>? = null,
                                   selection: Array<String?>? = null, selectionArgs: Array<String?>? = null,
                                   sortOrder: String? = null, cancellationSignal: CancellationSignal? = null): Cursor?
= if (atLeastO) query(contentUri.let { if (it.authority == MediaStore.AUTHORITY && buildBetween(Q, P))
    MediaStore.setIncludePending(it) else it }, projection, Bundle().apply {
        selection?.joinToString(separator = ", ") { "$it = ?" }?.let { putString(ContentResolver.QUERY_ARG_SQL_SELECTION, it) }
        selectionArgs?.let { putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, it) }
        sortOrder?.let { putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, it) }
    }.also {
        if (contentUri.authority == MediaStore.AUTHORITY && atLeastR) {
            it.putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            it.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            if (atLeastS) it.putInt(MediaStore.QUERY_ARG_INCLUDE_RECENTLY_UNMOUNTED_VOLUMES, MediaStore.MATCH_INCLUDE)
        }
    }, cancellationSignal?.cancellationSignalObject as android.os.CancellationSignal?)
else ContentResolverCompat.query(this, contentUri, projection,
    selection?.joinToString(separator = ", ") { "$it = ?" }, selectionArgs, sortOrder, cancellationSignal)

@OptIn(ExperimentalUnsignedTypes::class)
private fun Context.getUniqueMediaDrmID(): String = {
    MediaDrm(UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)).use {
        val widevineId = it.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
        val md = getMessageDigestInstanceOrNull("SHA-256")
        md?.update(widevineId)
        md?.digest()?.asUByteArray()?.joinToString("") { ubyte ->
            ubyte.toHexString()
        }.orEmpty()
    }
}.catchEmpty()

@RequiresPermission(Manifest.permission.READ_PHONE_STATE)
private fun Context.getDeviceIdCompat(): String = {
    getSystemService<TelephonyManager>()?.let {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_GSM) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CDMA) && !atLeastQ)
            TelephonyManagerCompat.getImei(it)
        else if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CDMA) && atLeastQ)
            it.meid
        else null
    }
}.catchEmpty()

@RequiresPermission("com.google.android.providers.gsf.permission.READ_GSERVICES")
private fun Context.getGSFId(): String = {
    if (GoogleApiAvailabilityLight.getInstance()
            .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS)
        contentResolver.queryAll(contentUri = "content://com.google.android.gsf.gservices".toUri(),
            projection = arrayOf("android_id")).use {
                it?.getSingleLong("android_id")?.toULong()?.toHexString().orEmpty()
        }
    else ""
}.catchEmpty()

@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
@Throws(SecurityException::class)
private fun TelephonyManager.getImeiCompat(slotIndex: Int): String? =
    if (atLeastQ) getImei(slotIndex)
    else if (atLeastM) getDeviceId(slotIndex)
    else {// TelephonyManagerCompat.getImei
        { javaClass.getDeclaredAccessibleMethod("getDeviceId", Int.Companion::class.java)
            .invoke(this, slotIndex) as String }.catchReturnNull(null, SecurityException::class.java)
    }

private fun Cursor?.getSingleInt(column: String) = if (this?.moveToFirst() == true)
    getIntOrNull(getColumnIndex(column)) else null
private fun Cursor?.getSingleLong(column: String) = if (this?.moveToFirst() == true)
    getLongOrNull(getColumnIndex(column)) else null
private fun Cursor?.getSingleString(column: String) = if (this?.moveToFirst() == true)
    getStringOrNull(getColumnIndex(column)) else null
private fun Cursor?.getSingleDouble(column: String) = if (this?.moveToFirst() == true)
    getDoubleOrNull(getColumnIndex(column)) else null
private fun Cursor?.getSingleFloat(column: String) = if (this?.moveToFirst() == true)
    getFloatOrNull(getColumnIndex(column)) else null
private fun Cursor?.getSingleShort(column: String) = if (this?.moveToFirst() == true)
    getShortOrNull(getColumnIndex(column)) else null
private fun Cursor?.getSingleBlob(column: String) = if (this?.moveToFirst() == true)
    getBlobOrNull(getColumnIndex(column)) else null

@RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.READ_EXTERNAL_STORAGE, "android.permission.LOCAL_MAC_ADDRESS"], conditional = true)
private fun Context.getWifiMac(): String = {(
    if (atLeastN) (NetworkInterface.getNetworkInterfaces().asSequence().firstOrNull { it.name.equals("wlan0", true) }
        ?: NetworkInterface.getByInetAddress(getWifiLinkInetAddresses()?.firstOrNull()))?.hardwareAddress.formatMac()
    else if (atLeastM) listOf("/sys/class/net/wlan0/address", "/sys/class/net/eth0/address").filter { Path.of(it).exists() }
        .firstNotNullOfOrNull { address -> execCommand("cat", address)?.bufferedReader()?.use { it.readLine() }
    } else getWifiConnectionInfo()?.macAddress)?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }
}.catchEmpty()

private fun ByteArray?.formatMac(): String = {
    takeIf { this?.size == 6 }?.toHexString(HexFormat { bytes.byteSeparator = ":" })
}.catchEmpty()

@SafeVarargs
private fun Class<*>.getAccessibleMethod(name: String, vararg parameterTypes: Class<*> = emptyArray()) =
    getMethod(name, *parameterTypes).apply { isAccessible = true }

@SafeVarargs
private fun Class<*>.getDeclaredAccessibleMethod(name: String, vararg parameterTypes: Class<*> = emptyArray()) =
    getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }

@SafeVarargs
private fun Class<*>.getAccessibleConstructor(isDeclared: Boolean = false, vararg parameterTypes: Class<*> = emptyArray()) =
    (if (isDeclared) getDeclaredConstructor(*parameterTypes) else getConstructor(*parameterTypes)).apply { isAccessible = true }

private fun getSystemProperty(key: String, defaultValue: String? = "") = getClassOrNull("android.os.SystemProperties")
    ?.getAccessibleMethod("get", String::class.java, String::class.java)?.invoke(null, key, defaultValue) as String?

fun PackageManager.getPackageInfoCompat(packageName: String, packageInfoFlags: Int = 0) = try {
    if (atLeastT) getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(packageInfoFlags.toLong()))
    else getPackageInfo(packageName, packageInfoFlags)
} catch (e: PackageManager.NameNotFoundException) { null }

private fun getMessageDigestInstanceOrNull(algorithm: String) = { MessageDigest.getInstance(algorithm) }.catchReturnNull()
private fun getClassOrNull(className: String) = { ClassLoader.getSystemClassLoader().loadClass(className) }.catchReturnNull()

private fun PackageInfo.getPrimarySignatureDigest() = getMessageDigestInstanceOrNull("SHA-1")?.let {
    PackageInfoCompat.getSignatures(appCtx.packageManager, packageName).firstOrNull()?.toByteArray()?.let { bytes ->
        it.digest(bytes).joinToString("") { (it.toUByte().toInt() or 256).toHexString(HexFormat.Default).substring(1, 3) } }.orEmpty()
}.orEmpty()

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
            it.getAccessibleMethod("getOAID", Context::class.java).invoke(it.getAccessibleConstructor(true).newInstance(), this@getOAID) as String
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
        startService(Intent("com.bun.msa.action.start.service")
            .setClassName("com.mdid.msa", "com.mdid.msa.service.MsaKlService")
            .putExtra("com.bun.msa.param.pkgname", packageName)
            .putExtra("com.bun.msa.param.runinset", true))?.let {
            getOAIDThroughIBinder(Intent("com.bun.msa.action.bindto.service")
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

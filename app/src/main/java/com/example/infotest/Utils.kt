@file:Suppress("PrivateApi", "ObsoleteSdkInt", "MissingPermission", "HardwareIds", "NewApi", "DEPRECATION")
@file:OptIn(DelicateCoroutinesApi::class, ExperimentalPathApi::class)

package com.example.infotest

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.LocationManager
import android.media.MediaDrm
import android.net.*
import android.net.wifi.WifiManager
import android.os.*
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.*
import android.telephony.*
import android.text.format.Formatter
import android.view.InputDevice
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContentResolverCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.core.os.ConfigurationCompat
import androidx.core.telephony.SubscriptionManagerCompat
import androidx.core.telephony.TelephonyManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import androidx.window.layout.WindowMetricsCalculator
import com.instacart.library.truetime.TrueTime
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
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
import java.time.format.FormatStyle
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.fileStore
import kotlin.io.path.inputStream
import kotlin.io.path.walk
import kotlin.math.hypot
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

object Constants {
    const val gaidTag = "gaid"
}

infix fun <T> Boolean.then(param: T): T? = if (this) param else null
fun Boolean?.toInt(): Int? = (this != null).then((this == true).then(1) ?: 0)

fun ExtensionModel.toJson(): String = Moshi.Builder().build()
    .adapter(ExtensionModel::class.java).nullSafe().lenient()
    .toJson(this)

inline fun <reified T : Activity> Context.startActivityCompat(
    options: ActivityOptionsCompat? = null,
    intentBuilder: Intent.() -> Unit = {}
) =
    Intent(this, T::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .apply(intentBuilder).let {
            if (it.resolveActivity(packageManager) != null)
                ContextCompat.startActivity(
                    this, it,
                    (options ?: ActivityOptionsCompat.makeBasic()).toBundle()
                )
        }

inline fun Context.startActionCompat(
    action: String, uri: Uri? = null, options: ActivityOptionsCompat? = null,
    intentBuilder: Intent.() -> Unit = {}
) = Intent(action, uri).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply(intentBuilder).run {
        resolveActivity(packageManager)?.let { ContextCompat
            .startActivity(this@startActionCompat, this, (options ?: ActivityOptionsCompat.makeBasic()).toBundle()) }
}

@OptIn(DelicateCoroutinesApi::class)
val extensionCreationContext = GlobalScope.coroutineContext + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
    if (GlobalApplication.isDebug) t.printStackTrace()
    MainActivity.text = "抓取数据错误，错误信息已经保存在Download文件夹，程序将在五秒后退出"
    GlobalScope.launch(Dispatchers.IO) {
        t.stackTraceToString().saveFileToDownload("modelError-${ZonedDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.FULL))}.txt")
        delay(5.seconds)
        exitProcess(-1)
    }
}

val currentNetworkTimeInstant: Instant = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        SystemClock.currentNetworkTimeClock().instant()
    else if (TrueTime.isInitialized())
        TrueTime.now().toInstant()
    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && appCtx.getSystemService<LocationManager>()?.gnssCapabilities?.hasOnDemandTime() == true) ||
                appCtx.getSystemService<LocationManager>()?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true))
        SystemClock.currentGnssTimeClock().instant()
    else Instant.now()
} catch (e: Exception) {
    Instant.now()
}

/*fun String?.retrace(mappingFile: File, isVerbose: Boolean = true) = run {
    Writer.nullWriter().buffered().use {
        ReTrace(ReTrace.REGULAR_EXPRESSION, ReTrace.REGULAR_EXPRESSION, false, isVerbose, mappingFile)
            .retrace(LineNumberReader(this.orEmpty().reader()), PrintWriter(it, true))
        it.toString()
    }
}*/

fun String?.saveFileToDownload(fileName: String, contentResolver: ContentResolver = appCtx.contentResolver) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val model = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, model)?.let { uri ->
            (contentResolver.openOutputStream(uri) as? FileOutputStream)?.use { stream ->
                //stream.channel.write(ByteBuffer.allocate(1024).also { it.put(this.orEmpty().encodeToByteArray()) })
                stream.channel.write(ByteBuffer.wrap(this.orEmpty().encodeToByteArray()).asReadOnlyBuffer())
                stream.fd.sync()
            }
            model.clear()
            model.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, model, null, null)
        }
    } else /*File(Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS).absolutePath, fileName)*/
        FileChannel.open(Paths.get(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS).absolutePath, fileName), StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE, StandardOpenOption.READ).use {
            it.write(ByteBuffer.wrap(this.orEmpty().encodeToByteArray()).asReadOnlyBuffer())
            it.force(false)
        }
        /*Files.createFile(Paths.get(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS).absolutePath, fileName)).bufferedWriter().use {
            it.write(this.orEmpty())
            it.flush()
    }*/
}

suspend fun ComponentActivity.createExtensionModel() = ExtensionModel(
    device = withContext(extensionCreationContext) { getDeviceInfo() },
    apps = withContext(extensionCreationContext) { getAppList() },
    contacts = withContext(extensionCreationContext) { getAddressBook() },
    calendars = withContext(extensionCreationContext) { getCalenderList() },
    sms = withContext(extensionCreationContext) { getSmsList() },
    photos = withContext(extensionCreationContext) { getImageList() }
)

private val imageInternalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_INTERNAL)
else MediaStore.Images.Media.INTERNAL_CONTENT_URI
private val imageExternalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
private val videoInternalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_INTERNAL)
else MediaStore.Video.Media.INTERNAL_CONTENT_URI
private val videoExternalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
private val audioInternalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_INTERNAL)
else MediaStore.Audio.Media.INTERNAL_CONTENT_URI
private val audioExternalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

private fun Context.countContent(vararg contentUri: Uri): Int =
    contentUri.filterNot { it == Uri.EMPTY }.fold(0) { sum, uri -> try {
        ContentResolverCompat.query(contentResolver, uri, emptyArray(),
            null, null, null, null)
            .use { sum + (it?.count ?: 0) }
    } catch (e: Exception) { sum }
}

private fun Activity.getDeviceInfo(): DeviceInfo {
    val wifiManager = getSystemService<WifiManager>()
    val telephonyManager = getSystemService<TelephonyManager>()
    val connectivityManager = getSystemService<ConnectivityManager>()
    val storageInfo = getStoragePair()
    val sdCardInfo = getSDCardPair()
    val currentPoint = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this).bounds
    /*val currentPoint = getSystemService<WindowManager>()?.let {
        Point().apply { it.defaultDisplay.getSize(this) }
    }*/
    val insets = (ViewCompat.getRootWindowInsets(window.decorView) ?: WindowInsetsCompat.CONSUMED)
        .getInsets(WindowInsetsCompat.Type.systemBars())
    val address = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GlobalApplication.address
        else GlobalApplication.gps?.let {
            Geocoder(this).getFromLocation(it.latitude.toDouble(), it.longitude.toDouble(), 1)
        }?.firstOrNull()
    } catch (e: Exception) { null }
    return DeviceInfo(
        albs = "",//getAlbs(),
        audioExternal = countContent(audioExternalUri),
        audioInternal = countContent(audioInternalUri),
        batteryStatus = getBatteryStatus(),
        generalData = getGeneralData(),
        hardware = getHardwareInfo(),
        network = getNetworkInfo(),
        otherData = getOtherData(),
        newStorage = getStorageInfo(),
        buildId = GlobalApplication.appVersion.toString(),
        buildName = GlobalApplication.appVersionName,
        contactGroup = countContent(ContactsContract.Groups.CONTENT_URI),
        createTime = currentNetworkTimeInstant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
        downloadFiles = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                countContent(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_INTERNAL))
            else /*Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).length().toInt()*/
                Paths.get(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
                    .walk(PathWalkOption.INCLUDE_DIRECTORIES).count()
        } catch (e: Exception) { 0 },
        imagesExternal = countContent(imageExternalUri),
        imagesInternal = countContent(imageInternalUri),
        packageName = packageName,
        videoExternal = countContent(videoExternalUri),
        videoInternal = countContent(videoInternalUri),
        gpsAdid = GlobalApplication.gaid.orEmpty(),
        deviceId = try {
            if (telephonyManager?.let { TelephonyManagerCompat.getImei(it) }?.isNotBlank() == true)
                TelephonyManagerCompat.getImei(telephonyManager)
            else if (getUniqueMediaDrmID().isNotBlank()) getUniqueMediaDrmID()
            else if (Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)?.let { it.isNotBlank() && it != "9774d56d682e549c" } == true)
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            else ""//getUniquePseudoId()
        } catch (e: Exception) { "" },
        deviceInfo = Build.MODEL,
        osType = "android", osVersion = Build.VERSION.RELEASE.toString(),
        ip = try {
            Scanner(URL("https://api.ipify.org").openStream(), StandardCharsets.UTF_8.name())
                .useDelimiter("\\A").use { it.hasNext().then(it.next()).orEmpty() }
            //appCtx.getSystemService<WifiManager>()?.connectionInfo?.ipAddress?.let { Formatter.formatIpAddress(it) }.orEmpty()
        } catch (e: Exception) { "" },
        memory = try {
            ActivityManager.MemoryInfo().apply {
                getSystemService<ActivityManager>()?.getMemoryInfo(this)
            }.totalMem.toString()
        } catch (e: Exception) { "-1" },
        storage = try {
            /*Formatter.formatFileSize(this, */storageInfo.first.toString()//)
        } catch (e: Exception) { "-1" },
        unUseStorage = try {
            /*Formatter.formatFileSize(this, */storageInfo.second.toString()//)
        } catch (e: Exception) { "-1" },
        gpsLatitude = GlobalApplication.gps?.latitude.orEmpty(),
        gpsLongitude = GlobalApplication.gps?.longitude.orEmpty(),
        gpsAddress = address?.getAddressLine(0).orEmpty(),
        addressInfo = address?.toString().orEmpty(),
        isWifi = connectivityManager?.let {
            ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && it.getNetworkCapabilities(it.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
                || getSystemService<ConnectivityManager>()?.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI).toInt() } ?: 0,
        wifiName = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                GlobalApplication.currentWifiCapabilities?.ssid.orEmpty()
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.extraInfo.orEmpty()
            else wifiManager?.connectionInfo?.ssid.takeIf { it?.isNotBlank() == true && !it.equals("<unknown ssid>", true) } ?: run {
                wifiManager?.configuredNetworks?.firstOrNull { it.networkId == wifiManager.connectionInfo?.networkId }?.SSID
            }
        } catch (e: Exception) { "" },
        isRoot = getIsRooted(),
        isSimulator = getIsSimulator(),
        battery = getSystemService<BatteryManager>()?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1,
        lastLoginTime = GlobalApplication.lastLoginTime.toString(),
        picCount = countContent(imageExternalUri, imageInternalUri),
        imsi = try {
            telephonyManager?.subscriberId?.takeIf { it.isNotBlank() } ?: "-1"
        } catch (e: Exception) { "-1" },
        mac = try {
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                NetworkInterface.getNetworkInterfaces().asSequence()
                    .filter { it.name == "wlan0" }.firstOrNull()?.hardwareAddress?.formatMac()
                else wifiManager?.connectionInfo?.macAddress)?.takeIf {
                    it.isNotBlank() && it != "02:00:00:00:00:00"
            }.orEmpty()
        } catch (e: Exception) { "" },
        sdCard = sdCardInfo.first, unUseSdCard = sdCardInfo.second,
        resolution = "${currentPoint.width() - insets.left - insets.right}x${currentPoint.height() - insets.bottom - insets.top}",
        idfa = "", idfv = "",
        ime = try {
            telephonyManager?.let { TelephonyManagerCompat.getImei(it) }
        } catch (e: Exception) { null } ?: Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty(),
        imeiHistory = try {
            val defaultSubscriptionId = telephonyManager?.let { TelephonyManagerCompat.getSubscriptionId(it) } ?: Int.MAX_VALUE
            val defaultSlotIndex = SubscriptionManagerCompat.getSlotIndex(defaultSubscriptionId)
            (getSystemService<SubscriptionManager>()?.activeSubscriptionInfoList?.mapNotNull {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) telephonyManager?.getImei(it.simSlotIndex)
                else telephonyManager?.getDeviceId(it.simSlotIndex)
            } ?: listOf((if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                telephonyManager?.getImei(SubscriptionManagerCompat.getSlotIndex(defaultSlotIndex))
            else telephonyManager?.getDeviceId(SubscriptionManagerCompat.getSlotIndex(defaultSlotIndex))).orEmpty()))
                .filter { it.isBlank() }.toTypedArray()
        } catch (e: Exception) {
            try {
                val defaultSubscriptionId = telephonyManager?.let { TelephonyManagerCompat.getSubscriptionId(it) } ?: Int.MAX_VALUE
                val defaultSlotIndex = SubscriptionManagerCompat.getSlotIndex(defaultSubscriptionId)
                listOf((if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    telephonyManager?.getImei(defaultSlotIndex)
                else telephonyManager?.getDeviceId(defaultSlotIndex)).orEmpty()).filter { it.isBlank() }.toTypedArray()
            } catch (ex: Exception) { emptyArray() } },
        brand = Build.BRAND.takeIf { it != Build.UNKNOWN }.orEmpty()
    )
}

private fun Context.getSDCardPair(): Pair<String, String> = try {
    getSDCardInfo().firstOrNull { it.isRemovable && it.state in listOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
            && /*File(it.path).exists()*/Files.exists(it.path) }?.path?.let {
        val stat = StatFs(it.absolutePathString())
        /*Formatter.formatFileSize(this, */(stat.blockSizeLong * stat.blockCountLong).toString()/*)*/ to
            /*Formatter.formatFileSize(this,*/ (stat.blockSizeLong * stat.availableBlocksLong).toString()
    } ?: ("-1" to "-1")
} catch (e: Exception) {
    "-1" to "-1"
}

private fun Context.getSDCardInfo() = mutableListOf<SDCardInfo>().apply {
    val sm = getSystemService<StorageManager>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
            addAll(sm?.storageVolumes?.mapNotNull {
                SDCardInfo(it.directoryCompat, it.state, it.isRemovable)
            } ?: emptyList())
        } catch (_: Exception) { }
    } else {
        try {
            val storageVolumeClazz =
                Class.forName("android.os.storage.StorageVolume")
            val getPathMethod =
                storageVolumeClazz.getMethod("getPath")
            val isRemovableMethod =
                storageVolumeClazz.getMethod("isRemovable")
            val getVolumeStateMethod =
                StorageManager::class.java.getMethod("getVolumeState",
                    String::class.java)
            val getVolumeListMethod =
                StorageManager::class.java.getMethod("getVolumeList")
            val result = getVolumeListMethod.invoke(sm) as Array<*>
            val length = java.lang.reflect.Array.getLength(result)
            for (i in 0 until length) {
                val storageVolumeElement = java.lang.reflect.Array.get(result, i)
                val path =
                    getPathMethod.invoke(storageVolumeElement) as String
                val isRemovable =
                    isRemovableMethod.invoke(storageVolumeElement) as Boolean
                val state =
                    getVolumeStateMethod.invoke(sm, path) as String
                add(SDCardInfo(Paths.get(path), state, isRemovable))
            }
        } catch (_: Exception) { }
    }
}

@Parcelize
private data class SDCardInfo(
    @TypeParceler<Path?, PathParceler> val path: Path?,
    val state: String,
    val isRemovable: Boolean
): Parcelable

object PathParceler: Parceler<Path?> {
    override fun create(parcel: Parcel): Path = Paths.get(parcel.readString())
    override fun Path?.write(parcel: Parcel, flags: Int) {
        parcel.writeString(this?.absolutePathString().orEmpty())
    }

}

fun Context.getImageList(): List<ImageInfo> = try {
    mutableListOf<ImageInfo>().apply {
        ContentResolverCompat.query(contentResolver, imageExternalUri, arrayOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.ImageColumns._ID else MediaStore.Images.ImageColumns.DATA,
            MediaStore.Images.ImageColumns.DISPLAY_NAME,
            MediaStore.Images.ImageColumns.DATE_TAKEN,
            MediaStore.Images.ImageColumns.DATE_ADDED,
            MediaStore.Images.ImageColumns.DATE_MODIFIED), null, null, null, null
        ).use { processCursor(it, contentResolver, imageExternalUri) }
        ContentResolverCompat.query(contentResolver, imageInternalUri, arrayOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.ImageColumns._ID else MediaStore.Images.ImageColumns.DATA,
            MediaStore.Images.ImageColumns.DISPLAY_NAME,
            MediaStore.Images.ImageColumns.DATE_TAKEN,
            MediaStore.Images.ImageColumns.DATE_ADDED,
            MediaStore.Images.ImageColumns.DATE_MODIFIED), null, null, null, null
        ).use { processCursor(it, contentResolver, imageInternalUri) }
    }
} catch (e: Exception) { emptyList() }

private fun MutableList<ImageInfo>.processCursor(cursor: Cursor?, contentResolver: ContentResolver, originalUri: Uri) = try {
    while (cursor?.moveToNext() == true) {
        cursor.getStringOrNull(cursor.getColumnIndex(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.ImageColumns._ID else MediaStore.Images.ImageColumns.DATA))?.let { id ->
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                contentResolver.openInputStream(MediaStore.setRequireOriginal(Uri.withAppendedPath(originalUri, id)))
            else Paths.get(id).inputStream(StandardOpenOption.READ))?.let { input ->
                val exif = input.toExifInterface()
                val size = try {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(input, Rect(), options)
                    options.outHeight.toString() to options.outWidth.toString()
                } catch (e: Exception) {
                    exif?.getAttribute(ExifInterface.TAG_IMAGE_LENGTH) to
                            exif?.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
                }
                add(ImageInfo(
                    name = cursor.getStringOrNull(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DISPLAY_NAME)).orEmpty(),
                    height = size.first.takeIf { it != "-1" } ?: exif?.getAttribute(ExifInterface.TAG_IMAGE_LENGTH).orEmpty(),
                    width = size.second.takeIf { it != "-1" } ?: exif?.getAttribute(ExifInterface.TAG_IMAGE_WIDTH).orEmpty(),
                    latitude = exif?.latLong?.getOrNull(0),
                    longitude = exif?.latLong?.getOrNull(1),
                    //gpsTimeStamp = exif?.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP),
                    model = exif?.getAttribute(ExifInterface.TAG_MODEL).orEmpty(),
                    saveTime = cursor.getLongOrNull(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_MODIFIED))?.toString().orEmpty(),
                    date = (cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN).takeIf { it != -1 }?.let { cursor.getLongOrNull(it) } ?:
                        exif?.getAttribute(ExifInterface.TAG_DATETIME)?.let { time: String? ->
                            LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                                .toInstant(ZonedDateTime.now().offset).toEpochMilli().toString()
                        })?.toString().orEmpty(),
                    createTime = currentNetworkTimeInstant.toEpochMilli().toString(),
                    orientation = exif?.getAttribute(ExifInterface.TAG_ORIENTATION).orEmpty(),
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
                        ?.toString().orEmpty()/* ?: attributes?.createdAtMillis?.toString().orEmpty()*/
                ))
            }
        }
    }
} catch (_: Exception) { }

/*private fun Context.getAlbs(): String = Moshi.Builder().build().adapter<List<ImageInfo>>(Types.newParameterizedType(List::class.java,
    ImageInfo::class.java)).toJson(getImageList())*/

private fun Context.getGeneralData(): GeneralData {
    val telephonyManager = getSystemService<TelephonyManager>()
    val connectivityManager = getSystemService<ConnectivityManager>()
    val locale = try {
        ConfigurationCompat.getLocales(resources.configuration).getFirstMatch(resources.assets.locales)
            ?: ConfigurationCompat.getLocales(resources.configuration).get(0)
    } catch (e: Exception) { null }
    val networkType = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) "other"//"ethernet"
            else if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) "wifi"
            else if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
                when (telephonyManager?.dataNetworkType) {
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
                    else -> "none"
                }
            }
            else "none"
        } else {
            if (connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET)
                    ?.state in listOf(NetworkInfo.State.CONNECTED, NetworkInfo.State.CONNECTING)
            ) "other"//"ethernet"
            else if (connectivityManager?.activeNetworkInfo?.isAvailable == true) {
                when (connectivityManager.activeNetworkInfo?.type) {
                    ConnectivityManager.TYPE_WIFI -> "wifi"
                    ConnectivityManager.TYPE_MOBILE -> when (connectivityManager.activeNetworkInfo?.subtype) {
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
                        else -> connectivityManager.activeNetworkInfo?.subtypeName?.let {
                            (it.equals("TD-SCDMA", true) || it
                                .equals("WCDMA", true) || it
                                .equals("CDMA2000", true))
                                .then("3g") ?: "none"
                        }

                    }
                    else -> "none"
                }
            } else "none"
        }
    } catch (e: Exception) { "none" }
    return GeneralData(
        androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty(),
        currentSystemTime = System.currentTimeMillis().toString(),
        elapsedRealtime = SystemClock.elapsedRealtime().toString(),
        gaid = GlobalApplication.gaid.orEmpty(),
        imei = try {
            telephonyManager?.let { TelephonyManagerCompat.getImei(it) }
        } catch (e: Exception) { null } ?: Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty(),
        isUsbDebug = (Settings.Global.getInt(contentResolver,
            Settings.Global.ADB_ENABLED, 0) > 0).toString(),
        isUsingProxyPort = try {
            ((!System.getProperty("http.proxyHost").isNullOrEmpty() &&
                    (System.getProperty("http.proxyPort")?.toInt() ?: -1) != -1)).toString()
        } catch (e: Exception) { false.toString() },
        isUsingVpn = try {
            (NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it?.isUp == true && it.interfaceAddresses.isNotEmpty() }
                .any { listOf("tun", "ppp", "pptp").contains(it.name) } ||
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            else connectivityManager?.allNetworks?.mapNotNull {
                    connectivityManager.getNetworkCapabilities(it)
                }?.any { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } == true).toString()
        } catch (e: Exception) { false.toString() },
        language = locale?.language.orEmpty(),
        localeDisplayLanguage = locale?.displayLanguage.orEmpty(),
        localeISO3Country = locale?.isO3Country.orEmpty(),
        localeISO3Language = locale?.isO3Language.orEmpty(),
        mac = try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .firstOrNull { it.name == "wlan0" }?.hardwareAddress?.formatMac()
                ?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }.orEmpty()
        } catch (e: Exception) { "" },
        networkOperatorName = try {
            telephonyManager?.networkOperatorName.orEmpty()
        } catch (e: Exception) { "" },
        networkType = networkType, networkTypeNew = networkType,
        phoneNumber = try {
            PhoneNumberUtils.formatNumber((if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && telephonyManager != null)
                getSystemService<SubscriptionManager>()?.getPhoneNumber(TelephonyManagerCompat.getSubscriptionId(telephonyManager))
            else telephonyManager?.line1Number)?.takeIf { telephonyManager?.simOperator?.isNotBlank() == true }.orEmpty(), Locale.getDefault().country).orEmpty()
        } catch (e: Exception) { "" },
        phoneType = try {
            telephonyManager?.phoneType ?: TelephonyManager.PHONE_TYPE_NONE
        } catch (e: Exception) { 0 },
        sensor = try {
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
        } catch (e: Exception) { emptyList() },
        timeZoneId = ZoneId.systemDefault().normalized().id,
        uptimeMillis = SystemClock.uptimeMillis().toString(),
        uuid = try { getUniquePseudoId() } catch (e: Exception) { "" }
    )
}

private fun Context.getBatteryStatus(): BatteryStatus {
    val manager = getSystemService<BatteryManager>()
    val chargeStatus = ContextCompat.registerReceiver(this, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    return BatteryStatus(
        batteryLevel = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                else chargeStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0))
            ?.let { (getBatteryCapacityByHook() * it / 100).toString() }.orEmpty(),
        batteryMax = getBatteryCapacityByHook().toString(),
        batteryPercent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            else -1,
        isAcCharge = (chargeStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            == BatteryManager.BATTERY_PLUGGED_AC).toInt(),
        isUsbCharge = (chargeStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            == BatteryManager.BATTERY_PLUGGED_USB).toInt(),
        isCharging = ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && manager?.isCharging == true) ||
            chargeStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) == BatteryManager.BATTERY_STATUS_CHARGING).toInt()
    )
}

private fun Activity.getHardwareInfo(): Hardware {
    val currentPoint = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this).bounds
    /*val currentPoint = getSystemService<WindowManager>()?.let {
        Point().apply { it.defaultDisplay.getSize(this) }
    }*/
    val insets = (ViewCompat.getRootWindowInsets(window.decorView) ?: WindowInsetsCompat.CONSUMED)
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
        model = Build.MODEL.trim { it <= ' ' }.replace("\\s*".toRegex(), ""),
        physicalSize = resources.displayMetrics.let {
            hypot((currentPoint.width()/* ?: it.widthPixels*/).toFloat() / it.xdpi, (currentPoint.height()/* ?: it.heightPixels*/).toFloat() / it.ydpi)
        }.toString(),
        productionDate = Build.TIME,
        release = Build.VERSION.RELEASE.takeIf { it != Build.UNKNOWN }.orEmpty(),
        sdkVersion = Build.VERSION.SDK_INT.toString(),
        serialNumber = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Build.getSerial().takeIf { it != Build.UNKNOWN }.orEmpty() else Build.SERIAL.takeIf { it != Build.UNKNOWN }.orEmpty()
        } catch (e: Exception) { "" }
    )
}

@SuppressLint("MissingPermission", "HardwareIds")
private fun getNetworkInfo(): Network {
    val wifiManager = appCtx.getSystemService<WifiManager>()
    val connectivityManager = appCtx.getSystemService<ConnectivityManager>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        wifiManager?.registerScanResultsCallback(Dispatchers.IO.asExecutor(), object : WifiManager.ScanResultsCallback() {
            override fun onScanResultsAvailable() = Unit
        })
    } else wifiManager?.startScan()
    return Network(
        ip = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                GlobalApplication.currentWifiLinkProperties?.linkAddresses
                    ?.firstOrNull { it.address is Inet4Address || it.address.address.size == 4 }?.address?.hostAddress.orEmpty()
                else wifiManager?.connectionInfo?.ipAddress?.let { Formatter.formatIpAddress(it) }.orEmpty()
        } catch (e: Exception) { "" },
        configuredWifi = wifiManager?.scanResults?.mapNotNull { Wifi(
            bssid = it.BSSID, mac = it.BSSID,
            name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) it.wifiSsid?.toString().orEmpty() else it.SSID,
            ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) it.wifiSsid?.toString().orEmpty() else it.SSID
        ) } ?: emptyList(),
        currentWifi = try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    GlobalApplication.currentWifiCapabilities ?: wifiManager?.connectionInfo
                else wifiManager?.connectionInfo
            val extraInfo = connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.extraInfo
            Wifi(bssid = info?.bssid.orEmpty(),
                mac = info?.bssid.orEmpty()/*(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP).then(
                    val address = NetworkInterface.getNetworkInterfaces().asSequence()
                        .firstOrNull { it.name == "wlan0" }?.hardwareAddress?.formatMac()
                        ?.takeIf { it?.isNotBlank() == true } ?: "02:00:00:00:00:00"
                ) ?: wifiManager?.connectionInfo?.macAddress*/,
                ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && extraInfo?.isNotBlank() == true) extraInfo else info?.ssid.orEmpty(),
                name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && extraInfo?.isNotBlank() == true) extraInfo else info?.ssid.orEmpty())
        } catch (e: Exception) { null },
        wifiCount = wifiManager?.scanResults?.count { it != null }
    )
}

private fun Context.getStorageInfo(): Storage {
    val internalStatfs = try {
        StatFs(Environment.getDataDirectory().path)
    } catch (e: Exception) { null }
    val externalStatfs = try {
        StatFs(Environment.getExternalStorageDirectory().path)
    } catch (e: Exception) { null }
    val memoryInfo = ActivityManager.MemoryInfo().apply {
        try {
            getSystemService<ActivityManager>()?.getMemoryInfo(this)
        } catch (_: Exception) { }
    }
    return Storage(
        appFreeMemory = try { Runtime.getRuntime().freeMemory().toString() } catch (e: Exception) { "" },
        appMaxMemory = try { Runtime.getRuntime().maxMemory().toString() } catch (e: Exception) { "" },
        appTotalMemory = try { Runtime.getRuntime().totalMemory().toString() } catch (e: Exception) { "" },
        containSd = try {
            (Environment.getExternalStorageState() in
                    listOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
                    /*EnvironmentCompat.getStorageState(Environment
                    .getExternalStorageDirectory())*/).toInt()?.toString() ?: "0"
        } catch (e: Exception) { "0" },
        extraSd = try {
            (ContextCompat.getExternalFilesDirs(this, null).filterNotNull()
                    .any { it.path.contains("extra") }).toInt()?.toString() ?: "0"
        } catch (e: Exception) { "0" },
        internalStorageTotal = try {
            (internalStatfs?.blockCountLong?:0) * (internalStatfs?.blockSizeLong?:0)
        } catch (e: Exception) { 0 },
        internalStorageUsable = try {
            (internalStatfs?.availableBlocksLong?:0) * (internalStatfs?.blockSizeLong?:0)
        } catch (e: Exception) { 0 },
        memoryCardSize = try {
            externalStatfs?.let { it.blockCountLong * it.blockSizeLong } ?: -1
        } catch (e: Exception) { -1 },
        memoryCardFreeSize = try {
            externalStatfs?.let { it.freeBlocksLong * it.blockSizeLong } ?: -1
        } catch (e: Exception) { -1 },
        memoryCardUsableSize = try {
            externalStatfs?.let { it.availableBlocksLong * it.blockSizeLong } ?: -1
        } catch (e: Exception) { -1 },
        memoryCardUsedSize = try {
            externalStatfs?.let { (it.blockCountLong - it.availableBlocksLong) * it.blockSizeLong } ?: -1
        } catch (e: Exception) { -1 },
        ramTotalSize = memoryInfo.totalMem.toString(), ramUsableSize = memoryInfo.availMem.toString()
    )
}

private fun Context.getOtherData(): OtherData = OtherData(
    dbm = if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.Q && GlobalApplication.dbm != -1) GlobalApplication.dbm.toString()
        else try {
            getSystemService<TelephonyManager>()?.allCellInfo?.dbmCompat?.toString() ?: "-1"
        } catch (e: Exception) { "-1" },
    keyboard = try {
        InputDevice.getDeviceIds().map { InputDevice.getDevice(it) }
            .firstOrNull { it?.isVirtual == false }?.keyboardType ?: 0
    } catch (e: Exception) { 0 },
    lastBootTime = (currentNetworkTimeInstant.toEpochMilli() - SystemClock.elapsedRealtimeNanos() / 1000000L).toString(),
    isRoot = getIsRooted(),
    isSimulator = getIsSimulator()
)

private fun Context.getIsRooted() = try {
    val value1 = (Build.TAGS != null) && Build.TAGS.contains("test-keys")
    val value2 = arrayOf("/system/bin/", "/system/xbin/", "/sbin/", "/system/sd/xbin/",
        "/system/bin/failsafe/", "/data/local/xbin/", "/data/local/bin/",
        "/data/local/", "/system/sbin/", "/usr/bin/", "/vendor/bin/"
    ).any { /*File(it, "su").exists()*/Paths.get(it, "su").exists(LinkOption.NOFOLLOW_LINKS) } ||
            /*File("/system/app/Superuser.apk").exists()*/Paths.get("/system/app/Superuser.apk").exists(LinkOption.NOFOLLOW_LINKS)
    val value3 = try {
        val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
        process.inputStream.use {
            val available = it.available() > 0
            process.destroy()
            available
        }
    } catch (e: Exception) { false }
    (value1 || value2 || value3).toInt()
} catch (e: Exception) { 0 }

private fun Context.getIsSimulator() = try {
    ((Build.FINGERPRINT.startsWith("google/sdk_gphone_")
                && Build.FINGERPRINT.endsWith(":user/release-keys")
                && Build.MANUFACTURER == "Google" && Build.PRODUCT.startsWith("sdk_gphone_") && Build.BRAND == "google"
                && Build.MODEL.startsWith("sdk_gphone_"))
        || getSystemService<TelephonyManager>()?.networkOperatorName?.equals("android", true) == true
        //
        || Build.FINGERPRINT.startsWith("generic")
        || Build.FINGERPRINT.startsWith("unknown")
        || Build.MODEL.contains("google_sdk")
        || Build.MODEL.contains("Emulator")
        || Build.MODEL.contains("Android SDK built for x86")
        //bluestacks
        || "QC_Reference_Phone" == Build.BOARD && !"Xiaomi".equals(Build.MANUFACTURER, ignoreCase = true) //bluestacks
        || Build.MANUFACTURER.contains("Genymotion")
        || Build.HOST=="Build2" //MSI App Player
        || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
        || Build.PRODUCT == "google_sdk"
        // another Android SDK emulator check
        || hookSystemPropertiesSimulator()).toInt()
} catch (e: Exception) { 0 }

private fun Context.getAddressBook(): List<Contact> = try { mutableListOf<Contact>().apply {
    ContentResolverCompat.query(
        contentResolver, ContactsContract.Contacts.CONTENT_URI, arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.LAST_TIME_CONTACTED,
            ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP,
            ContactsContract.Contacts.TIMES_CONTACTED,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        ), null, null, null, null).use {
        while (it?.moveToNext() == true) {
            if ((it.getIntOrNull(it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) ?: -1) > 0) {
                ContentResolverCompat.query(contentResolver,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(it.getStringOrNull(it.getColumnIndex(ContactsContract.Contacts._ID))),
                    null, null).use { phoneCursor ->
                    while (phoneCursor?.moveToNext() == true) {
                        add(Contact(
                            name = it.getStringOrNull(it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)).orEmpty(),
                            phoneNumber = phoneCursor.getStringOrNull(phoneCursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER)).orEmpty(),
                            lastTimeContacted = it.getLongOrNull(it.getColumnIndex(ContactsContract.Contacts.LAST_TIME_CONTACTED)),
                            updateTime = it.getLongOrNull(it.getColumnIndex(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)),
                            timesContacted = it.getIntOrNull(it.getColumnIndex(ContactsContract.Contacts.TIMES_CONTACTED))
                        ))
                    }
                }
            } else add(Contact(
                name = it.getStringOrNull(it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)).orEmpty(),
                phoneNumber = "",
                lastTimeContacted = it.getLongOrNull(it.getColumnIndex(ContactsContract.Contacts.LAST_TIME_CONTACTED)),
                updateTime = it.getLongOrNull(it.getColumnIndex(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP))?:0L,
                timesContacted = it.getIntOrNull(it.getColumnIndex(ContactsContract.Contacts.TIMES_CONTACTED))
            ))
        }
    }
}
} catch (e: Exception) {
    emptyList()
}

@SuppressLint("QueryPermissionsNeeded")
private fun Context.getAppList(): List<App> = try {
    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()))
    else packageManager.getInstalledPackages(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) PackageManager.MATCH_UNINSTALLED_PACKAGES else 0)).mapNotNull {
        App(
            name = try { packageManager.getApplicationLabel(it.applicationInfo).toString().orEmpty() } catch (e: Exception) { "" },
            packageName = it.packageName,
            versionCode = PackageInfoCompat.getLongVersionCode(it).toString(),
            obtainTime = Instant.now().epochSecond,
            appType = (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                it.applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0).toInt()?.toString() ?: "0",
            installTime = it.firstInstallTime,
            updateTime = it.lastUpdateTime,
            appVersion = it.versionName.orEmpty()
        )
    }
} catch (e: Exception) {
    emptyList()
}

private fun Context.getSmsList() = try {
    mutableListOf<Sms>().apply {
        ContentResolverCompat.query(contentResolver, Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS, Telephony.Sms.PERSON, Telephony.Sms.BODY, Telephony.Sms.READ,
                Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.SEEN, Telephony.Sms.STATUS,
                Telephony.Sms.SUBJECT), null, null, Telephony.Sms.DEFAULT_SORT_ORDER, null
        ).use {
            while (it?.moveToNext() == true) {
                add(Sms(
                    otherPhone = it.getStringOrNull(it.getColumnIndex(Telephony.Sms.ADDRESS)).orEmpty(),
                    content = it.getStringOrNull(it.getColumnIndex(Telephony.Sms.BODY)).orEmpty(),
                    seen = it.getIntOrNull(it.getColumnIndex(Telephony.Sms.SEEN))?:-1,
                    status = when (it.getIntOrNull(it.getColumnIndex(Telephony.Sms.STATUS))) {
                        Telephony.Sms.STATUS_NONE -> -1
                        Telephony.Sms.STATUS_COMPLETE -> 0
                        Telephony.Sms.STATUS_PENDING -> 64
                        Telephony.Sms.STATUS_FAILED -> 128
                        else -> -1 },
                    time = it.getLongOrNull(it.getColumnIndex(Telephony.Sms.DATE))?:-1L,
                    type = if (it.getIntOrNull(it.getColumnIndex(Telephony.Sms.TYPE)) != Telephony.Sms.MESSAGE_TYPE_INBOX) 2 else 1,
                    packageName = packageName,//Telephony.Sms.getDefaultSmsPackage(appCtx).orEmpty(),
                    read = it.getIntOrNull(it.getColumnIndex(Telephony.Sms.READ))?:-1,
                    subject = it.getStringOrNull(it.getColumnIndex(Telephony.Sms.SUBJECT)).orEmpty()
                ))
            }
        }
    }
} catch (e: Exception) {
    emptyList()
}

private fun Context.getCalenderList(): List<Calendar> = try {
    mutableListOf<Calendar>().apply {
        ContentResolverCompat.query(contentResolver, CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events.TITLE, CalendarContract.Events._ID, CalendarContract.Events.DTEND,
                CalendarContract.Events.DTSTART, CalendarContract.Events.DESCRIPTION), null, null, null, null).use { cursor ->
            while (cursor?.moveToNext() == true) {
                cursor.getLongOrNull(cursor.getColumnIndex(CalendarContract.Events._ID))?.let { id ->
                    add(Calendar(
                        eventTitle = cursor.getStringOrNull(cursor.getColumnIndex(CalendarContract.Events.TITLE)).orEmpty(),
                        eventId = id,
                        endTime = cursor.getLongOrNull(cursor.getColumnIndex(CalendarContract.Events.DTEND)) ?: -1L,
                        startTime = cursor.getLongOrNull(cursor.getColumnIndex(CalendarContract.Events.DTSTART)) ?: -1L,
                        des = cursor.getStringOrNull(cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)).orEmpty(),
                        reminders = mutableListOf<Reminder>().apply {
                            try {
                                CalendarContract.Reminders.query(contentResolver, id, arrayOf(CalendarContract.Reminders.METHOD,
                                        CalendarContract.Reminders.MINUTES,
                                        CalendarContract.Reminders._ID)).use {
                                    while (it?.moveToNext() == true) {
                                        add(Reminder(
                                            eventId = id,
                                            method = it.getIntOrNull(it.getColumnIndex(CalendarContract.Reminders.METHOD)),
                                            minutes = it.getIntOrNull(it.getColumnIndex(CalendarContract.Reminders.MINUTES)),
                                            reminderId = it.getLongOrNull(it.getColumnIndex(CalendarContract.Reminders._ID)))
                                        )
                                    }
                                }
                            } catch (_: Exception) { }
                        })
                    )
                }
            }
        }
    }
} catch (e: Exception) {
    emptyList()
}

@SuppressLint("DiscouragedPrivateApi")
private fun Context.getStoragePair(): Pair<Long, Long> {
    val storageManager = getSystemService<StorageManager>()
    var total = 0L
    var free = 0L
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
            val validVolumes = storageManager?.storageVolumes
                ?.filter { it.state in listOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val storageStatsManager = getSystemService<StorageStatsManager>()
                val volumesUuid = validVolumes?.mapNotNull { volume ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) volume.storageUuid
                    else try {
                        volume.directoryCompat?.let { storageManager.getUuidForPath(it.toFile()) } ?: volume.uuid.toUUIDorNull()
                    }
                    catch (_: Exception) { volume.uuid.toUUIDorNull() }
                }
                if (volumesUuid.isNullOrEmpty()) {
                    total += storageStatsManager?.getTotalBytes(StorageManager.UUID_DEFAULT) ?: 0L
                    free += storageStatsManager?.getFreeBytes(StorageManager.UUID_DEFAULT) ?: 0L
                } else volumesUuid.forEach {
                    total += storageStatsManager?.getTotalBytes(it) ?: 0L
                    free += storageStatsManager?.getFreeBytes(it) ?: 0L
                }
            } else validVolumes?.forEach {
                total += it?.directoryCompat?.fileStore()?.totalSpace ?: 0L
                free += it?.directoryCompat?.fileStore()?.usableSpace ?: 0L
            }
        } catch (e: Exception) {
            StatFs(Environment.getExternalStorageDirectory().path).let {
                total = it.blockCountLong * it.blockSizeLong
                free = it.freeBlocksLong * it.blockSizeLong
            }
        }
    } else {
        try {
            val volumeClazz = Class.forName("android.os.storage.StorageVolume")
            val getPathMethod = volumeClazz.getDeclaredMethod("getPath")
            val getVolumeList = StorageManager::class.java.getDeclaredMethod("getVolumeList")
            getVolumeList.invoke(storageManager)?.let {
                for (i in 0 until java.lang.reflect.Array.getLength(it)) {
                    val volumeFile = File(getPathMethod.invoke(java.lang.reflect.Array.get(it, i)) as String)
                    total += volumeFile.totalSpace
                    free += volumeFile.usableSpace
                }
            }
        } catch (e: Exception) {
            StatFs(Environment.getExternalStorageDirectory().path).let {
                total = it.blockCountLong * it.blockSizeLong
                free = it.freeBlocksLong * it.blockSizeLong
            }
        }
    }
    return total to free
}

private fun String?.toUUIDorNull() = try {
    UUID.fromString(this)
} catch (_: Exception) { null }

val Collection<CellInfo>.dbmCompat: Int
    get() = mapNotNull {
        (when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> it.cellSignalStrength
            it is CellInfoGsm -> it.cellSignalStrength
            it is CellInfoCdma -> it.cellSignalStrength
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && it is CellInfoTdscdma -> it.cellSignalStrength
            it is CellInfoWcdma -> it.cellSignalStrength
            it is CellInfoLte -> it.cellSignalStrength
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && it is CellInfoNr -> it.cellSignalStrength
            else -> null
        })?.dbm
    }.lastOrNull { it != if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) CellInfo.UNAVAILABLE else Int.MIN_VALUE } ?: -1

private val StorageVolume.directoryCompat: Path?
    @SuppressLint("DiscouragedPrivateApi")
    get() = Paths.get(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) directory?.absolutePath
        else try {
            val getPathMethod = javaClass.getDeclaredMethod("getPath")
            /*File(getPathMethod.invoke(this) as String)*/getPathMethod.invoke(this) as String
        } catch (_: Exception) { null })

@Throws(IOException::class)
private fun InputStream.toExifInterface(): ExifInterface? = try {
    ExifInterface(this)
} catch (e: Exception) {
    // use try catch to ignore error log in console
    null
}

@SuppressLint("PrivateApi")
private fun getBatteryCapacityByHook(): Double = try {
    Class.forName("com.android.internal.os.PowerProfile").let {
        it.getMethod("getBatteryCapacity")
            .invoke(it.getDeclaredConstructor(Context::class.java).newInstance(appCtx)) as Double
    }
} catch (e: Exception) { 0.0 }

private fun ByteArray?.formatMac(): String = try {
    takeIf { this?.size == 6 }?.joinToString(separator = ":") {
        String.format("%02x", it)
    }.orEmpty()
    /*val HEX_DIGITS = byteArrayOf(
        '0'.toByte(), '1'.toByte(), '2'.toByte(), '3'.toByte(), '4'.toByte(), '5'.toByte(),
        '6'.toByte(), '7'.toByte(), '8'.toByte(), '9'.toByte(), 'A'.toByte(), 'B'.toByte(),
        'C'.code.toByte(), 'D'.toByte(), 'E'.toByte(), 'F'.toByte()
    )
    if (this != null || this?.size?.equals(6) == true) {
        val mac = ByteArray(17)
        var p = 0

        for (i in 0 until 6) {
            val b = get(i)
            mac[p] = HEX_DIGITS[b.toInt() and 0xF0 shr 4]
            mac[p + 1] = HEX_DIGITS[b.toInt() and 0xF]

            if (i != 5) {
                mac[p + 2] = ':'.toByte()
                p += 3
            }
        }
        String(mac)
    } else ""*/
} catch (e: Exception) { "" }

@SuppressLint("HardwareIds")
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

@OptIn(ExperimentalUnsignedTypes::class)
private fun Context.getUniqueMediaDrmID(): String = try {
    MediaDrm(UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)).use {
        val widevineId = it.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(widevineId)
        md.digest().asUByteArray().joinToString("") { ubyte ->
            ubyte.toString(radix = 16).padStart(2, '0')
        }
    }
} catch (_: Exception) {
    ""
}

private fun Context.hookSystemPropertiesSimulator(): Boolean = try {
    classLoader.loadClass("android.os.SystemProperties").let {
        it.getMethod("get", String::class.java)
            .invoke(it, "ro.kernel.qemu") as String == "1"
    }
} catch (e: Exception) {
    false
} catch (t: Throwable) {
    false
}
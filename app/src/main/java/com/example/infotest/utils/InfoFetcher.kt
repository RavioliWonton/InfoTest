@file:Suppress("DEPRECATION", "HardwareIds")

package com.example.infotest.utils

import android.Manifest
import android.accounts.AccountManager
import android.app.ActivityManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.InputDevice
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresPermission
import androidx.collection.mutableObjectListOf
import androidx.core.content.getSystemService
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import androidx.core.telephony.SubscriptionManagerCompat
import androidx.core.telephony.TelephonyManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.window.core.ExperimentalWindowApi
import androidx.window.layout.WindowMetricsCalculator
import com.example.infotest.App
import com.example.infotest.Calendar
import com.example.infotest.CallRecords
import com.example.infotest.Contact
import com.example.infotest.DeviceInfo
import com.example.infotest.ExtensionModel
import com.example.infotest.GeneralData
import com.example.infotest.GlobalApplication
import com.example.infotest.ImageInfo
import com.example.infotest.MainActivity
import com.example.infotest.OtherData
import com.example.infotest.Reminder
import com.example.infotest.Sms
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import splitties.init.appCtx
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.time.ZoneId
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.walk
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateCoroutinesApi::class)
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

suspend fun ComponentActivity.createExtensionModel() = ExtensionModel(
    device = withContext(extensionCreationContext) { getDeviceInfo() },
    apps = withContext(extensionCreationContext) { getAppList() },
    contacts = withContext(extensionCreationContext) { getAddressBook() },
    calendars = withContext(extensionCreationContext) { getCalenderList() },
    sms = withContext(extensionCreationContext) { getSmsList() },
    photos = withContext(extensionCreationContext) { getImageList() },
    callLogs = withContext(extensionCreationContext) { getCallLog() }
)

@OptIn(ExperimentalPathApi::class, ExperimentalWindowApi::class)
@RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO,
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE,
    "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
private fun ComponentActivity.getDeviceInfo(): DeviceInfo {
    val wifiManager = applicationContext.getSystemService<WifiManager>()
    val telephonyManager = getSystemService<TelephonyManager>()
    val connectivityManager = applicationContext.getSystemService<ConnectivityManager>()
    val storageInfo = getStoragePair()
    val sdCardInfo = getSDCardPair()
    val metric = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this)
    val insets = (ViewCompat.getRootWindowInsets(window.decorView) ?:
        if (atLeastR) WindowInsetsCompat.toWindowInsetsCompat(getSystemService<WindowManager>()!!.currentWindowMetrics.windowInsets, window.decorView)
        else WindowInsetsCompat.CONSUMED).getInsets(WindowInsetsCompat.Type.systemBars())
    val address = {
        GlobalApplication.address ?: if (GlobalApplication.gps?.couldFetchAddress() == true) GlobalApplication.gps?.let {
            Geocoder(this).getFromLocation(it.latitude, it.longitude, 1)
        }?.firstOrNull()
        else null
    }.catchReturnNull()
    val imeiHistoryFallback = { listOfNotNull(telephonyManager?.let(TelephonyManagerCompat::getImei).takeIf { it?.isIMEIValid() == true }) }.catchEmpty()

    @RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_NUMBERS, "android.permission.READ_PRIVILEGED_PHONE_STATE", Manifest.permission.READ_SMS,
        Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_BASIC_PHONE_STATE], conditional = true)
    fun getGeneralData() = GeneralData(
            androidId = contentResolver.getAndroidID(),
            currentSystemTime = currentNetworkTimeInstant.toEpochMilli().toString(),
            elapsedRealtime = SystemClock.elapsedRealtime().toString(),
            gaid = GlobalApplication.gaid.orEmpty(),
            imei = { telephonyManager?.let(TelephonyManagerCompat::getImei).takeIf { it?.isIMEIValid() == true } }.catchReturn(contentResolver.getAndroidID()),
            isUsbDebug = (Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) > 0).toString(),
            isUsingProxyPort = {
                atLeastR && connectivityManager?.defaultProxy?.isValid == true ||
                        Settings.Global.getString(contentResolver, Settings.Global.HTTP_PROXY).isNotBlank() ||
                        (System.getProperty("http.proxyPort")?.toInt() ?: -1) != -1
            }.catchFalse().toString(),
            isUsingVpn = {
                NetworkInterface.getNetworkInterfaces().asSequence().filter { it?.isUp == true }.any { iter ->
                    listOf("tun", "ppp", "pptp").any { iter.name.startsWith(it, ignoreCase = true) } } ||
                if (atLeastN) connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                else connectivityManager?.allNetworks?.mapNotNull { connectivityManager.getNetworkCapabilities(it) }
                    ?.any { it.isValidateNetwork(NetworkCapabilities.TRANSPORT_VPN) } == true
            }.catchFalse().toString(),
            language = getSystemDefaultLocale().language.orEmpty(), localeDisplayLanguage = getSystemDefaultLocale().displayLanguage.orEmpty(),
            localeISO3Country = getSystemDefaultLocale().isO3Country.orEmpty(), localeISO3Language = getSystemDefaultLocale().isO3Language.orEmpty(),
            mac = getWifiMac(), networkType = getNetworkType(), networkTypeNew = getNetworkType(), networkOperatorName = getNetworkOperatorNameCompat(),
            phoneNumber = { PhoneNumberUtils.formatNumber(getPhoneNumber(), getSystemDefaultLocale().country) }.catchEmpty(),
            phoneType = { telephonyManager?.phoneType }.catchReturn(TelephonyManager.PHONE_TYPE_NONE),
            sensor = {
                getSystemService<SensorManager>()?.getSensorList(Sensor.TYPE_ALL)?.mapNotNull {
                    com.example.infotest.Sensor(
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
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE])
    fun getOtherData(): OtherData = OtherData(
        dbm = (if (atLeastQ && GlobalApplication.dbm != -1) GlobalApplication.dbm else getDbmCompat()).toString(),
        keyboard = {
            InputDevice.getDeviceIds().map { InputDevice.getDevice(it) }.firstOrNull { it?.isVirtual == false }?.keyboardType
        }.catchZero(),
        lastBootTime = (currentNetworkTimeInstant.toEpochMilli() - SystemClock.elapsedRealtimeNanos() / 1000000L).toString(),
        isRoot = getIsRooted(), isSimulator = getIsSimulator()
    )

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
            else Path(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath).normalize()
                .walk(PathWalkOption.INCLUDE_DIRECTORIES, PathWalkOption.FOLLOW_LINKS).count()
        }.catchZero(),
        imagesExternal = countContent(imageExternalUri), imagesInternal = countContent(imageInternalUri),
        packageName = packageName, gpsAdid = GlobalApplication.gaid.orEmpty(),
        videoExternal = countContent(videoExternalUri), videoInternal = countContent(videoInternalUri),
        deviceId = {
            getDeviceIdCompat().ifBlank { getUniqueMediaDrmID().ifBlank { getGSFId().ifBlank {
                contentResolver.getAndroidID()//getUniquePseudoId()
            } } }
        }.catchEmpty(),
        deviceInfo = Build.MODEL.takeIf { it != Build.UNKNOWN }.orEmpty(),
        osType = "android", osVersion = Build.VERSION.RELEASE.takeIf { it != Build.UNKNOWN }.orEmpty(),
        ip = { lifecycleScope.future(noExceptionContext) {
            "https://api.ipify.org".getResponseString(method = RequestMethod.GET)
        }.join() }.catchEmpty()
            /*{ Scanner(URL("https://api.ipify.org").openStream(), StandardCharsets.UTF_8.name())
                .useDelimiter("\\A").use { it.hasNext().then(it.next()) }
        }.catchEmpty()*/,
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
        gpsLatitude = GlobalApplication.gps?.latitude?.toString().orEmpty(),
        gpsLongitude = GlobalApplication.gps?.longitude?.toString().orEmpty(),
        gpsAddress = address?.getAddressLine(0).orEmpty(),
        addressInfo = address?.toString().orEmpty(),
        isWifi = (atLeastQ && connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                || connectivityManager?.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI).toInt() ?: 0,
        wifiName = {
            if (atLeastS) GlobalApplication.currentWifiCapabilities?.ssid
            else if (atLeastQ) connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.extraInfo
            else wifiManager?.connectionInfo?.ssid.takeIf { it?.isNotBlank() == true && !it.equals(UNKNOWN_SSID, true) } ?:
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
        ime = { telephonyManager?.let(TelephonyManagerCompat::getImei).takeIf { it?.isIMEIValid() == true } }.catchReturn(contentResolver.getAndroidID()),
        imeiHistory = {(
                if (atLeastU) getSystemService<SubscriptionManager>()?.allSubscriptionInfoList
                    ?.filter { SubscriptionManager.isValidSubscriptionId(it.subscriptionId) }
                    ?.mapNotNull { info -> telephonyManager?.getImeiCompat(info.simSlotIndex).takeIf { it?.isIMEIValid() == true }.orEmpty() }
                else if (atLeastR) getSystemService<SubscriptionManager>()?.completeActiveSubscriptionInfoList
                    ?.filter { SubscriptionManager.isValidSubscriptionId(it.subscriptionId) }
                    ?.mapNotNull { info -> telephonyManager?.getImeiCompat(info.simSlotIndex).takeIf { it?.isIMEIValid() == true }.orEmpty() }
                else if (atLeastQ) getSystemService<SubscriptionManager>()?.accessibleSubscriptionInfoList
                    ?.filter { SubscriptionManager.isValidSubscriptionId(it.subscriptionId) }
                    ?.mapNotNull { info -> telephonyManager?.getImeiCompat(info.simSlotIndex).takeIf { it?.isIMEIValid() == true }.orEmpty() }
                else if (atLeastLM) getSystemService<SubscriptionManager>()?.activeSubscriptionInfoList
                    ?.mapNotNull { it.simSlotIndex }?.filter { it != -1 /*SubscriptionManager.INVALID_SIM_SLOT_INDEX*/ }
                    ?.ifEmpty { listOf(SubscriptionManagerCompat.getSlotIndex(getDefaultSubscriptionId())) }
                    ?.map { telephonyManager?.getImeiCompat(it).takeIf { it?.isIMEIValid() == true }.orEmpty() }
                else imeiHistoryFallback)?.filter { it.isBlank() }?.toTypedArray()
        }.catchReturn(imeiHistoryFallback.toTypedArray())
    )
}

@Suppress("QueryPermissionsNeeded")
private fun Context.getAppList(): List<App> = {
    (if (atLeastT) packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()))
    else packageManager.getInstalledPackages(if (atLeastN) PackageManager.MATCH_UNINSTALLED_PACKAGES
    else PackageManager.GET_UNINSTALLED_PACKAGES)).mapNotNull { info -> App(
        name = { info.applicationInfo?.let { packageManager.getApplicationLabel(it).toString() }}.catchEmpty(),
        packageName = info.packageName, obtainTime = currentNetworkTimeInstant.epochSecond,
        versionCode = PackageInfoCompat.getLongVersionCode(info).toString(),
        appType = info.applicationInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM != 0 || it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0).toInt()?.toString() } ?: "0",
        installTime = info.firstInstallTime, updateTime = info.lastUpdateTime, appVersion = info.versionName.orEmpty())
    }
}.catchEmpty()

@RequiresPermission.Read(RequiresPermission(Manifest.permission.READ_CONTACTS))
private fun Context.getAddressBook(): List<Contact> = { mutableObjectListOf<Contact>().apply {
    contentResolver.queryAll(contentUri = ContactsContract.Contacts.CONTENT_URI, projection = arrayOf(
        ContactsContract.Contacts.LAST_TIME_CONTACTED, ContactsContract.Contacts.DISPLAY_NAME,
        ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP, ContactsContract.Contacts._ID,
        ContactsContract.Contacts.TIMES_CONTACTED, ContactsContract.Contacts.HAS_PHONE_NUMBER
    )).use { while (it?.moveToNext() == true) {
            val contact = Contact(
                name = it.getStringOrNull(it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)).orEmpty(),
                phoneNumber = "", timesContacted = it.getIntOrNull(it.getColumnIndex(ContactsContract.Contacts.TIMES_CONTACTED)),
                lastTimeContacted = it.getLongOrNull(it.getColumnIndex(ContactsContract.Contacts.LAST_TIME_CONTACTED)),
                updateTime = it.getLongOrNull(it.getColumnIndex(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)))
            if ((it.getIntOrNull(it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) ?: -1) > 0)
                contentResolver.queryAll(contentUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    selection = arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID),
                    selectionArgs = arrayOf(it.getStringOrNull(it.getColumnIndex(ContactsContract.Contacts._ID)))
                ).use { phoneCursor -> while (phoneCursor?.moveToNext() == true)
                    add(contact.copy(phoneNumber = phoneCursor.getStringOrNull(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)).orEmpty()))
                }
            else add(contact)
        }
        }
    }.asList()
}.catchEmpty()

@RequiresPermission.Read(RequiresPermission(anyOf = [Manifest.permission.READ_CALENDAR, "com.coloros.permission.READ_COLOROS_CALENDAR"], conditional = true))
@RequiresPermission(Manifest.permission.GET_ACCOUNTS)
private fun Context.getCalenderList(): List<Calendar> = {
    mutableObjectListOf<Calendar>().apply {
        fun getEventsFromStore(authority: String = CalendarContract.Events.CONTENT_URI.authority!!,
                               projection: Array<String>? = arrayOf(CalendarContract.Events.TITLE,
                                   CalendarContract.Events._ID, CalendarContract.Events.DTEND,
                                   CalendarContract.Events.DTSTART, CalendarContract.Events.DESCRIPTION),
                               selection: Array<String?>? = emptyArray(), selectionArgs: Array<String?>? = emptyArray(),
                               addCondition: (id: Long) -> Boolean = { true }) =
            contentResolver.queryAll(contentUri = CalendarContract.Events.CONTENT_URI.buildUpon().authority(authority).build(),
                projection = projection, selection = selection, selectionArgs = selectionArgs).use { cursor ->
                while (cursor?.moveToNext() == true) cursor.getLongOrNull(cursor.getColumnIndex(CalendarContract.Events._ID))?.let { id ->
                    if (addCondition.invoke(id)) add(Calendar(eventId = id,
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
                        }
                    ))
                }
            }
        AccountManager.get(applicationContext)?.accounts?.forEach { account ->
            contentResolver.queryAll(contentUri = CalendarContract.Calendars.CONTENT_URI, projection = arrayOf(CalendarContract.Calendars._ID),
                selection = arrayOf(CalendarContract.CALLER_IS_SYNCADAPTER, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE),
                selectionArgs = arrayOf(true.toString(), account.name, account.type), sortOrder = CalendarContract.Calendars.DEFAULT_SORT_ORDER
            ).use { calendar -> if (calendar?.moveToNext() == true) getEventsFromStore(
                selection = arrayOf(CalendarContract.Events.CALENDAR_ID),
                selectionArgs = arrayOf(calendar.getLongOrNull(calendar.getColumnIndex(CalendarContract.Calendars._ID)).toString()))
            }
        }
        getEventsFromStore(addCondition = { id -> count { it.eventId == id } == 0 })
        if (isColorOS() && (packageManager.getPackageInfoCompat("com.coloros.calendar")?.versionCode ?: -1) >= 7001000)
            getEventsFromStore(authority = "com.coloros.calendar")
    }.asList()
}.catchEmpty()

@RequiresPermission.Read(RequiresPermission(Manifest.permission.READ_SMS))
private fun Context.getSmsList(): List<Sms> = {
    mutableObjectListOf<Sms>().apply {
        contentResolver.queryAll(contentUri = Telephony.Sms.CONTENT_URI, projection = arrayOf(
            Telephony.Sms.ADDRESS, Telephony.Sms.PERSON, Telephony.Sms.BODY, Telephony.Sms.READ,
            Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.SEEN, Telephony.Sms.STATUS,
            Telephony.Sms.SUBJECT), sortOrder = Telephony.Sms.DEFAULT_SORT_ORDER).use {
        while (it?.moveToNext() == true) add(Sms(
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
            subject = it.getStringOrNull(it.getColumnIndex(Telephony.Sms.SUBJECT)).orEmpty()))
        }
    }.asList()
}.catchEmpty()

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

@RequiresPermission.Read(RequiresPermission(Manifest.permission.READ_CALL_LOG))
private fun Context.getCallLog(): List<CallRecords> = {
    mutableObjectListOf<CallRecords>().apply {
        contentResolver.queryAll(contentUri = CallLog.Calls.CONTENT_URI, projection = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.COUNTRY_ISO, CallLog.Calls.DURATION,
            CallLog.Calls.FEATURES, CallLog.Calls.TYPE, CallLog.Calls.GEOCODED_LOCATION).apply {
                if (atLeastN) plus(CallLog.Calls.VIA_NUMBER)
                if (atLeastS) plus(CallLog.Calls.LOCATION)
            }, sortOrder = CallLog.Calls.DEFAULT_SORT_ORDER
        ).use { while (it?.moveToNext() == true) add(CallRecords(
            id = it.getLongOrNull(it.getColumnIndex(CallLog.Calls._ID)) ?: -1L,
            number = it.getStringOrNull(it.getColumnIndex(CallLog.Calls.NUMBER)).orEmpty(),
            date = it.getLongOrNull(it.getColumnIndex(CallLog.Calls.DATE)) ?: -1L,
            duration = it.getLongOrNull(it.getColumnIndex(CallLog.Calls.DURATION)) ?: -1L,
            features = it.getIntOrNull(it.getColumnIndex(CallLog.Calls.FEATURES)) ?: -1,
            type = it.getIntOrNull(it.getColumnIndex(CallLog.Calls.TYPE)) ?: -1,
            countryIso = it.getStringOrNull(it.getColumnIndex(CallLog.Calls.COUNTRY_ISO)).orEmpty(),
            geocodedLocation = it.getStringOrNull(it.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)).orEmpty()
        ).also { callRecord -> if (atLeastS && it.getStringOrNull(it.getColumnIndex(CallLog.Calls.LOCATION)) != null)
            contentResolver.queryAll(contentUri = it.getStringOrNull(it.getColumnIndex(CallLog.Calls.LOCATION))!!.toUri().normalizeScheme(),
                projection = arrayOf(CallLog.Locations.LATITUDE, CallLog.Locations.LONGITUDE)).use { location ->
                    callRecord.latitude = location.getSingleDouble(CallLog.Locations.LATITUDE)
                    callRecord.longitude = location.getSingleDouble(CallLog.Locations.LONGITUDE)
                }
            else if (atLeastN) callRecord.caller = it.getStringOrNull(it.getColumnIndex(CallLog.Calls.VIA_NUMBER)).orEmpty()
            }
        )}
    }.asList()
}.catchEmpty()

val serializer by lazy {
    Json {
        isLenient = true
        prettyPrint = true
        explicitNulls = false
        encodeDefaults = true
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
        allowSpecialFloatingPointValues = true
    }
}

@RequiresPermission.Write(RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, conditional = true))
fun String?.saveFileToDownload(fileName: String, contentResolver: ContentResolver = appCtx.contentResolver) {
    val buffer = ByteBuffer.wrap(this.orEmpty().encodeToByteArray()).asReadOnlyBuffer()
    if (atLeastQ) {
        val model = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        contentResolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), model)?.let { uri ->
            contentResolver.openAssetFileDescriptor(uri, "w")?.createOutputStream()?.use { stream ->
                //stream.channel.write(ByteBuffer.allocate(1024).also { it.put(this.orEmpty().encodeToByteArray()) })
                stream.channel.write(buffer)
                stream.fd.sync()
            }
            model.clear()
            model.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, model, null, null)
        }
    } else FileChannel.open(Path(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
        fileName), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use {
        it.write(buffer)
        it.force(false)
    }
}
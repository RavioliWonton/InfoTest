package com.example.infotest

import android.os.Parcel
import android.os.Parcelable
import androidx.collection.MutableObjectList
import androidx.collection.ObjectList
import androidx.core.os.ParcelCompat
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

@Serializable
@Parcelize
data class ExtensionModel(
    /**Device info*/
    @SerialName(value = "device_info") val device: DeviceInfo? = null,
    /**Contact info*/
    @SerialName(value = "address_book") val contacts: List<Contact>? = null,
    /**App info*/
    @SerialName(value = "app_list") val apps: List<App>? = null,
    /**Sms Info*/
    @SerialName(value = "sms") val sms: List<Sms>? = null,
    /**Calendar info*/
    @SerialName(value = "calendar_list") val calendars: List<Calendar>? = null,

    @SerialName(value = "photoInfos") val photos: List<ImageInfo>? = null,

    @SerialName(value = "call_log") val callLogs: List<CallRecords>? = null
) : Parcelable

@Suppress("unused")
class ObjectListParceler<P: Parcelable>(private val type: KClass<P>): Parceler<ObjectList<P>?> {
    override fun create(parcel: Parcel): ObjectList<P> = MutableObjectList<P>().apply {
        ParcelCompat.readParcelableArrayTyped(parcel, ClassLoader.getSystemClassLoader(), type.javaObjectType)?.mapNotNull { type.safeCast(it) }?.let { addAll(it) }
    }
    override fun ObjectList<P>?.write(parcel: Parcel, flags: Int) {
        parcel.writeParcelableArray(arrayListOf<P>().apply {
            forEach { add(it) }
        }.toArray(arrayOf()), flags)
    }
}

@Serializable
@Parcelize
data class DeviceInfo(
    /**轨迹跟踪记录, 抓取所有的图片，然后抓取图片内的信息*/
    @SerialName(value = "albs") val albs: String?,
    /**音频外部文件个数*/
    @SerialName(value = "audio_external") val audioExternal: Int?,
    /**音频内部文件个数*/
    @SerialName(value = "audio_internal") val audioInternal: Int?,
    /**电量信息*/
    @SerialName(value = "battery_status") val batteryStatus: BatteryStatus?,
    /**设备基本信息*/
    @SerialName(value = "general_data") val generalData: GeneralData?,
    /**设备硬件信息*/
    @SerialName(value = "hardware") val hardware: Hardware?,
    /**网路信息*/
    @SerialName(value = "network") val network: Network?,
    /**其他信息*/
    @SerialName(value = "other_data") val otherData: OtherData?,
    /**存储信息*/
    @SerialName(value = "new_storage") val newStorage: Storage?,
    /**应用版本号对应的技术编码*/
    @SerialName(value = "build_id") val buildId: String?,
    /**APP版本号*/
    @SerialName(value = "build_name") val buildName: String?,
    /**联系人小组个数*/
    @SerialName(value = "contact_group") val contactGroup: Int?,
    /**抓取时间*/
    @SerialName(value = "create_time") val createTime: String?,
    /**下载的文件个数*/
    @SerialName(value = "download_files") val downloadFiles: Int?,
    /**图片外部文件个数*/
    @SerialName(value = "images_external") val imagesExternal: Int?,
    /**图片内部文件个数*/
    @SerialName(value = "images_internal") val imagesInternal: Int?,
    /**包名*/
    @SerialName(value = "package_name") val packageName: String?,
    /**视频外部文件个数*/
    @SerialName(value = "video_external") val videoExternal: Int?,
    /**视频内部文件个数*/
    @SerialName(value = "video_internal") val videoInternal: Int?,
    /**GPS_ADID*/
    @SerialName(value = "gps_adid") val gpsAdid: String?,
    /**设备ID 1.能取到imei传imei 2.取不到imei传安卓ID 3.都没有,传机构自己的设备id,请尽量保证顺序*/
    @SerialName(value = "device_id") val deviceId: String?,
    /**设备信息*/
    @SerialName(value = "device_info") val deviceInfo: String?,
    /**设备系统类型 仅支持 android/ios*/
    @SerialName(value = "os_type") val osType: String?,
    /**设备系统版本*/
    @SerialName(value = "os_version") val osVersion: String?,
    /**设备ip地址*/
    @SerialName(value = "ip") val ip: String?,
    /**内存大小，获取不到传 -1*/
    @SerialName(value = "memory") val memory: String?,
    /**存储空间大小，获取不到传 -1*/
    @SerialName(value = "storage") val storage: String?,
    /**存储空间大小，获取不到传 -*/
    @SerialName(value = "unuse_storage") val unUseStorage: String?,
    /**经度*/
    @SerialName(value = "gps_longitude") val gpsLongitude: String?,
    /**纬度*/
    @SerialName(value = "gps_latitude") val gpsLatitude: String?,
    /**gps地址*/
    @SerialName(value = "gps_address") val gpsAddress: String?,
    /**详细地址*/
    @SerialName(value = "address_info") val addressInfo: String?,
    /**是否wifi 0不是 1是*/
    @SerialName(value = "wifi") val isWifi: Int?,
    /**wifi名称 获取不到传 -1*/
    @SerialName(value = "wifi_name") val wifiName: String?,
    /**电量*/
    @SerialName(value = "battery") val battery: Int?,
    /**是否root 0未root 1已root*/
    @SerialName(value = "is_root") val isRoot: Int?,
    /**是否是模拟器 0不是模拟器 1是模拟器*/
    @SerialName(value = "is_simulator") val isSimulator: Int?,
    /**上次活跃时间戳*/
    @SerialName(value = "last_login_time") val lastLoginTime: String?,
    /**图片数量，等于外部图片加内部图片，获取不到传-1*/
    @SerialName(value = "pic_count") val picCount: Int?,
    /**sim卡串号*/
    @SerialName(value = "imsi") val imsi: String?,
    /**mac地址 获取不到传-1*/
    @SerialName(value = "mac") val mac: String?,
    /**SD卡存储空间大小，获取不到传-1*/
    @SerialName(value = "sdcard") val sdCard: String?,
    /**SD卡未使用存储空间大小，获取不到传-1*/
    @SerialName(value = "unuse_sdcard") val unUseSdCard: String?,
    /**ios系统idfv*/
    @SerialName(value = "idfv") val idfv: String?,
    /**ios系统idfa*/
    @SerialName(value = "idfa") val idfa: String?,
    @SerialName(value = "imei_history") val imeiHistory: Array<String>?,
    /**安卓系统imei，获取不到传-1*/
    @SerialName(value = "imei") val ime: String?,
    /**屏幕分辨率*/
    @SerialName(value = "resolution") val resolution: String?,
    /**品牌*/
    @SerialName(value = "brand") val brand: String?
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeviceInfo

        if (albs != other.albs) return false
        if (audioExternal != other.audioExternal) return false
        if (audioInternal != other.audioInternal) return false
        if (batteryStatus != other.batteryStatus) return false
        if (generalData != other.generalData) return false
        if (hardware != other.hardware) return false
        if (network != other.network) return false
        if (otherData != other.otherData) return false
        if (newStorage != other.newStorage) return false
        if (buildId != other.buildId) return false
        if (buildName != other.buildName) return false
        if (contactGroup != other.contactGroup) return false
        if (createTime != other.createTime) return false
        if (downloadFiles != other.downloadFiles) return false
        if (imagesExternal != other.imagesExternal) return false
        if (imagesInternal != other.imagesInternal) return false
        if (packageName != other.packageName) return false
        if (videoExternal != other.videoExternal) return false
        if (videoInternal != other.videoInternal) return false
        if (gpsAdid != other.gpsAdid) return false
        if (deviceId != other.deviceId) return false
        if (deviceInfo != other.deviceInfo) return false
        if (osType != other.osType) return false
        if (osVersion != other.osVersion) return false
        if (ip != other.ip) return false
        if (memory != other.memory) return false
        if (storage != other.storage) return false
        if (unUseStorage != other.unUseStorage) return false
        if (gpsLongitude != other.gpsLongitude) return false
        if (gpsLatitude != other.gpsLatitude) return false
        if (gpsAddress != other.gpsAddress) return false
        if (addressInfo != other.addressInfo) return false
        if (isWifi != other.isWifi) return false
        if (wifiName != other.wifiName) return false
        if (battery != other.battery) return false
        if (isRoot != other.isRoot) return false
        if (isSimulator != other.isSimulator) return false
        if (lastLoginTime != other.lastLoginTime) return false
        if (picCount != other.picCount) return false
        if (imsi != other.imsi) return false
        if (mac != other.mac) return false
        if (sdCard != other.sdCard) return false
        if (unUseSdCard != other.unUseSdCard) return false
        if (idfv != other.idfv) return false
        if (idfa != other.idfa) return false
        if (imeiHistory != null) {
            if (other.imeiHistory == null) return false
            if (!imeiHistory.contentEquals(other.imeiHistory)) return false
        } else if (other.imeiHistory != null) return false
        if (ime != other.ime) return false
        if (resolution != other.resolution) return false
        return brand == other.brand
    }

    override fun hashCode(): Int {
        var result = albs?.hashCode() ?: 0
        result = 31 * result + (audioExternal ?: 0)
        result = 31 * result + (audioInternal ?: 0)
        result = 31 * result + (batteryStatus?.hashCode() ?: 0)
        result = 31 * result + (generalData?.hashCode() ?: 0)
        result = 31 * result + (hardware?.hashCode() ?: 0)
        result = 31 * result + (network?.hashCode() ?: 0)
        result = 31 * result + (otherData?.hashCode() ?: 0)
        result = 31 * result + (newStorage?.hashCode() ?: 0)
        result = 31 * result + (buildId?.hashCode() ?: 0)
        result = 31 * result + (buildName?.hashCode() ?: 0)
        result = 31 * result + (contactGroup ?: 0)
        result = 31 * result + (createTime?.hashCode() ?: 0)
        result = 31 * result + (downloadFiles ?: 0)
        result = 31 * result + (imagesExternal ?: 0)
        result = 31 * result + (imagesInternal ?: 0)
        result = 31 * result + (packageName?.hashCode() ?: 0)
        result = 31 * result + (videoExternal ?: 0)
        result = 31 * result + (videoInternal ?: 0)
        result = 31 * result + (gpsAdid?.hashCode() ?: 0)
        result = 31 * result + (deviceId?.hashCode() ?: 0)
        result = 31 * result + (deviceInfo?.hashCode() ?: 0)
        result = 31 * result + (osType?.hashCode() ?: 0)
        result = 31 * result + (osVersion?.hashCode() ?: 0)
        result = 31 * result + (ip?.hashCode() ?: 0)
        result = 31 * result + (memory?.hashCode() ?: 0)
        result = 31 * result + (storage?.hashCode() ?: 0)
        result = 31 * result + (unUseStorage?.hashCode() ?: 0)
        result = 31 * result + (gpsLongitude?.hashCode() ?: 0)
        result = 31 * result + (gpsLatitude?.hashCode() ?: 0)
        result = 31 * result + (gpsAddress?.hashCode() ?: 0)
        result = 31 * result + (addressInfo?.hashCode() ?: 0)
        result = 31 * result + (isWifi ?: 0)
        result = 31 * result + (wifiName?.hashCode() ?: 0)
        result = 31 * result + (battery ?: 0)
        result = 31 * result + (isRoot ?: 0)
        result = 31 * result + (isSimulator ?: 0)
        result = 31 * result + (lastLoginTime?.hashCode() ?: 0)
        result = 31 * result + (picCount ?: 0)
        result = 31 * result + (imsi?.hashCode() ?: 0)
        result = 31 * result + (mac?.hashCode() ?: 0)
        result = 31 * result + (sdCard?.hashCode() ?: 0)
        result = 31 * result + (unUseSdCard?.hashCode() ?: 0)
        result = 31 * result + (idfv?.hashCode() ?: 0)
        result = 31 * result + (idfa?.hashCode() ?: 0)
        result = 31 * result + (imeiHistory?.contentHashCode() ?: 0)
        result = 31 * result + (ime?.hashCode() ?: 0)
        result = 31 * result + (resolution?.hashCode() ?: 0)
        result = 31 * result + (brand?.hashCode() ?: 0)
        return result
    }
}

@Serializable
@Parcelize
data class Contact(
    @SerialName(value = "contact_display_name") val name: String?,

    @SerialName(value = "number") val phoneNumber: String?,

    @SerialName(value = "up_time") val updateTime: Long?,

    @SerialName(value = "last_time_contacted") val lastTimeContacted: Long?,

    @SerialName(value = "times_contacted") val timesContacted: Int?,
) : Parcelable

@Serializable
@Parcelize
data class App(
    @SerialName(value = "app_name") val name: String?,

    @SerialName(value = "package_name") val packageName: String?,

    @SerialName(value = "version_code") val versionCode: String?,

    @SerialName(value = "obtain_time") val obtainTime: Long?,

    @SerialName(value = "app_type") val appType: String?,

    @SerialName(value = "in_time") val installTime: Long?,

    @SerialName(value = "up_time") val updateTime: Long?,

    @SerialName(value = "app_version") val appVersion: String?,
) : Parcelable

@Serializable
@Parcelize
data class Sms(
    @SerialName(value = "other_phone") val otherPhone: String?,

    @SerialName(value = "content") val content: String?,
    /**0-未读，1-已读*/
    @SerialName(value = "seen") val seen: Int?,
    /**短信状态：-1表示接收，0-complete，64-pending，128-failed*/

    @SerialName(value = "read") val read: Int?,

    @SerialName(value = "subject") val subject: String?,

    @SerialName(value = "status") val status: Int?,

    @SerialName(value = "time") val time: Long?,

    @SerialName(value = "type") val type: Int?,

    @SerialName(value = "package_name") val packageName: String?,
) : Parcelable

@Serializable
@Parcelize
data class Calendar(
    @SerialName(value = "event_title") val eventTitle: String?,

    @SerialName(value = "event_id") val eventId: Long?,

    @SerialName(value = "end_time") val endTime: Long?,

    @SerialName(value = "start_time") val startTime: Long?,

    @SerialName(value = "description") val des: String?,

    @SerialName(value = "reminders") val reminders: List<Reminder>?
) : Parcelable

@Serializable
@Parcelize
data class BatteryStatus(
    /**电池电量 e.g. 2730(传的数据不用带单位)mAH*/
    @SerialName(value = "battery_level") val batteryLevel: String?,
    /**电池总电量 e.g. 3900(传的数据不用带单位)mAH*/
    @SerialName(value = "battery_max") val batteryMax: String?,
    /**电池百分比*/
    @SerialName(value = "battery_pct") val batteryPercent: Int?,
    /**是否交流充电 0不在 1在*/
    @SerialName(value = "is_ac_charge") val isAcCharge: Int?,
    /**是否正在充电 0不在 1在*/
    @SerialName(value = "is_charging") val isCharging: Int?,
    /**是否usb充电 0不在 1在*/
    @SerialName(value = "is_usb_charge") val isUsbCharge: Int?
) : Parcelable

@Serializable
@Parcelize
data class GeneralData(
    /**Android id*/
    @SerialName(value = "and_id") val androidId: String?,
    /**设备当前时间*/
    @SerialName(value = "currentSystemTime") val currentSystemTime: String?,
    /**开机时间到现在的毫秒数（包括睡眠时间）*/
    @SerialName(value = "elapsedRealtime") val elapsedRealtime: String?,
    /**google advertising id(google 广告 id)*/
    @SerialName(value = "gaid") val gaid: String?,
    /**imei*/
    @SerialName(value = "imei") val imei: String?,
    /**是否开启debug调试*/
    @SerialName(value = "is_usb_debug") val isUsbDebug: String?,
    /**是否使用代理*/
    @SerialName(value = "is_using_proxy_port") val isUsingProxyPort: String?,

    @SerialName(value = "is_using_vpn") val isUsingVpn: String?,

    @SerialName(value = "language") val language: String?,

    @SerialName(value = "locale_display_language") val localeDisplayLanguage: String?,

    @SerialName(value = "locale_iso_3_country") val localeISO3Country: String?,

    @SerialName(value = "locale_iso_3_language") val localeISO3Language: String?,

    @SerialName(value = "mac") val mac: String?,

    @SerialName(value = "network_operator_name") val networkOperatorName: String?,

    @SerialName(value = "network_type") val networkType: String?,

    @SerialName(value = "network_type_new") val networkTypeNew: String?,

    @SerialName(value = "phone_number") val phoneNumber: String?,

    @SerialName(value = "phone_type") val phoneType: Int?,

    @SerialName(value = "sensor_list") val sensor: List<Sensor>?,

    @SerialName(value = "time_zone_id") val timeZoneId: String?,

    @SerialName(value = "uptimeMillis") val uptimeMillis: String?,

    @SerialName(value = "uuid") val uuid: String?
) : Parcelable

@Serializable
@Parcelize
data class Sensor(
    @SerialName(value = "maxRange") val maxRange: String?,

    @SerialName(value = "minDelay") val minDelay: String?,

    @SerialName(value = "name") val name: String?,

    @SerialName(value = "power") val power: String?,

    @SerialName(value = "resolution") val resolution: String?,

    @SerialName(value = "type") val type: String?,

    @SerialName(value = "vendor") val vendor: String?,

    @SerialName(value = "version") val version: String?
) : Parcelable

@Serializable
@Parcelize
data class Hardware(
    @SerialName(value = "board") val board: String?,

    @SerialName(value = "brand") val brand: String?,

    @SerialName(value = "cores") val cores: Int?,

    @SerialName(value = "device_height") val deviceHeight: Int?,

    @SerialName(value = "device_name") val deviceName: String?,

    @SerialName(value = "device_width") val deviceWidth: Int?,

    @SerialName(value = "model") val model: String?,

    @SerialName(value = "physical_size") val physicalSize: String?,
    /**手机出厂时间戳*/
    @SerialName(value = "production_date") val productionDate: Long?,

    @SerialName(value = "release") val release: String?,

    @SerialName(value = "sdk_version") val sdkVersion: String?,

    @SerialName(value = "serial_number") val serialNumber: String?,
) : Parcelable

@Serializable
@Parcelize
data class Network(
    @SerialName(value = "ip") val ip: String?,

    @SerialName(value = "configured_wifi") val configuredWifi: List<Wifi>?,

    @SerialName(value = "current_wifi") val currentWifi: Wifi?,

    @SerialName(value = "wifi_count") val wifiCount: Int?
) : Parcelable

@Serializable
@Parcelize
data class Wifi(
    @SerialName(value = "bssid") val bssid: String?,

    @SerialName(value = "mac") val mac: String?,

    @SerialName(value = "name") val name: String?,

    @SerialName(value = "ssid") val ssid: String?,
) : Parcelable

@Serializable
@Parcelize
data class OtherData(
    /**手机的信号强度*/
    @SerialName(value = "dbm") val dbm: String?,
    /**连接到设备的键盘种类*/
    @SerialName(value = "keyboard") val keyboard: Int?,
    /**最后一次启动时间*/
    @SerialName(value = "last_boot_time") val lastBootTime: String?,
    /**是否 root*/
    @SerialName(value = "root_jailbreak") val isRoot: Int?,

    @SerialName(value = "simulator") val isSimulator: Int?,
) : Parcelable

@Serializable
@Parcelize
data class Storage(
    @SerialName(value = "app_free_memory") val appFreeMemory: String?,

    @SerialName(value = "app_max_memory") val appMaxMemory: String?,

    @SerialName(value = "app_total_memory") val appTotalMemory: String?,

    @SerialName(value = "contain_sd") val containSd: String?,

    @SerialName(value = "extra_sd") val extraSd: String?,

    @SerialName(value = "internal_storage_total") val internalStorageTotal: Long?,

    @SerialName(value = "internal_storage_usable") val internalStorageUsable: Long?,

    @SerialName(value = "memory_card_free_size") val memoryCardFreeSize: Long?,

    @SerialName(value = "memory_card_size") val memoryCardSize: Long?,

    @SerialName(value = "memory_card_size_use") val memoryCardUsedSize: Long?,

    @SerialName(value = "memory_card_usable_size") val memoryCardUsableSize: Long?,

    @SerialName(value = "ram_total_size") val ramTotalSize: String?,

    @SerialName(value = "ram_usable_size") val ramUsableSize: String?,
) : Parcelable

@Serializable
@Parcelize
data class Reminder(
    @SerialName(value = "eventId") val eventId: Long?,

    @SerialName(value = "method") val method: Int?,

    @SerialName(value = "minutes") val minutes: Int?,

    @SerialName(value = "reminder_id") val reminderId: Long?,
) : Parcelable

@Serializable
@Parcelize
data class ImageInfo(
    val name: String,

    val author: String,

    val height: String,

    val width: String,

    /**
     * latitude，e.g. 31.2377 or 0.0 or null
     */
    val latitude: Double?,

    /**
     * longitude e.g. 121.4256 or 0.0 or null
     */
    val longitude: Double?,
    /*拍摄时间*/
    val date: String?,
    /**
     * 读取时间（当前时间）
     */
    val createTime: String,
    /**
     * 最后修改时间
     */
    val saveTime: String,
    /**
     * 创建时间
     */
    val takeTime: String,

    val orientation: String,

    val xResolution: String,

    val yResolution: String,

    @SerialName(value = "gps_altitude")
    val altitude: String,

    val gpsProcessingMethod: String,

    val lensMake: String,

    val lensModel: String,

    val focalLength: String,

    val flash: String,

    val software: String,
    /**
     * 设备型号
     */
    val model: String
) : Parcelable

@Serializable
@Parcelize
data class CallRecords(
    val id: Long,
    val number: String,
    val date: Long,
    val duration: Long,
    val features: Int,
    val type: Int,
    var caller: String? = null,
    val countryIso: String,
    val geocodedLocation: String,
    var latitude: Double? = null,
    var longitude: Double? = null
): Parcelable
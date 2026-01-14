@file:Suppress("DEPRECATION")

package com.example.infotest.utils

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.hardware.Sensor
import android.hardware.SensorManager
import android.inputmethodservice.InputMethodService
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.view.WindowManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.window.core.ExperimentalWindowApi
import androidx.window.layout.WindowMetricsCalculator
import com.example.infotest.Hardware
import java.nio.CharBuffer
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.math.hypot

@OptIn(ExperimentalWindowApi::class)
@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PRIVILEGED_PHONE_STATE"], conditional = true)
fun Activity.getHardwareInfo(): Hardware {
    val metric = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this)
    val currentPoint = metric.bounds
    val insets = (ViewCompat.getRootWindowInsets(window.decorView) ?:
        if (atLeastR) WindowInsetsCompat.toWindowInsetsCompat(getSystemService<WindowManager>()!!.currentWindowMetrics.getWindowInsets(), window.decorView) else WindowInsetsCompat.CONSUMED)
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
            @Suppress("HardwareIds")
            (if (atLeastO) Build.getSerial() else Build.SERIAL).takeIf { it != Build.UNKNOWN }
        }.catchEmpty()
    )
}

private fun Context.isUiContextCompat() = {
    if (atLeastS) isUiContext
    else unwrapUntilAnyOrNull(Activity::class.java, InputMethodService::class.java) != null
}.catchFalse()

private fun Context.getPhysicalSize() = {
    val displayMetrics = Resources.getSystem().displayMetrics
    val maximumPoint = takeIf { it.isUiContextCompat() }?.let { WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(it).bounds }
    hypot((maximumPoint?.width() ?: displayMetrics.widthPixels).toFloat() / displayMetrics.xdpi,
        (maximumPoint?.height() ?: displayMetrics.heightPixels).toFloat() / displayMetrics.ydpi)
}.catchReturnNull()

private fun Context.isPad() = (getPhysicalSize() ?: -1f) >= 7.0f ||
        (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE

@Suppress("UnusedReceiverParameter")
fun Context.getIsRooted() = { Process.myUid() == if (atLeastQ) Process.ROOT_UID else 0
    //(Build.TAGS != Build.UNKNOWN) && Build.TAGS.contains("test-keys") || false positive for custom rom
    arrayOf("/system/bin/", "/system/xbin/", "/sbin/", "/system/sd/xbin/",
            "/system/bin/failsafe/", "/data/local/xbin/", "/data/local/bin/",
            "/data/local/", "/system/sbin/", "/usr/bin/", "/vendor/bin/"
        ).anyExistNoFollowingLink("su")
    || arrayOf("/data/adb/modules", "/system/app/Superuser.apk").anyExistNoFollowingLink()
    || execCommandSize("which", "su")?.toBoolean() == true
}.catchFalse().toInt()

@RequiresPermission(anyOf = [Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_BASIC_PHONE_STATE])
fun Context.getIsSimulator() = {
    (Build.FINGERPRINT.startsWith("google/sdk_gphone_") && Build.FINGERPRINT.endsWith(":user/release-keys")
            && Build.MANUFACTURER == "Google" && Build.PRODUCT.startsWith("sdk_gphone_") && Build.BRAND == "google"
            && Build.MODEL.startsWith("sdk_gphone_"))
            || getNetworkOperatorNameCompat().equals("android", true)
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
            || Build.PRODUCT.contains("simulator") || ActivityManager.isUserAMonkey()
            // https://stackoverflow.com/a/33558819
            || ContextCompat.registerReceiver(this, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) == 0
            || arrayOf("/dev/socket/qemud", "/dev/qemu_pipe", "/system/lib/libc_malloc_debug_qemu.so", "system/lib/libc_malloc_debug_leak.so", "/sys/qemu_trace", "/system/bin/qemu-prop",
                "/dev/socket/genyd", "/dev/socket/baseband_genyd").anyExistNoFollowingLink()
            || execCommandSize("cat", "/proc/self/cgroup").toBoolean() == true
            // another Android SDK emulator check
            // https://github.com/samohyes/Anti-vm-in-android/blob/master/VM-TEST/app/src/main/java/com/example/xudong_shao/vm_test/Utils.java#L30
            || { arrayOf("init.svc.qemu", "init.svc.qemu-props", "qemu.hw.mainkeys", "qemu.sf.fake_camera", "qemu.sf.lcd_density", "ro.kernel.android.qemud", "ro.kernel.qemu.gles").any { getSystemProperty(it)?.isNotBlank() == true } }.catchFalse()
            || { getSystemProperty("ro.bootloader")?.contains("unknown", true) == true || getSystemProperty("ro.bootmode")?.contains("unknown", true) == true
                || getSystemProperty("ro.hardware")?.contains("goldfish", true) == true || getSystemProperty("ro.product.device")?.contains("generic", true) == true
                || getSystemProperty("ro.product.model")?.contains("sdk", true) == true || getSystemProperty("ro.product.name")?.contains("sdk", true) == true
                || getSystemProperty("ro.kernel.qemu").equals("1") }.catchFalse()
            || arrayOf("000000000000000", "e21833235b6eef10", "012345678912345").any { it.equals(getDeviceIdCompat(), true) }
            || arrayOf("+15555215554", "+15555215556", "+15555215558", "+15555215560", "+15555215562", "+15555215564", "+15555215566", "+15555215568",
                "+15555215570", "+15555215572", "+15555215574", "+15555215576", "+15555215578", "+15555215580", "+15555215582", "+15555215584")
                .map { PhoneNumberUtils.normalizeNumber(it) }.any {
                    { if (atLeastS) PhoneNumberUtils.areSamePhoneNumber(it, PhoneNumberUtils.normalizeNumber(getPhoneNumber()), getSystemDefaultLocale().country)
                    else PhoneNumberUtils.compare(it, PhoneNumberUtils.normalizeNumber(getPhoneNumber())) }.catchFalse()
                }
            || arrayOf("/proc/tty/drivers", "/proc/cpuinfo").any { path -> CharBuffer.allocate(1024).applyEmitException { Path(path).bufferedReader().use { it.read(this) } }
                .toString().contains("goldfish", true) }
            || { Path("/proc/self/status").bufferedReader().useLines { sequence -> sequence.any {
                it.startsWith("TracerPid", true) && it.substring("TracerPid".length + 1).toInt() > 0 } } }.catchFalse()
}.catchFalse().toInt()
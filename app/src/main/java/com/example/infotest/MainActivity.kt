package com.example.infotest

import android.Manifest
import android.location.Geocoder
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemGesturesPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.core.location.LocationCompat
import androidx.lifecycle.lifecycleScope
import com.example.infotest.ui.theme.InfoTestTheme
import com.example.infotest.utils.GetCellInfoComposeAsync
import com.example.infotest.utils.GetLocationAsyncComposable
import com.example.infotest.utils.RegisterWifiCallback
import com.example.infotest.utils.StartWifiScan
import com.example.infotest.utils.atLeastQ
import com.example.infotest.utils.atLeastT
import com.example.infotest.utils.createExtensionModel
import com.example.infotest.utils.currentNetworkTimeInstant
import com.example.infotest.utils.emitException
import com.example.infotest.utils.extensionCreationContext
import com.example.infotest.utils.lastDbmCompat
import com.example.infotest.utils.saveFileToDownload
import com.example.infotest.utils.toJson
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
    companion object {
        var text by mutableStateOf("申请权限")
    }
    private var isStartingFetch by mutableStateOf(false)
    private val permissionArray = mutableListOf(
        Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CONTACTS, Manifest.permission.READ_SMS, Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALENDAR, Manifest.permission.GET_ACCOUNTS
    )
    @RequiresApi(Build.VERSION_CODES.Q)
    private val qPermissionArray = permissionArray.plusElement(Manifest.permission.ACCESS_MEDIA_LOCATION)
        .minusElement(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val tPermissionArray = qPermissionArray.plus(arrayOf(Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.POST_NOTIFICATIONS)).minusElement(Manifest.permission.READ_EXTERNAL_STORAGE)

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*if (isNetworkAvailable())
            lifecycleScope.launch(noExceptionContext) {
                TrueTime.build().withLoggingEnabled(GlobalApplication.isDebug)
                    .withConnectionTimeout(Duration.ofMinutes(1).toMillis().toInt())
                    .withCustomizedCache(object : CacheInterface {
                        private val timeMMKV = MMKV.mmkvWithID(Constants.mmkvTimeId, MMKV.MULTI_PROCESS_MODE)
                        override fun get(key: String?, defaultValue: Long): Long = timeMMKV.getLong(key, defaultValue)
                        override fun put(key: String?, value: Long) { timeMMKV.putLong(key, value) }
                        override fun clear() { timeMMKV.clearAll() }
                    }).withNtpHost("ntp2.nim.ac.cn").initialize()
            }*/
        setContent {
            enableEdgeToEdge()

            InfoTestTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Greeting(name = text)
                    if (!isStartingFetch) {
                        val permissions = if (atLeastT) tPermissionArray
                            else if (atLeastQ) qPermissionArray
                            else permissionArray
                        val permission =
                            rememberMultiplePermissionsState(permissions = permissions) { result ->
                                result.filter { it.value }.takeUnless { it.isNotEmpty() }
                                    ?.let { ActivityCompat.finishAffinity(this) }
                            }
                        if (permission.allPermissionsGranted) {
                            StartWifiScan()
                            RegisterWifiCallback(onCapabilitiesChanged = { _, networkCapabilities ->
                                if (atLeastQ && networkCapabilities.transportInfo is WifiInfo)
                                    GlobalApplication.currentWifiCapabilities = networkCapabilities.transportInfo as WifiInfo
                            }, onLinkPropertiesChanged = { _, linkProperties -> GlobalApplication.currentWifiLinkProperties = linkProperties })
                            GetCellInfoComposeAsync { GlobalApplication.dbm = it.lastDbmCompat }
                            GetLocationAsyncComposable { location ->
                                GlobalApplication.gps = GPS(location.latitude.toString(), location.longitude.toString(),
                                    LocationCompat.getElapsedRealtimeNanos(location))
                                if (atLeastT && Geocoder.isPresent()) emitException {
                                    Geocoder(this@MainActivity).getFromLocation(location.latitude, location.longitude, 1) {
                                        GlobalApplication.address = it.firstOrNull()
                                    }
                                }
                            }
                            startFetch()
                        }
                        else LaunchedEffect(permission) { permission.launchMultiplePermissionRequest() }
                    }
                }
            }
        }
    }

    private fun startFetch() {
        if (!isStartingFetch) {
            isStartingFetch = true
            text = "开始抓取数据，先等待五秒钟以获得地理位置和GAID"
            lifecycleScope.launch(extensionCreationContext) {
                delay(5.seconds)
                text = "正在抓取……"
                createExtensionModel().toJson()
                    .saveFileToDownload("model-${currentNetworkTimeInstant.toEpochMilli()}.txt", contentResolver)
                text = "抓取完成！信息已经保存在Download文件夹，程序将在五秒钟之内关闭"
                delay(5.seconds)
                finishAndRemoveTask()
            }
        }
    }

    private fun onFinish() {
        GlobalApplication.lastLoginTime = currentNetworkTimeInstant.epochSecond
    }

    override fun onDestroy() {
        super.onDestroy()
        onFinish()
    }
}

@Composable
fun Greeting(name: String) {
    Column(verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .systemGesturesPadding()
            .fillMaxSize()) {
        Text(text = name,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentSize())
    }
}
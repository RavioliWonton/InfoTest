package com.example.infotest.utils

import android.Manifest
import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.Parcel
import android.os.Parcelable
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import androidx.collection.mutableObjectListOf
import androidx.collection.objectListOf
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.example.infotest.Storage
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.fileStore
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid

fun Context.getStorageInfo(): Storage {
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
            @Suppress("DEPRECATION")
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

@Parcelize
private data class SDCardInfo(
    val path: @WriteWith<PathParceler> Path?,
    val state: String,
    val isRemovable: Boolean
): Parcelable

object PathParceler: Parceler<Path?> {
    override fun create(parcel: Parcel): Path? = parcel.readString()?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
    override fun Path?.write(parcel: Parcel, flags: Int) {
        parcel.writeString(this?.absolutePathString().orEmpty())
    }
}

@RequiresPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
private fun Context.getSDCardInfo() = mutableObjectListOf<SDCardInfo>().applyEmitException {
    val sm = getSystemService<StorageManager>()
    if (atLeastN) (if (atLeastT && Environment.isExternalStorageManager()) sm?.storageVolumesIncludingSharedProfiles else sm?.storageVolumes)
        ?.mapNotNull { SDCardInfo(it.directoryCompat, it.state, it.isRemovable) }?.let(::addAll)
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

@RequiresPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
fun Context.getSDCardPair(): Pair<String, String> = {
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

@OptIn(ExperimentalUuidApi::class)
@WorkerThread
@Suppress("DiscouragedPrivateApi")
@RequiresPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
fun Context.getStoragePair() = runCatching {
    val storageManager = getSystemService<StorageManager>()
    if (atLeastN && storageManager != null) {
        val validVolumes = (if (atLeastT && Environment.isExternalStorageManager()) storageManager.storageVolumesIncludingSharedProfiles
            else storageManager.storageVolumes).filter { it.state in objectListOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY) }
        if (atLeastO) validVolumes.mapNotNull {
            if (atLeastS) it.storageUuid else {
                { it.directoryCompat?.toFile()?.let(storageManager::getUuidForPath) }.catchReturnNull(it.uuid.toUUIDorNull()?.toJavaUuid())
            }
        }.takeIf { it.isNotEmpty() }?.fold(0L to 0L) { (total, free), uuid ->
            (total + (getSystemService<StorageStatsManager>()?.getTotalBytes(uuid) ?: 0L)) to
                    (free + (getSystemService<StorageStatsManager>()?.getFreeBytes(uuid) ?: 0L))
        } ?: ((getSystemService<StorageStatsManager>()?.getTotalBytes(StorageManager.UUID_DEFAULT) ?: 0L) to
            (getSystemService<StorageStatsManager>()?.getFreeBytes(StorageManager.UUID_DEFAULT) ?: 0L))
        else validVolumes.fold(0L to 0L) { (total, free), volume ->
            (total + (volume?.directoryCompat?.fileStore()?.totalSpace ?: 0L)) to
                    (free + (volume?.directoryCompat?.fileStore()?.usableSpace ?: 0L))
        }
    } else {
        val getPathMethod = getClassOrNull("android.os.storage.StorageVolume")?.getDeclaredAccessibleMethod("getPath")
        StorageManager::class.java.getDeclaredAccessibleMethod("getVolumeList").invoke(storageManager, emptyArray<Any>())?.let {
            (0 until java.lang.reflect.Array.getLength(it))
                .map { index -> java.lang.reflect.Array.get(it, index) }
                .fold(0L to 0L) { (total, free), element ->
                    (getPathMethod?.invoke(element) as? String)?.toPath()?.toNioPath()
                        ?.fileStore()?.let { store -> (total + store.totalSpace) to (free + store.usableSpace) }
                        ?: (total to free)
                }
            } ?: (0L to 0L)
        }
    }.getOrElse {
        StatFs(Environment.getExternalStorageDirectory().absolutePath).let {
            it.totalBytes to it.availableBytes
        }
    }

private val StorageVolume.directoryCompat: Path?
    @Suppress("DiscouragedPrivateApi")
    get() = { Paths.get(if (atLeastR) directory?.absolutePath
    else javaClass.getDeclaredAccessibleMethod("getPath").invoke(this, emptyArray<Any>()) as? String).normalize() }.catchReturnNull()
@file:Suppress("DEPRECATION", "unused")

package com.example.infotest.utils

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.res.Resources
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.annotation.RequiresPermission
import androidx.collection.MutableObjectList
import androidx.core.app.LocaleManagerCompat
import androidx.core.content.ContentResolverCompat
import androidx.core.database.getBlobOrNull
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getFloatOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getShortOrNull
import androidx.core.database.getStringOrNull
import androidx.core.os.CancellationSignal
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.exifinterface.media.ExifInterface
import com.example.infotest.ImageInfo
import us.fatehi.pointlocation6709.Angle
import us.fatehi.pointlocation6709.parse.PointLocationParser
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.io.path.Path

@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE], conditional = true)
val imageInternalUri: Uri = if (atLeastQ) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_INTERNAL) else MediaStore.Images.Media.INTERNAL_CONTENT_URI
@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE], conditional = true)
val imageExternalUri: Uri = if (atLeastQ) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE], conditional = true)
val videoInternalUri: Uri = if (atLeastQ) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_INTERNAL) else MediaStore.Video.Media.INTERNAL_CONTENT_URI
@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE], conditional = true)
val videoExternalUri: Uri = if (atLeastQ) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE], conditional = true)
val audioInternalUri: Uri = if (atLeastQ) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_INTERNAL) else MediaStore.Audio.Media.INTERNAL_CONTENT_URI
@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.READ_EXTERNAL_STORAGE], conditional = true)
val audioExternalUri: Uri = if (atLeastQ) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

fun ContentResolver.queryAll(@RequiresPermission contentUri: Uri, projection: Array<String>? = null,
                             selection: Array<String?>? = null, selectionArgs: Array<String?>? = null,
                             sortOrder: String? = null, cancellationSignal: CancellationSignal? = null): Cursor?
= if (atLeastO) query(contentUri.let { if (it.authority == MediaStore.AUTHORITY && buildBetween(Build.VERSION_CODES.Q, Build.VERSION_CODES.P))
    @Suppress("NewApi")
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

@SafeVarargs
fun Context.countContent(@RequiresPermission vararg contentUri: Uri): Int =
    contentUri.filterNot { it == Uri.EMPTY }.fold(0) { sum, uri -> {
        contentResolver.queryAll(contentUri = uri, projection = emptyArray()).use { sum + (it?.count ?: 0) }
    }.catchReturn(sum)
}

fun Cursor?.getSingleInt(column: String) = if (this?.moveToFirst() == true)
    getIntOrNull(getColumnIndex(column)) else null
fun Cursor?.getSingleLong(column: String) = if (this?.moveToFirst() == true)
    getLongOrNull(getColumnIndex(column)) else null
fun Cursor?.getSingleString(column: String) = if (this?.moveToFirst() == true)
    getStringOrNull(getColumnIndex(column)) else null
fun Cursor?.getSingleDouble(column: String) = if (this?.moveToFirst() == true)
    getDoubleOrNull(getColumnIndex(column)) else null
fun Cursor?.getSingleFloat(column: String) = if (this?.moveToFirst() == true)
    getFloatOrNull(getColumnIndex(column)) else null
fun Cursor?.getSingleShort(column: String) = if (this?.moveToFirst() == true)
    getShortOrNull(getColumnIndex(column)) else null
fun Cursor?.getSingleBlob(column: String) = if (this?.moveToFirst() == true)
    getBlobOrNull(getColumnIndex(column)) else null

/*private fun Context.getAlbs(): String = Moshi.Builder().build().adapter<List<ImageInfo>>(Types.newParameterizedType(List::class.java,
    ImageInfo::class.java)).toJson(getImageList())*/

@RequiresPermission(anyOf = [Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.ACCESS_MEDIA_LOCATION], conditional = true)
fun MutableObjectList<ImageInfo>.processCursor(cursor: Cursor?, contentResolver: ContentResolver, originalUri: Uri): Unit = emitException {
    fun Cursor.toInput() = { getStringOrNull(getColumnIndex(if (atLeastQ) MediaStore.Images.ImageColumns._ID else MediaStore.Images.ImageColumns.DATA))?.let {
        if (atLeastQ) contentResolver.openFileDescriptor(MediaStore.setRequireOriginal(ContentUris.withAppendedId(originalUri, it.toLong())), "r")
        else ParcelFileDescriptor.open(Path(it).toAbsolutePath().toFile(), ParcelFileDescriptor.MODE_READ_ONLY)
    } }.catchReturnNull()
    fun MediaMetadataRetriever.getDate() = {
        ZonedDateTime.parse(extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE),
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).toInstant().toEpochMilli().toString()
    }.catchReturnNull({
        ZonedDateTime.parse(extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE),
            DateTimeFormatter.ofPattern("yyyy MM dd")).toInstant().toEpochMilli().toString()
    }.catchReturnNull())

    while (cursor?.moveToNext() == true) cursor.toInput()?.use { input ->
        val retriever = { MediaMetadataRetriever().apply { setDataSource(input.fileDescriptor) } }.catchReturnNull()
        val exif = { ExifInterface(input.fileDescriptor) }.catchReturnNull()
        val size = {
            if(atLeastQ && retriever != null) retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_IMAGE_WIDTH) to
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_IMAGE_HEIGHT)
            else BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFileDescriptor(input.fileDescriptor, Rect(), it) }
                .let { it.outHeight.toString() to it.outWidth.toString() }
        }.catchReturn("-1" to "-1")
        val locationFallback = { PointLocationParser.parsePointLocation(retriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)) }.catchReturnNull()
        add(ImageInfo(
            name = cursor.getStringOrNull(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DISPLAY_NAME)).orEmpty(),
            height = size.first?.takeIf { it != "-1" } ?: exif?.getAttribute(ExifInterface.TAG_IMAGE_LENGTH).orEmpty(),
            width = size.second?.takeIf { it != "-1" } ?: exif?.getAttribute(ExifInterface.TAG_IMAGE_WIDTH).orEmpty(),
            latitude = exif?.latLong?.getOrNull(0) ?: locationFallback?.latitude?.format(Angle.AngleFormat.SHORT)?.toDoubleOrNull(),
            longitude = exif?.latLong?.getOrNull(1) ?: locationFallback?.longitude?.format(Angle.AngleFormat.SHORT)?.toDoubleOrNull(),
            //gpsTimeStamp = exif?.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP),
            model = exif?.getAttribute(ExifInterface.TAG_MODEL).orEmpty(),
            saveTime = cursor.getLongOrNull(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_MODIFIED))?.toString().orEmpty(),
            date = (cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN).takeIf { it != -1 }?.let(cursor::getLongOrNull) ?:
                (exif?.getAttribute(ExifInterface.TAG_DATETIME))?.let { time ->
                    LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")).toInstant(ZonedDateTime.now().offset)
                        .toEpochMilli() })?.toString() ?: retriever?.getDate().orEmpty(),
            createTime = currentNetworkTimeInstant.toEpochMilli().toString(),
            orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)?.toString().orEmpty(),
            xResolution = exif?.getAttribute(ExifInterface.TAG_X_RESOLUTION).orEmpty(),
            yResolution = exif?.getAttribute(ExifInterface.TAG_Y_RESOLUTION).orEmpty(),
            altitude = (exif?.getAltitude(0.0).takeIf { it != 0.0 } ?: locationFallback?.altitude)?.toString().orEmpty(),
            author = exif?.getAttribute(ExifInterface.TAG_ARTIST).orEmpty(),
            lensMake = exif?.getAttribute(ExifInterface.TAG_LENS_MAKE).orEmpty(),
            lensModel = exif?.getAttribute(ExifInterface.TAG_LENS_MODEL).orEmpty(),
            focalLength = exif?.getAttribute(ExifInterface.TAG_FOCAL_LENGTH).orEmpty(),
            software = exif?.getAttribute(ExifInterface.TAG_SOFTWARE).orEmpty(),
            gpsProcessingMethod = exif?.getAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD).orEmpty(),
            flash = exif?.getAttribute(ExifInterface.TAG_FLASH).orEmpty(),
            takeTime = cursor.getLongOrNull(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_ADDED))?.toString().orEmpty()
        ))
        if (atLeastQ) retriever?.close() else retriever?.release()
    }
}

fun Context.getSystemDefaultLocale(): Locale = {
    fun LocaleListCompat?.isNullOrEmpty() = this == null || isEmpty || equals(LocaleListCompat.getEmptyLocaleList())
    (LocaleManagerCompat.getSystemLocales(this).takeIf { !it.isNullOrEmpty() }
        ?: ConfigurationCompat.getLocales(Resources.getSystem().configuration).takeIf { !it.isNullOrEmpty() }
        ?: LocaleListCompat.getDefault()).get(0)
}.catchReturn(Resources.getSystem().configuration.locale)
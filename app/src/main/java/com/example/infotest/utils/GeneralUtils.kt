@file:Suppress("DEPRECATION", "unused")

package com.example.infotest.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BaseBundle
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.AnyThread
import androidx.annotation.CheckResult
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.collection.MutableObjectList
import androidx.collection.ObjectList
import androidx.collection.emptyObjectList
import androidx.collection.mutableObjectListOf
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.rawType
import org.xmlpull.v1.XmlPullParser
import splitties.init.appCtx
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.nio.file.FileSystems
import java.nio.file.LinkOption
import java.security.MessageDigest
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Suppress("SpellCheckingInspection")
object Constants {
    const val GAIDTAG = "gaid"
    const val APPSETIDTAG = "appSetId"
    const val LASTLOGINTAG = "lastLogin"
    const val GPSTAG = "gps"
    const val DBMTAG = "dbm"
    const val GNSSTIMETAG = "gnssTime"
    const val WIFICAPABILITIESTAG = "wifiCapabilities"
    const val WIFIPROPERTIESTAG = "wifiProperties"
    const val ADDRESSTAG = "address"
    val mmkvDefaultRoute = FileSystems.getDefault().separator + "mmkv"
}

val ApplicationInfo.minSdkVersionCompat: Int
    get() = if (atLeastN) minSdkVersion else {
        { appCtx.assets.use { assetManager ->
            assetManager.openXmlResourceParser(assetManager.javaClass.getAccessibleMethod("addAssetPath", String.Companion::class.java)
                ?.invoke(assetManager, publicSourceDir) as Int, "AndroidManifest.xml").use {
                    while (it.next() != XmlPullParser.END_DOCUMENT) {
                        if (it.eventType == XmlPullParser.START_TAG && it.name.equals("uses-sdk"))
                            for (i in 0 until it.attributeCount) {
                                if (it.getAttributeNameResource(i) == android.R.attr.minSdkVersion || "minSdkVersion" == it.getAttributeName(i))
                                    it.getAttributeIntValue(i, -1)
                            }
                    }
                    -1
                }
            }
        }.catchReturn(-1)
    }


@Suppress("ObsoleteSdkInt")
@CheckResult
@ChecksSdkIntAtLeast
fun buildBetween(@IntRange(Build.VERSION_CODES.BASE.toLong(), Build.VERSION_CODES.CUR_DEVELOPMENT.toLong()) floor: Int = appCtx.applicationInfo.minSdkVersionCompat,
                 @IntRange(Build.VERSION_CODES.BASE.toLong(), Build.VERSION_CODES.CUR_DEVELOPMENT.toLong()) tail: Int = Build.VERSION_CODES.CUR_DEVELOPMENT): Boolean =
    Build.VERSION.SDK_INT in floor..tail
@ChecksSdkIntAtLeast(Build.VERSION_CODES.LOLLIPOP_MR1)
val atLeastLM = buildBetween(Build.VERSION_CODES.LOLLIPOP_MR1)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.M)
val atLeastM = buildBetween(Build.VERSION_CODES.M)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.N)
val atLeastN = buildBetween(Build.VERSION_CODES.N)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.O)
val atLeastO = buildBetween(Build.VERSION_CODES.O)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.P)
val atLeastP = buildBetween(Build.VERSION_CODES.P)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.Q)
val atLeastQ = buildBetween(Build.VERSION_CODES.Q)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.R)
val atLeastR = buildBetween(Build.VERSION_CODES.R)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
val atLeastS = buildBetween(Build.VERSION_CODES.S)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.TIRAMISU)
val atLeastT = buildBetween(Build.VERSION_CODES.TIRAMISU)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
val atLeastU = buildBetween(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)
val atLeastV = buildBetween(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@ChecksSdkIntAtLeast(Build.VERSION_CODES.BAKLAVA)
val atLeastB = buildBetween(Build.VERSION_CODES.BAKLAVA)
// Has no effect
@CheckResult
@ChecksSdkIntAtLeast
@RequiresApi(Build.VERSION_CODES.R)
fun atLeastExtension(@IntRange(Build.VERSION_CODES.R.toLong(), SdkExtensions.AD_SERVICES.toLong()) buildVersion: Int = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, extensionVersion: Int) =
    SdkExtensions.getExtensionVersion(buildVersion) >= extensionVersion

infix fun <T> Boolean.then(param: T): T? = if (this) param else null
fun Boolean?.toInt(): Int? = (this != null).then((this == true).then(1) ?: 0)
fun Int?.toBoolean(): Boolean? = (this != null).then((this!! > 0).then(true) == true)

@OptIn(ExperimentalContracts::class)
inline fun emitException(vararg neededThrowExceptions: Class<out Exception> = emptyArray(), block: () -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
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
        callsInPlace(this@catchReturn, InvocationKind.AT_MOST_ONCE)
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
        callsInPlace(this@catchReturnNull, InvocationKind.AT_MOST_ONCE)
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

@Suppress("IntentWithNullActionLaunch")
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
class ObjectListAdapter<T>(private val elementAdapter: JsonAdapter<T>) {
    @ToJson
    fun toJson(list: ObjectList<T>?) = list?.joinToString(separator = ", ", prefix = "[",
        postfix = "]", transform = { elementAdapter.toJson(it) }).orEmpty()
    @FromJson
    fun fromJson(list: Array<String?>?) = mutableObjectListOf<T>().plusAssign(list?.mapNotNull {
            element -> element?.let { elementAdapter.fromJson(it) } }.orEmpty())
}

class ObjectListAdapterCompat<T>(private val elementAdapter: JsonAdapter<T>): JsonAdapter<ObjectList<T>>() {
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
class ObjectListAdapterFactory: JsonAdapter.Factory {
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? = {
        if (type.rawType == ObjectList::class.java && type is ParameterizedType)
            (type.actualTypeArguments.firstOrNull() as Class<*>?)
                ?.let { ObjectListAdapterCompat(moshi.adapter(it)) }
        else null
    }.catchReturnNull()
}

@AnyThread
fun getClassOrNull(className: String) =
    { (Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()).loadClass(className) }.catchReturnNull()
@SafeVarargs
fun Class<*>.getAccessibleMethod(name: String, vararg parameterTypes: Class<*> = emptyArray()): Method? =
    getMethod(name, *parameterTypes).apply { isAccessible = true }
@SafeVarargs
fun Class<*>.getDeclaredAccessibleMethod(name: String, vararg parameterTypes: Class<*> = emptyArray()): Method? =
    getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
@SafeVarargs
fun Class<*>.getAccessibleConstructor(isDeclared: Boolean = false, vararg parameterTypes: Class<*> = emptyArray()): Constructor<out Any>? =
    (if (isDeclared) getDeclaredConstructor(*parameterTypes) else getConstructor(*parameterTypes)).apply { isAccessible = true }

fun getSystemProperty(key: String, defaultValue: String? = "") = getClassOrNull("android.os.SystemProperties")
    ?.getAccessibleMethod("get", String::class.java, String::class.java)?.invoke(null, key, defaultValue) as String?

@SafeVarargs
fun execCommand(command: String, vararg arguments: String = emptyArray(), environment: Array<String> = emptyArray()) = {
    Runtime.getRuntime().exec(arrayOf("sh", command).plus(arguments), environment).inputStream
}.catchReturnNull()
@SafeVarargs
fun execCommandSize(command: String, vararg arguments: String = emptyArray()) = {
    execCommand(command, *arguments).use { it?.available() }
}.catchReturnNull()

fun PackageManager.getPackageInfoCompat(packageName: String, packageInfoFlags: Int = 0) = try {
    if (atLeastT) getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(packageInfoFlags.toLong()))
    else getPackageInfo(packageName, packageInfoFlags)
} catch (e: PackageManager.NameNotFoundException) { null }

fun getMessageDigestInstanceOrNull(algorithm: String) = { MessageDigest.getInstance(algorithm) }.catchReturnNull()

@OptIn(ExperimentalUuidApi::class)
fun String?.toUUIDorNull() = { Uuid.parse(this.orEmpty()) }.catchReturnNull()

fun BaseBundle.getBooleanCompat(key: String, defaultValue: Boolean = false) = {
    if (atLeastLM) getBoolean(key, defaultValue)
    else get(key) as Boolean? ?: defaultValue
}.catchReturn(defaultValue)
fun BaseBundle.getBooleanArrayCompat(key: String, defaultValue: BooleanArray = booleanArrayOf()) = {
    (if (atLeastLM) getBooleanArray(key) else get(key) as BooleanArray?) ?: defaultValue
}.catchReturn(defaultValue)

@Suppress("NewApi")
@SafeVarargs
fun Array<String>?.anyExistNoFollowingLink(vararg subPath: String = arrayOf()): Boolean = anyExist(option = LinkOption.NOFOLLOW_LINKS, subPath = subPath)
@SafeVarargs
fun Array<String>?.anyExist(option: LinkOption? = null, vararg subPath: String = arrayOf()): Boolean =
    this?.any { { if (option!= null) Path(it, *subPath).exists(option) else Path(it, *subPath).exists() }.catchFalse() } == true

tailrec fun <T: Context> Context.unwrapUntil(condition: Context.() -> T?): T? = when {
    this.condition() != null -> this.condition()
    this is ContextWrapper -> baseContext.unwrapUntil(condition)
    else -> null
}
@SafeVarargs
fun Context.unwrapUntilAnyOrNull(vararg type: Class<out Context>) =
    unwrapUntil { if (type.any { it.isInstance(this) }) this@unwrapUntilAnyOrNull else null }
fun Context.findActivity() = unwrapUntil { takeIf { it is Activity && !it.isDestroyed } as Activity }

/*fun String?.retrace(mappingFile: File, isVerbose: Boolean = true) = run {
    Writer.nullWriter().buffered().use {
        ReTrace(ReTrace.REGULAR_EXPRESSION, ReTrace.REGULAR_EXPRESSION, false, isVerbose, mappingFile)
            .retrace(LineNumberReader(this.orEmpty().reader()), PrintWriter(it, true))
        it.toString()
    }
}*/
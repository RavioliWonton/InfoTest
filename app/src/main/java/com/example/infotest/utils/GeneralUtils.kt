@file:Suppress("DEPRECATION", "unused")

package com.example.infotest.utils

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BaseBundle
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.ext.SdkExtensions
import androidx.annotation.AnyThread
import androidx.annotation.CheckResult
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.collection.MutableObjectList
import androidx.collection.ObjectList
import androidx.collection.emptyObjectList
import androidx.collection.objectListOf
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.time.TrustedTimeClient
import kotlinx.coroutines.tasks.await
import org.xmlpull.v1.XmlPullParser
import splitties.init.appCtx
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
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

@Suppress("UNCHECKED_CAST")
private object SettingCache {
    val SECURE_SET by lazy {
        { getClassOrNull("android.provider.Settings\$Global")?.let {
            it.getAccessibleField("MOVED_TO_SECURE", true)?.get(it) as? HashSet<String>
        } }.catchReturnNull()
    }
    val LOCK_SET by lazy {
        { getClassOrNull("android.provider.Settings\$Secure")?.let {
            it.getAccessibleField("MOVED_TO_LOCK_SETTINGS", true)?.get(it) as? HashSet<String>
        } }.catchReturnNull()
    }
    val GLOBAL_SET by lazy {
        { getClassOrNull("android.provider.Settings\$Secure")?.let {
            it.getAccessibleField("MOVED_TO_GLOBAL", true)?.get(it) as? HashSet<String>
        } }.catchReturnNull()
    }
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
/*class ObjectListAdapter<T>(private val elementAdapter: JsonAdapter<T>) {
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
}*/

@AnyThread
fun getClassOrNull(className: String) =
    { (Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()).loadClass(className) }.catchReturnNull()
@SafeVarargs
@Throws(NoSuchMethodException::class)
fun Class<*>.getAccessibleMethod(name: String, vararg parameterTypes: Class<*> = emptyArray()): Method? =
    getMethod(name, *parameterTypes).apply { isAccessible = true }
@SafeVarargs
@Throws(NoSuchMethodException::class)
fun Class<*>.getDeclaredAccessibleMethod(name: String, vararg parameterTypes: Class<*> = emptyArray()): Method? =
    getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
@SafeVarargs
fun Class<*>.hasMethod(isDeclared: Boolean, name: String, vararg parameterTypes: Class<*> = emptyArray()): Boolean = {
    (if (isDeclared) getDeclaredMethod(name, *parameterTypes) else getMethod(name, *parameterTypes)) != null
}.catchFalse()
@Throws(NoSuchFieldException::class)
fun Class<*>.getAccessibleField(name: String, isDeclared: Boolean = false): Field? =
    (if (isDeclared) getDeclaredField(name) else getField(name)).apply { isAccessible = true }
@SafeVarargs
@Throws(NoSuchMethodException::class)
fun Class<*>.getAccessibleConstructor(isDeclared: Boolean = false, vararg parameterTypes: Class<*> = emptyArray()): Constructor<out Any>? =
    (if (isDeclared) getDeclaredConstructor(*parameterTypes) else getConstructor(*parameterTypes)).apply { isAccessible = true }

fun Any.getTransactionIdViaReflection(name: String): Int = {
    javaClass.enclosingClass?.getAccessibleField(name, true)?.get(this) as Int?
}.catchZero()
fun Any.getInterfaceDescriptorViaReflection(): String = {
    javaClass.getDeclaredAccessibleMethod("getInterfaceDescriptor")?.invoke(this, emptyArray<Any>()) as String?
}.catchEmpty()

fun getSystemProperty(key: String, defaultValue: String? = "") = getClassOrNull("android.os.SystemProperties")
    ?.getAccessibleMethod("get", String::class.java, String::class.java)?.invoke(null, key, defaultValue) as String?
fun getSystemPropertyWithFallback(key: String, defaultValue: String? = "") = getSystemProperty(key, defaultValue).takeIf { defaultValue?.isNotBlank() == true && it?.isNotBlank() == true }
    ?: { execCommand("getprop $key")?.use { it.bufferedReader().readLine() } }.catchReturnNull(defaultValue)

@SafeVarargs
fun execCommand(command: String, vararg arguments: String = emptyArray(), environment: Array<String> = emptyArray()) = {
    Runtime.getRuntime().exec(arrayOf("sh", command).plus(arguments), environment).inputStream
}.catchReturnNull()
@SafeVarargs
fun execCommandSize(command: String, vararg arguments: String = emptyArray()) = {
    execCommand(command, *arguments).use { it?.available() }
}.catchReturnNull()

fun PackageManager.getPackageInfoCompat(packageName: String, packageInfoFlags: Int = 0) = {
    if (atLeastT) getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(packageInfoFlags.toLong()))
    else getPackageInfo(packageName, packageInfoFlags)
}.catchReturnNull()

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

fun isColorOS() = objectListOf("ONEPLUS", "OPPO", "REALME").any { Build.MANUFACTURER.equals(it, true) } ||
        getSystemProperty("ro.build.version.opporom")?.isNotBlank() == true

@OptIn(ExperimentalContracts::class)
suspend inline fun <reified R> TrustedTimeClient.use(block: suspend (TrustedTimeClient) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    try {
        return block(this)
    } catch (e: Throwable) {
        throw e
    } finally {
        this.dispose().await()
    }
}

fun ContentResolver.getSecureSettingStringForUser(name: String, userId: Int = getUserIdByReflection(), defaultValue: String? = null) = {
    getClassOrNull("android.provider.Settings\$Secure")?.getDeclaredAccessibleMethod("getStringForUser",
        ContentResolver::class.java, String::class.java, Int::class.java)
        ?.invoke(null, this, name, userId) as String? }.catchReturnNull(defaultValue)

fun ContentResolver.getSecureSettingStringForUserViaLowLevel(name: String, userId: Int = getUserIdByReflection(), defaultValue: String? = null) = {
    if (SettingCache.GLOBAL_SET?.contains(name) == true) getClassOrNull("android.provider.Settings\$Global")
        ?.getDeclaredAccessibleMethod("getStringForUser", ContentResolver::class.java, String::class.java, Int::class.java)
        ?.invoke(null, this, name, userId) as String?
    else if (SettingCache.LOCK_SET?.contains(name) == true) getClassOrNull("com.android.internal.widget.ILockSettings\$Stub")
        ?.getDeclaredAccessibleMethod("asInterface", IBinder::class.java)
        ?.invoke(null, getClassOrNull("android.os.ServiceManager")?.getDeclaredAccessibleMethod("getService", String::class.java)
            ?.invoke(null, "lock_settings"))?.takeIf { Process.myUid() != Process.SYSTEM_UID }?.javaClass?.getDeclaredAccessibleMethod("getString",
            String::class.java, String::class.java, Int::class.java)?.invoke(null, name, defaultValue, userId) as String?
    else getClassOrNull("android.provider.Settings\$Secure")
        ?.getAccessibleField("sNameValueCache", true)?.get(null)?.javaClass
        ?.getDeclaredAccessibleMethod("getStringForUser", ContentResolver::class.java, String::class.java, Int::class.java)
        ?.invoke(null, this, name, userId) as String?
}.catchReturnNull(defaultValue)

fun getUserIdByReflection() = { getClassOrNull("android.os.UserHandle")
    ?.getDeclaredAccessibleMethod("getUserId", Int::class.java)
    ?.invoke(null, Process.myUid()
        .takeIf { it != if (atLeastQ) Process.INVALID_UID else -1 } ?:
        if (atLeastQ) Process.ROOT_UID else Process.SYSTEM_UID) as Int? }.catchReturn(0)

/*fun String?.retrace(mappingFile: File, isVerbose: Boolean = true) = run {
    Writer.nullWriter().buffered().use {
        ReTrace(ReTrace.REGULAR_EXPRESSION, ReTrace.REGULAR_EXPRESSION, false, isVerbose, mappingFile)
            .retrace(LineNumberReader(this.orEmpty().reader()), PrintWriter(it, true))
        it.toString()
    }
}*/
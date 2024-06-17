package com.example.infotest.utils

import android.net.http.HttpEngine
import android.net.http.HttpException
import android.net.http.UploadDataProvider
import android.net.http.UploadDataSink
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresExtension
import androidx.core.net.TrafficStatsCompat
import com.getkeepsafe.relinker.ReLinker
import com.google.android.gms.net.CronetProviderInstaller
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.Buffer
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.ExperimentalCronetEngine
import splitties.init.appCtx
import java.io.IOException
import java.lang.IllegalStateException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val DEFAULT_BUFFER_SIZE = 16 * 1024;

enum class RequestMethod {
    GET,
    POST
}

@delegate:RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
private val httpEngine by lazy(LazyThreadSafetyMode.PUBLICATION) {
    HttpEngine.Builder(appCtx)
        .setEnableHttp2(true)
        .setEnableQuic(true)
        .setEnableHttpCache(HttpEngine.Builder.HTTP_CACHE_DISABLED, 0L)
        .build()
}
private val cronetEngine by lazy(LazyThreadSafetyMode.PUBLICATION) {
    ExperimentalCronetEngine.Builder(appCtx)
        .enableHttp2(true)
        .enableSdch(true)
        .enableBrotli(true)
        .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISABLED, 0L)
        .setLibraryLoader(object : CronetEngine.Builder.LibraryLoader() {
            override fun loadLibrary(path: String?) {
                ReLinker.recursively().loadLibrary(appCtx, path)
            }
        })
        .build() as ExperimentalCronetEngine
}
val moshi by lazy(LazyThreadSafetyMode.PUBLICATION) { Moshi.Builder().build() }

suspend inline fun <reified T> String.getResponseObject(method: RequestMethod = RequestMethod.POST,
                                                        encoding: Charset = StandardCharsets.UTF_8,
                                                        headers: Map<String, String> = mapOf(),
                                                        data: String? = null) =
    getResponseObject<T>(method, encoding, headers, data?.toByteArray(encoding))
@OptIn(ExperimentalStdlibApi::class)
suspend inline fun <reified T> String.getResponseObject(method: RequestMethod = RequestMethod.POST,
                                         encoding: Charset = StandardCharsets.UTF_8,
                                         headers: Map<String, String> = mapOf(),
                                         data: ByteArray? = null) =
    moshi.adapter<T>().fromJson(getResponseString(method, encoding, headers, data))
suspend fun String.getResponseString(method: RequestMethod = RequestMethod.POST,
                                     encoding: Charset = StandardCharsets.UTF_8,
                                     headers: Map<String, String> = mapOf(),
                                     data: ByteArray? = null) =
    getResponseBuffer(method, headers, data).readString(encoding)
suspend fun String.getResponseBuffer(method: RequestMethod = RequestMethod.POST,
                                     headers: Map<String, String> = mapOf(),
                                     data: ByteArray? = null) =
    if (atLeastU || atLeastS && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7)
        getResponseBufferHttpEngine(method.name, headers, data)
    else if (CronetProviderInstaller.isInstalled()) getResponseBufferCronet(method.name, headers, data)
    else getResponseBufferHttpUrlConnection(method.name, headers, data)

@RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
private suspend fun String.getResponseBufferHttpEngine(method: String, headers: Map<String, String>, data: ByteArray? = null) = suspendCancellableCoroutine<Buffer> { cont ->
    httpEngine.newUrlRequestBuilder(this, Dispatchers.IO.asExecutor(), object : UrlRequest.Callback {
        private val buffer = Buffer()
        override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) =
            request.followRedirect()
        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) =
            request.read(ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE))
        override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
            byteBuffer.flip()
            buffer.write(byteBuffer)
            byteBuffer.clear()
            request.read(byteBuffer)
        }
        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) =
            cont.resume(buffer)
        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: HttpException) =
            cont.resumeWithException(error)
        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) = cont.cancel() as Unit
    }).setHttpMethod(method).setCacheDisabled(true).apply {
        headers.forEach { addHeader(it.key, it.value) }
        data?.let {
            setUploadDataProvider(object : UploadDataProvider() {
                override fun getLength() = it.size.toLong()
                override fun read(sink: UploadDataSink, buffer: ByteBuffer) {
                    buffer.put(it)
                    sink.onReadSucceeded(false)
                }
                override fun rewind(sink: UploadDataSink) = sink.onRewindSucceeded()
            }, Dispatchers.IO.asExecutor())
        }
    }.build().start()
}
private suspend fun String.getResponseBufferCronet(method: String, headers: Map<String, String>, data: ByteArray? = null) = suspendCancellableCoroutine<Buffer> { cont ->
    cronetEngine.newUrlRequestBuilder(this, object : org.chromium.net.UrlRequest.Callback() {
        private val buffer = Buffer()
        override fun onRedirectReceived(request: org.chromium.net.UrlRequest?,
                                        info: org.chromium.net.UrlResponseInfo?,
                                        newLocationUrl: String?) = request?.followRedirect() ?: Unit
        override fun onResponseStarted(request: org.chromium.net.UrlRequest?,
                                       info: org.chromium.net.UrlResponseInfo?) =
            request?.read(ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE)) ?: Unit
        override fun onReadCompleted(request: org.chromium.net.UrlRequest?,
                                     info: org.chromium.net.UrlResponseInfo?,
                                     byteBuffer: ByteBuffer?) = byteBuffer?.let {
            it.flip()
            buffer.write(it)
            it.clear()
            request?.read(it)
        } ?: Unit
        override fun onSucceeded(request: org.chromium.net.UrlRequest?,
                                 info: org.chromium.net.UrlResponseInfo?) =
            cont.resume(buffer)
        override fun onFailed(request: org.chromium.net.UrlRequest?,
                              info: org.chromium.net.UrlResponseInfo?,
                              error: CronetException?) = cont.resumeWithException(error ?: IllegalStateException())
    }, Dispatchers.IO.asExecutor()).setHttpMethod(method).disableCache().apply {
        headers.forEach { addHeader(it.key, it.value) }
        data?.let {
            setUploadDataProvider(object : org.chromium.net.UploadDataProvider() {
                override fun getLength() = it.size.toLong()
                override fun read(sink: org.chromium.net.UploadDataSink?, buffer: ByteBuffer?) {
                    buffer?.put(it)
                    sink?.onReadSucceeded(false)
                }
                override fun rewind(sink: org.chromium.net.UploadDataSink?) =
                    sink?.onRewindSucceeded() ?: Unit
            }, Dispatchers.IO.asExecutor())
        }
    }.setTrafficStatsTag(TrafficStatsCompat.getThreadStatsTag()).build().start()
}
private suspend fun String.getResponseBufferHttpUrlConnection(method: String, headers: Map<String, String>, data: ByteArray? = null) =
    withContext(Dispatchers.IO) {
        with(URL(this@getResponseBufferHttpUrlConnection).openConnection() as HttpsURLConnection) {
            doInput = true
            doOutput = true
            defaultUseCaches = false
            requestMethod = method
            instanceFollowRedirects = true

            headers.forEach { addRequestProperty(it.key, it.value) }
            data?.let { outputStream.write(it) }

            connect()

            if (responseCode == HttpsURLConnection.HTTP_OK)
                return@withContext Buffer().apply { read(inputStream.readBytes()) }
            else throw IOException(errorStream.readBytes().decodeToString())
        }
    }
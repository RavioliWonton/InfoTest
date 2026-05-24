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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okio.Buffer
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.QuicOptions
import splitties.init.appCtx
import java.io.IOException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val DEFAULT_BUFFER_SIZE = 16 * 1024

enum class RequestMethod {
    GET,
    POST,
    DELETE,
    HEAD,
    PUT
}

@delegate:RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
private val httpEngine by lazy(LazyThreadSafetyMode.PUBLICATION) {
    HttpEngine.Builder(appCtx)
        .setEnableHttp2(true).setEnableQuic(true).setEnableBrotli(true)
        .setEnableHttpCache(HttpEngine.Builder.HTTP_CACHE_DISABLED, 0L)
        .build()
}
@delegate:QuicOptions.Experimental
private val cronetEngine by lazy(LazyThreadSafetyMode.PUBLICATION) {
    CronetEngine.Builder(appCtx)
        .enableHttp2(true).enableBrotli(true).enableQuic(true)
        .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISABLED, 0L)
        .setQuicOptions(QuicOptions.builder().enableTlsZeroRtt(true))
        .setLibraryLoader(object : CronetEngine.Builder.LibraryLoader() {
            override fun loadLibrary(path: String?) {
                ReLinker.recursively().loadLibrary(appCtx, path)
            }
        })
        .build()
}

suspend inline fun <reified T> String.getResponseObject(method: RequestMethod = RequestMethod.POST,
                                                        encoding: Charset = StandardCharsets.UTF_8,
                                                        headers: Map<String, String> = mapOf(),
                                                        data: String? = null) =
    getResponseObject<T>(method, encoding, headers, data?.toByteArray(encoding))
@OptIn(ExperimentalStdlibApi::class)
@Throws(SerializationException::class, IllegalArgumentException::class)
suspend inline fun <reified T> String.getResponseObject(method: RequestMethod = RequestMethod.POST,
                                                              encoding: Charset = StandardCharsets.UTF_8,
                                                              headers: Map<String, String> = mapOf(),
                                                              data: ByteArray? = null) =
    serializer.decodeFromString<T>(getResponseString(method, encoding, headers, data))
suspend fun String.getResponseString(method: RequestMethod = RequestMethod.POST,
                                     encoding: Charset = StandardCharsets.UTF_8,
                                     headers: Map<String, String> = mapOf(),
                                     data: ByteArray? = null) =
    getResponseBuffer(method, headers, data).readString(encoding)
suspend fun String.getResponseBuffer(method: RequestMethod = RequestMethod.POST,
                                     headers: Map<String, String> = mapOf(),
                                     data: ByteArray? = null) =
    if (atLeastU || atLeastS && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7)
        getResponseBufferHttpEngine(method.name.uppercase(), headers, data)
    else if (CronetProviderInstaller.isInstalled()) getResponseBufferCronet(method.name.uppercase(), headers, data)
    else getResponseBufferHttpUrlConnection(method.name.uppercase(), headers, data)

@OptIn(ExperimentalAtomicApi::class)
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
private suspend fun String.getResponseBufferHttpEngine(method: String, headers: Map<String, String>, data: ByteArray? = null) = suspendCancellableCoroutine { cont ->
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
        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) { cont.cancel() }
    }).setHttpMethod(method).setCacheDisabled(true).apply {
        headers.forEach { addHeader(it.key, it.value) }
        data?.let {
            setUploadDataProvider(object : UploadDataProvider() {
                private val position = AtomicInt(0)

                override fun getLength() = it.size.toLong()
                override fun read(sink: UploadDataSink, buffer: ByteBuffer) {
                    minOf(buffer.remaining(), it.size - position.load())
                        .takeIf { bytesToRead -> bytesToRead > 0 }
                        ?.let { bytesToRead ->
                            buffer.put(it, position.load(), bytesToRead)
                            position.addAndFetch(bytesToRead)
                        }
                    sink.onReadSucceeded(it.size - position.load() == 0)
                }
                override fun rewind(sink: UploadDataSink) {
                    position.store(0)
                    sink.onRewindSucceeded()
                }
            }, Dispatchers.IO.asExecutor())
        }
    }.build().also { request -> cont.invokeOnCancellation { request.cancel() } }.start()
}
@OptIn(ExperimentalAtomicApi::class)
@Suppress("DEPRECATION")
private suspend fun String.getResponseBufferCronet(method: String, headers: Map<String, String>, data: ByteArray? = null) = suspendCancellableCoroutine { cont ->
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
        override fun onCanceled(request: org.chromium.net.UrlRequest?, info: org.chromium.net.UrlResponseInfo?) {
            cont.cancel()
        }
    }, Dispatchers.IO.asExecutor()).setHttpMethod(method).disableCache().apply {
        headers.forEach { addHeader(it.key, it.value) }
        data?.let {
            setUploadDataProvider(object : org.chromium.net.UploadDataProvider() {
                private val position = AtomicInt(0)

                override fun getLength() = it.size.toLong()
                override fun read(sink: org.chromium.net.UploadDataSink, buffer: ByteBuffer) {
                    minOf(buffer.remaining(), it.size - position.load())
                        .takeIf { bytesToRead -> bytesToRead > 0 }
                        ?.let { bytesToRead ->
                            buffer.put(it, position.load(), bytesToRead)
                            position.addAndFetch(bytesToRead)
                        }
                    sink.onReadSucceeded(it.size - position.load() == 0)
                }
                override fun rewind(sink: org.chromium.net.UploadDataSink) {
                    position.store(0)
                    sink.onRewindSucceeded()
                }
            }, Dispatchers.IO.asExecutor())
        }
    }.setTrafficStatsTag(TrafficStatsCompat.getThreadStatsTag()).build().also { request ->
        cont.invokeOnCancellation { request.cancel() }
    }.start()
}
private suspend fun String.getResponseBufferHttpUrlConnection(method: String, headers: Map<String, String>, data: ByteArray? = null) =
    withContext(Dispatchers.IO) {
        with(URL(this@getResponseBufferHttpUrlConnection).openConnection() as HttpsURLConnection) {
            doInput = true
            doOutput = data != null
            defaultUseCaches = false
            requestMethod = method
            instanceFollowRedirects = true

            headers.forEach { addRequestProperty(it.key, it.value) }
            data?.let { outputStream.write(it) }

            connect()

            if (responseCode == HttpsURLConnection.HTTP_OK)
                return@withContext Buffer().apply { write(inputStream.readBytes()) }.also { disconnect() }
            else throw IOException(errorStream.readBytes().decodeToString().also { disconnect() })
        }
    }
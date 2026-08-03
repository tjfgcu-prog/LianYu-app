package com.lianyu.ai.network

import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import com.lianyu.ai.common.security.DeviceRequestSigner 
import okio.Buffer

/**
 * Open-source request interceptor.
 *
 * Private request signing, native white-box crypto, and server-bound integrity
 * checks are not included in the public edition. The class remains as a small
 * compatibility hook and TLS helper for forks that want to add their own policy.
 */
class RequestSecurityInterceptor(
    private val shouldSignRequest: (okhttp3.Request) -> Boolean = { false }
) : Interceptor {
    companion object {
        fun enforceTls(builder: OkHttpClient.Builder) {
            try {
                val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS)
                    .tlsVersions(okhttp3.TlsVersion.TLS_1_2, okhttp3.TlsVersion.TLS_1_3)
                    .build()
                builder.connectionSpecs(listOf(tlsSpec, ConnectionSpec.CLEARTEXT))
            } catch (_: Exception) {
                // Keep platform defaults if restricted TLS is unavailable.
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    if (!shouldSignRequest(request)) {
        return chain.proceed(request)
    }
    val signed = runCatching {
        val payload = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readByteArray()
        } ?: request.url.toString().toByteArray(Charsets.UTF_8)
        DeviceRequestSigner.sign(payload)
    }.getOrNull()

    val finalRequest = if (signed != null) {
        request.newBuilder()
            .addHeader("X-Device-Signature", signed.signature)
            .addHeader("X-Device-Key-Id", signed.keyId)
            .addHeader("X-Device-Id", signed.deviceId)
            .addHeader("X-Signature-Algorithm", DeviceRequestSigner.SIGNATURE_ALGORITHM)
            .build()
    } else {
        request
    }
    return chain.proceed(finalRequest)
    }
}

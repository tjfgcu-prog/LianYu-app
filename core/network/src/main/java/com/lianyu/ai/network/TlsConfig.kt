package com.lianyu.ai.network

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion

/** 只允许 TLS 1.2 / 1.3，不允许明文 HTTP。 */
object TlsConfig {
    fun enforceTls(builder: OkHttpClient.Builder) {
        try {
            val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS)
                .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
                .build()
            builder.connectionSpecs(listOf(tlsSpec))
        } catch (_: Exception) {
            // 受限 TLS 不可用时保持平台默认
        }
    }
}

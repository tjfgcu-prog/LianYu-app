package com.lianyu.ai.feature.localmodel

import android.content.Context
import com.lianyu.ai.domain.LocalModelProvider


class LocalModelProviderImpl(context: Context) : LocalModelProvider {

    private val appContext = context.applicationContext

    private val ggufPrefs by lazy {
        appContext.getSharedPreferences("gguf_model_prefs", Context.MODE_PRIVATE)
    }
    private val ggufModel by lazy { GgufLocalModel(appContext) }

    private fun isGgufEnabled(): Boolean =
        ggufPrefs.getBoolean("gguf_enabled", false) &&
            !ggufPrefs.getString("gguf_file_uri", null).isNullOrBlank()

    override suspend fun isAvailable(): Boolean = isGgufEnabled()

    override suspend fun generateResponse(prompt: String, context: String): String {
        val uri = ggufPrefs.getString("gguf_file_uri", null)
            ?: throw IllegalStateException("未选择 GGUF 模型文件")
        return ggufModel.generate(uri, context, prompt)
    }

    override suspend fun preloadIfEnabled() {
        if (isGgufEnabled()) {
            val uri = ggufPrefs.getString("gguf_file_uri", null)
            if (!uri.isNullOrBlank()) {
                runCatching { ggufModel.ensureLoaded(uri) }
            }
        }
    }

    override fun getModelName(): String = "GGUF (Custom)"
    override fun getModelVersion(): String = "custom"
}

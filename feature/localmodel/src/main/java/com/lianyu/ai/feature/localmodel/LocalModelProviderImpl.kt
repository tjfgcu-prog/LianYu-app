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

    // [FIX 1/6] 读取设置里保存的上下文长度，没设置过就用 8192 兜底。
    private fun getContextLength(): Int =
        ggufPrefs.getInt("gguf_context_length", 8192)

    override suspend fun isAvailable(): Boolean = isGgufEnabled()

    override suspend fun generateResponse(prompt: String, context: String): String {
        val uri = ggufPrefs.getString("gguf_file_uri", null)
            ?: throw IllegalStateException("未选择 GGUF 模型文件")
        return ggufModel.generate(uri, context, prompt, getContextLength())
    }

    override suspend fun preloadIfEnabled() {
        if (isGgufEnabled()) {
            val uri = ggufPrefs.getString("gguf_file_uri", null)
            if (!uri.isNullOrBlank()) {
                runCatching { ggufModel.ensureLoaded(uri, getContextLength()) }
            }
        }
    }

    override fun getModelName(): String = "GGUF (Custom)"
    override fun getModelVersion(): String = "custom"
}

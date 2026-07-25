package com.lianyu.ai.feature.localmodel

import android.content.Context
import com.lianyu.ai.domain.LocalModelProvider
import com.lianyu.ai.domain.ModelInfo
import com.lianyu.ai.domain.ModelState
import com.lianyu.ai.domain.ModelStatus

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
    override fun getAvailableModels(): List<ModelInfo> = emptyList()
    override fun getModelState(modelId: String): ModelState = ModelState(
        modelId = modelId, displayName = "", downloadUrl = "", expectedBytes = 0L,
        isSelected = false, status = ModelStatus.NOT_DOWNLOADED
    )
    override fun getAllModelStates(): Map<String, ModelState> = emptyMap()
    override suspend fun downloadModel(modelId: String) {}
    override suspend fun cancelDownload(modelId: String) {}
    override suspend fun enableModel(modelId: String) {}
    override suspend fun disableModel(modelId: String) {}
    override suspend fun deleteModel(modelId: String) {}
}

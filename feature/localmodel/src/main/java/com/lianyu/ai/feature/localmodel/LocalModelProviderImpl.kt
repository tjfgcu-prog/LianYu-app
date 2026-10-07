package com.lianyu.ai.feature.localmodel

import android.content.Context
import com.lianyu.ai.domain.LocalModelProvider
import com.lianyu.ai.domain.LocalModelResult
import com.lianyu.ai.domain.LocalModelStatus
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


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
        if (isGgufEnabled()) loadModel()
    }

    override fun getStatus(): LocalModelStatus = ggufModel.status

    override suspend fun loadModel(): LocalModelResult {
        val uri = ggufPrefs.getString("gguf_file_uri", null)
        if (uri.isNullOrBlank()) return LocalModelResult(false, "未选择 GGUF 模型文件")
        val startedAt = System.currentTimeMillis()
        return try {
            val didLoad = ggufModel.load(uri, getContextLength())
            val ms = System.currentTimeMillis() - startedAt
            // 只有真的执行了加载才记录，避免"已加载"时把真实耗时覆盖成 0
            if (didLoad) saveLoadRecord(true, ms, "")
            LocalModelResult(true, "加载完成", ms)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val ms = System.currentTimeMillis() - startedAt
            val reason = e.message ?: "未知错误"
            saveLoadRecord(false, ms, reason)
            LocalModelResult(false, reason, ms)
        }
    }

    override suspend fun unloadModel() {
        ggufModel.unload()
    }

    override suspend fun testModel(): LocalModelResult {
        val loadResult = loadModel()
        if (!loadResult.success) return loadResult
        val uri = ggufPrefs.getString("gguf_file_uri", null)
            ?: return LocalModelResult(false, "未选择 GGUF 模型文件")
        val startedAt = System.currentTimeMillis()
        return try {
            val text = ggufModel.generate(uri, "", "请用一句话介绍你自己。", getContextLength())
            val ms = System.currentTimeMillis() - startedAt
            if (text.isBlank()) {
                LocalModelResult(false, "模型已加载，但没有生成任何内容", ms)
            } else {
                LocalModelResult(true, "测试通过：生成 ${text.length} 字，用时 ${"%.1f".format(ms / 1000f)} 秒", ms)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LocalModelResult(false, e.message ?: "生成失败", System.currentTimeMillis() - startedAt)
        }
    }

    override fun getLastLoadRecord(): String {
        val time = ggufPrefs.getLong("gguf_last_load_time", 0L)
        if (time == 0L) return ""
        val at = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(time))
        return if (ggufPrefs.getBoolean("gguf_last_load_ok", false)) {
            val sec = ggufPrefs.getLong("gguf_last_load_ms", 0L) / 1000f
            "上次加载：成功 · 用时 ${"%.1f".format(sec)} 秒 · $at"
        } else {
            val reason = ggufPrefs.getString("gguf_last_load_reason", "") ?: ""
            "上次加载：失败 · $reason · $at"
        }
    }

    private fun saveLoadRecord(ok: Boolean, ms: Long, reason: String) {
        ggufPrefs.edit()
            .putBoolean("gguf_last_load_ok", ok)
            .putLong("gguf_last_load_ms", ms)
            .putLong("gguf_last_load_time", System.currentTimeMillis())
            .putString("gguf_last_load_reason", reason)
            .apply()
    }

    override fun getModelName(): String = "GGUF (Custom)"
    override fun getModelVersion(): String = "custom"
}

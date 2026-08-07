package com.lianyu.ai.network.tts

import android.content.Context
import com.lianyu.ai.common.SecureLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalTtsUiState(
    val status: LocalTtsUiStatus = LocalTtsUiStatus.NOT_IMPORTED,
    val hasLexicon: Boolean = false,
    val errorMessage: String? = null
)

enum class LocalTtsUiStatus {
    NOT_IMPORTED,  // 主模型/tokens 缺失
    READY,         // 文件就绪，未启用
    ENABLED,       // 已启用
    FAILED         // 已导入但加载/测试合成失败
}

/**
 * 本地离线 TTS 模型管理器：手动导入文件后的校验 / 启用 / 删除。
 * 不含任何下载逻辑——模型文件全部通过系统文件选择器手动导入
 * （见 TtsSettingsScreen.kt 里的 LocalModeCard）。
 */
class LocalTtsModelManager private constructor(private val context: Context) {

    private val appContext = context.applicationContext
    private val preferences = LocalTtsPreferences(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(LocalTtsUiState())
    val state: StateFlow<LocalTtsUiState> = _state.asStateFlow()

    init {
        scope.launch {
            preferences.isEnabled.collect { refresh() }
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val enabled = preferences.isEnabled.first()
        val ready = LocalTtsModel.isReady(appContext)
        val status = when {
            enabled && ready -> LocalTtsUiStatus.ENABLED
            ready -> LocalTtsUiStatus.READY
            else -> LocalTtsUiStatus.NOT_IMPORTED
        }
        _state.value = LocalTtsUiState(status = status, hasLexicon = LocalTtsModel.hasLexicon(appContext))
    }

    suspend fun enable() = withContext(Dispatchers.IO) {
        if (!LocalTtsModel.isReady(appContext)) {
            _state.value = LocalTtsUiState(
                status = LocalTtsUiStatus.FAILED,
                hasLexicon = LocalTtsModel.hasLexicon(appContext),
                errorMessage = "模型文件缺失，请先导入 model.onnx 和 tokens.txt"
            )
            return@withContext
        }
        val loadError = tryLoadAndTestSynthesis()
        if (loadError != null) {
            _state.value = LocalTtsUiState(
                status = LocalTtsUiStatus.FAILED,
                hasLexicon = LocalTtsModel.hasLexicon(appContext),
                errorMessage = "模型文件存在但无法加载：$loadError"
            )
            SecureLog.e(TAG, "本地 TTS 模型加载校验失败: $loadError")
            return@withContext
        }
        preferences.setEnabled(true)
        refresh()
        SecureLog.i(TAG, "本地 TTS 模型已启用")
    }

    suspend fun disable() = withContext(Dispatchers.IO) {
        preferences.setEnabled(false)
        refresh()
        SecureLog.i(TAG, "本地 TTS 模型已禁用")
    }

    suspend fun deleteModel() = withContext(Dispatchers.IO) {
        preferences.setEnabled(false)
        LocalTtsModel.delete(appContext)
        refresh()
        SecureLog.i(TAG, "已删除本地 TTS 模型文件")
    }

    private fun tryLoadAndTestSynthesis(): String? {
        var tts: com.k2fsa.sherpa.onnx.OfflineTts? = null
        return try {
            val modelPath = LocalTtsModel.modelFile(appContext).absolutePath
            val tokensPath = LocalTtsModel.tokensFile(appContext).absolutePath
            val lexiconPath = if (LocalTtsModel.hasLexicon(appContext)) {
                LocalTtsModel.lexiconFile(appContext).absolutePath
            } else ""

            val vitsConfig = com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig(
                model = modelPath,
                lexicon = lexiconPath,
                tokens = tokensPath,
                dataDir = "",
                dictDir = "",
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f
            )
            val ttsConfig = com.k2fsa.sherpa.onnx.OfflineTtsConfig(
                model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(vits = vitsConfig)
            )
            tts = com.k2fsa.sherpa.onnx.OfflineTts(assetManager = null, config = ttsConfig)
            val audio = tts.generate("你好", 0, 1.0f)
            if (audio.samples.isEmpty()) "测试合成返回了空音频（模型可能不完整）" else null
        } catch (e: Throwable) {
            e.message ?: e.javaClass.simpleName
        } finally {
            try { tts?.release() } catch (_: Exception) { }
        }
    }

    fun close() {
        scope.cancel()
    }

    companion object {
        private const val TAG = "LocalTtsModelManager"

        @Volatile
        private var instance: LocalTtsModelManager? = null

        fun getInstance(context: Context): LocalTtsModelManager {
            return instance ?: synchronized(this) {
                instance ?: LocalTtsModelManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

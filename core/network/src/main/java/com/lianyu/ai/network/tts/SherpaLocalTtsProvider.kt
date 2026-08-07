package com.lianyu.ai.network.tts

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.lianyu.ai.common.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地离线 TTS Provider - 基于 sherpa-onnx [OfflineTts]。
 * 端上运行，无需联网。模型文件需先在设置页手动导入（见 [LocalTtsModel]）。
 */
class SherpaLocalTtsProvider : TtsProviderInterface, ConfigurableTtsProvider {

    private val mutex = Mutex()
    private var offlineTts: OfflineTts? = null
    private var loadedModelMtime: Long = -1L
    private var config: TtsConfig = TtsConfig()

    override fun updateConfig(config: TtsConfig) {
        this.config = config
    }

    override suspend fun synthesize(context: Context, text: String, voiceId: String?): String? =
        withContext(Dispatchers.IO) {
            try {
                val preferences = LocalTtsPreferences(context)
                if (!preferences.isEnabled.first()) {
                    lastError = "本地 TTS 模型未启用"
                    SecureLog.w(TAG, "本地 TTS 未启用")
                    return@withContext null
                }
                if (!LocalTtsModel.isReady(context)) {
                    lastError = "模型文件缺失，请到设置页重新导入"
                    SecureLog.w(TAG, "模型文件缺失")
                    return@withContext null
                }
                val tts = getOrLoadOfflineTts(context)
                val sid = parseSid(voiceId)
                val speed = config.localTtsSpeed.coerceIn(0.5f, 2.0f)

                SecureLog.d(TAG, "合成: sid=$sid, speed=$speed, len=${text.length}")
                val audio = tts.generate(text, sid, speed)

                val outputDir = File(context.cacheDir, "tts_audio")
                outputDir.mkdirs()
                val outputFile = File(outputDir, "local_${System.currentTimeMillis()}.wav")
                LocalTtsWavWriter.writePcmToWav(audio.samples, audio.sampleRate, outputFile)

                SecureLog.i(TAG, "本地 TTS 合成成功: ${outputFile.absolutePath}")
                lastError = null
                outputFile.absolutePath
            } catch (e: Throwable) {
                lastError = e.message ?: e.javaClass.simpleName
                SecureLog.e(TAG, "本地 TTS 合成失败", e)
                null
            }
        }

    override fun getVoices(): List<TtsVoice> {
        return listOf(
            TtsVoice("speaker_0", "默认音色", "自定义", "zh-CN", "sid=0"),
            TtsVoice("__custom_sid__", "自定义 sid", "自定义", "zh-CN", "在设置页填入 sid")
        )
    }

    override suspend fun testConnection(): Boolean = true

    // ── 内部 ──

    private suspend fun getOrLoadOfflineTts(context: Context): OfflineTts {
        val currentMtime = LocalTtsModel.modelFile(context).lastModified()
        if (offlineTts != null && loadedModelMtime == currentMtime) {
            return offlineTts!!
        }
        return mutex.withLock {
            if (offlineTts != null && loadedModelMtime == currentMtime) {
                offlineTts!!
            } else {
                try { offlineTts?.release() } catch (_: Exception) {}
                val tts = createOfflineTts(context)
                offlineTts = tts
                loadedModelMtime = currentMtime
                SecureLog.i(TAG, "OfflineTts 加载成功, sampleRate=${tts.sampleRate()}")
                tts
            }
        }
    }

    private fun createOfflineTts(context: Context): OfflineTts {
        val modelPath = LocalTtsModel.modelFile(context).absolutePath
        val tokensPath = LocalTtsModel.tokensFile(context).absolutePath
        val lexiconPath = if (LocalTtsModel.hasLexicon(context)) LocalTtsModel.lexiconFile(context).absolutePath else ""

        val vitsConfig = OfflineTtsVitsModelConfig(
            model = modelPath,
            lexicon = lexiconPath,
            tokens = tokensPath,
            dataDir = "",
            dictDir = "",
            noiseScale = 0.667f,
            noiseScaleW = 0.8f,
            lengthScale = 1.0f.coerceIn(0.5f, 2.0f) / config.localTtsSpeed.coerceIn(0.5f, 2.0f)
        )
        val modelConfig = OfflineTtsModelConfig(vits = vitsConfig)
        val ttsConfig = OfflineTtsConfig(model = modelConfig)
        return OfflineTts(assetManager = null, config = ttsConfig)
    }

    /**
     * 解析 voiceId 为 sid，上限用 [TtsConfig.localTtsNumSpeakers]
     * （用户在设置页手动填的说话人数量，默认 1）夹住。
     */
    private fun parseSid(voiceId: String?): Int {
        val raw = voiceId?.removePrefix("speaker_")?.trim()
        val parsed = raw?.toIntOrNull()
        val sid = parsed ?: config.localTtsSid
        return sid.coerceIn(0, (config.localTtsNumSpeakers - 1).coerceAtLeast(0))
    }

    companion object {
        private const val TAG = "SherpaLocalTts"

        @Volatile
        var lastError: String? = null
            private set
    }
}

package com.lianyu.ai.network.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.common.TimeoutBudgets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class AndroidTtsProvider : TtsProviderInterface {
    private var tts: TextToSpeech? = null
    private val initLock = Any()
    // 标记 TTS 引擎是否真正就绪（回调 SUCCESS + 语言设置完成）。
    // 并发调用 initialize() 时，未就绪的调用方挂起等待，而非提前返回 true。
    @Volatile private var ready: Boolean = false

    override suspend fun synthesize(context: Context, text: String, voiceId: String?): String? {
        // 系统 TTS 是"边合成边播放"，没有落盘文件，因此无法像其它 provider 一样返回文件路径。
        // 用 SYSTEM_TTS_PLAYED 哨兵值表示"确实在本机播放成功了"；超时/异常/引擎报错才返回 null。
        // 这样设置页"试听"才能反映真实结果，而不是无脑显示"已播放"。
        return withTimeoutOrNull(TimeoutBudgets.TTS_SYNTH_MS) {
            val t = tts ?: return@withTimeoutOrNull null
            val deferred = CompletableDeferred<Unit>()
            val succeeded = AtomicBoolean(false)

            try {
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        succeeded.set(true)
                        deferred.complete(Unit)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { deferred.complete(Unit) }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        SecureLog.w("AndroidTTS", "speak onError code=$errorCode")
                        deferred.complete(Unit)
                    }
                })
                val attrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                t.setAudioAttributes(attrs)
                t.setSpeechRate(1.0f)
                t.setPitch(1.0f)
                val queueResult = t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
                if (queueResult != TextToSpeech.SUCCESS) {
                    SecureLog.w("AndroidTTS", "speak() queue failed, result=$queueResult")
                    return@withTimeoutOrNull null
                }
                deferred.await()
            } catch (e: Exception) {
                SecureLog.e("AndroidTTS", "speak error", e)
                return@withTimeoutOrNull null
            }
            if (succeeded.get()) SYSTEM_TTS_PLAYED else null
        }
    }

    override fun getVoices(): List<TtsVoice> = listOf(
        TtsVoice("default", "系统默认语音", "Female", "zh", "使用系统内置TTS引擎")
    )

    override suspend fun testConnection(): Boolean = true

    fun isInitialized(): Boolean = ready

    suspend fun initialize(context: Context): Boolean {
        if (ready) return true

        data class Setup(val deferred: CompletableDeferred<Unit>, val successRef: AtomicBoolean)
        val setup: Setup? = synchronized(initLock) {
            if (ready) return@synchronized null
            if (tts != null && !ready) return@synchronized null
            try {
                val deferred = CompletableDeferred<Unit>()
                val successRef = AtomicBoolean(false)
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) successRef.set(true)
                    deferred.complete(Unit)
                }
                Setup(deferred, successRef)
            } catch (e: Exception) {
                tts?.shutdown()
                tts = null
                SecureLog.e("AndroidTTS", "init setup failed", e)
                return@synchronized null
            }
        }
        if (setup == null) {
            var spins = 0
            while (!ready && spins < 100) {
                delay(50)
                spins++
            }
            return ready
        }

        runCatching { setup.deferred.await() }

        synchronized(initLock) {
            return if (setup.successRef.get() && tts != null) {
                val langResult = tts?.setLanguage(Locale.CHINESE) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    lastDiagnostic = "系统语音引擎缺少中文语音数据（结果码=$langResult）。" +
                        "请到系统设置 → 更多设置/语言与输入法 → 文字转语音(TTS)输出 → 安装/下载中文语音数据后重试；" +
                        "或换用其它语音提供商。"
                    SecureLog.w("AndroidTTS", lastDiagnostic ?: "")
                    tts?.setLanguage(Locale.getDefault())
                } else {
                    lastDiagnostic = null
                }
                ready = true
                true
            } else {
                tts?.shutdown()
                tts = null
                ready = false
                false
            }
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun release() {
        synchronized(initLock) {
            tts?.shutdown()
            tts = null
            ready = false
        }
    }

    companion object {
        const val PROVIDER_ID = "android_tts"
        const val SYSTEM_TTS_PLAYED = "system_tts_played"
        @Volatile
        var lastDiagnostic: String? = null
    }
}

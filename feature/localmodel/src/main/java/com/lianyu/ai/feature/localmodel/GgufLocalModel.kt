package com.lianyu.ai.feature.localmodel

import kotlinx.coroutines.Job
import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.nehuatl.llamacpp.LlamaHelper
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 对 llamacpp-kotlin 库的简单封装，负责加载用户选择的 .gguf 文件并生成回复。
 */
class GgufLocalModel(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun logD(msg: String) {
        try {
            val f = File(appContext.filesDir, "chatvm_debug.log")
            f.appendText("${System.currentTimeMillis()} [GgufLocalModel] $msg\n")
        } catch (_: Exception) { }
    }

    private val llmFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val llamaHelper by lazy {
        LlamaHelper(appContext.contentResolver, scope, llmFlow)
    }

    @Volatile private var loadedUri: String? = null
    @Volatile private var loadedContextLength: Int = -1

    // [FIX 1/6] contextLength 不再写死 2048，由调用方传入（来自设置里的用户选择）。
    // 同时把 contextLength 纳入"是否需要重新加载"的判断条件：
    // 之前只比较 modelUri，用户在设置里改了上下文长度也不会生效，直到换模型或重启 App。
    suspend fun ensureLoaded(modelUri: String, contextLength: Int = 8192) {
        if (loadedUri == modelUri && loadedContextLength == contextLength) {
            logD("ensureLoaded: already loaded with same contextLength, skip. uri=$modelUri")
            return
        }
        logD("ensureLoaded: begin load, uri=$modelUri, contextLength=$contextLength")
        suspendCancellableCoroutine<Unit> { cont ->
            llamaHelper.load(path = modelUri, contextLength = contextLength) { id ->
                logD("ensureLoaded: load callback fired, id=$id")
                loadedUri = modelUri
                loadedContextLength = contextLength
                if (cont.isActive) cont.resume(Unit)
            }
        }
        logD("ensureLoaded: load coroutine resumed, done")
    }

    suspend fun generate(
        modelUri: String,
        systemPrompt: String,
        userPrompt: String,
        contextLength: Int = 8192
    ): String {
        val chatMlPrompt = buildString {
            if (systemPrompt.isNotBlank()) {
                append("<|im_start|>system\n")
                append(systemPrompt)
                append("<|im_end|>\n")
            }
            append("<|im_start|>user\n")
            append(userPrompt)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
            // [FIX 5] 手动插入空的 think 块，强制关闭 Qwen3 的思考模式。
            // 这是 llama.cpp 生态里对 Qwen3 系列模型的标准做法：
            // 系统提示词里的"不要输出思考过程"只是文字要求，模型可能不理会；
            // 而预先在 assistant 回合里放一个空 <think></think>，模型会认为思考阶段已经结束，
            // 直接从后面开始生成正文，不会再吐 <think>...</think> 内容。
            append("<think>\n\n</think>\n\n")
        }
        logD("generate: called, chatMlPrompt.length=${chatMlPrompt.length}")
        ensureLoaded(modelUri, contextLength)
        logD("generate: ensureLoaded returned, about to predict")
        val builder = StringBuilder()
        return suspendCancellableCoroutine { cont ->
            lateinit var collectJob: Job
            collectJob = scope.launch {
                llmFlow.collect { event ->
                    when (event) {
                        is LlamaHelper.LLMEvent.Ongoing -> {
                            logD("event: Ongoing, word='${event.word}'")
                            builder.append(event.word)
                        }
                        is LlamaHelper.LLMEvent.Done -> {
                            logD("event: Done, totalLength=${builder.length}")
                            if (cont.isActive) cont.resume(builder.toString())
                            collectJob.cancel()
                        }
                        is LlamaHelper.LLMEvent.Error -> {
                            logD("event: Error")
                            if (cont.isActive) cont.resumeWithException(
                                IllegalStateException("GGUF 本地模型生成失败")
                            )
                            collectJob.cancel()
                        }
                        else -> {}
                    }
                }
            }
            cont.invokeOnCancellation { collectJob.cancel() }
            logD("generate: calling llamaHelper.predict()")
            scope.launch { llamaHelper.predict(chatMlPrompt) }
        }
    }
}

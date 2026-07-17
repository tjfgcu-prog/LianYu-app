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

    private suspend fun ensureLoaded(modelUri: String) {
        if (loadedUri == modelUri) {
            logD("ensureLoaded: already loaded, skip. uri=$modelUri")
            return
        }
        logD("ensureLoaded: begin load, uri=$modelUri")
        suspendCancellableCoroutine<Unit> { cont ->
            llamaHelper.load(path = modelUri, contextLength = 2048) { id ->
                logD("ensureLoaded: load callback fired, id=$id")
                loadedUri = modelUri
                if (cont.isActive) cont.resume(Unit)
            }
        }
        logD("ensureLoaded: load coroutine resumed, done")
    }

    suspend fun generate(modelUri: String, systemPrompt: String, userPrompt: String): String {
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
        }
        logD("generate: called, chatMlPrompt.length=${chatMlPrompt.length}")
        ensureLoaded(modelUri)
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

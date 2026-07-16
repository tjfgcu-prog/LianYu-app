package com.lianyu.ai.feature.localmodel

import android.content.Context
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

    private val llmFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val llamaHelper by lazy {
        LlamaHelper(appContext.contentResolver, scope, llmFlow)
    }

    @Volatile private var loadedUri: String? = null

    private suspend fun ensureLoaded(modelUri: String) {
        if (loadedUri == modelUri) return
        suspendCancellableCoroutine<Unit> { cont ->
            llamaHelper.load(path = modelUri, contextLength = 2048) {
                loadedUri = modelUri
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    suspend fun generate(modelUri: String, prompt: String): String {
        ensureLoaded(modelUri)
        val builder = StringBuilder()
        return suspendCancellableCoroutine { cont ->
            val collectJob = scope.launch {
                llmFlow.collect { event ->
                    when (event) {
                        is LlamaHelper.LLMEvent.Ongoing -> builder.append(event.word)
                        is LlamaHelper.LLMEvent.Done -> {
                            if (cont.isActive) cont.resume(builder.toString())
                        }
                        is LlamaHelper.LLMEvent.Error -> {
                            if (cont.isActive) cont.resumeWithException(
                                IllegalStateException("GGUF 本地模型生成失败")
                            )
                        }
                        else -> {}
                    }
                }
            }
            cont.invokeOnCancellation { collectJob.cancel() }
            scope.launch { llamaHelper.predict(prompt) }
        }
    }
}

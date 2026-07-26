package com.lianyu.ai.feature.memory.engine

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import kotlin.math.sqrt

class OnnxEmbeddingProvider(context: Context) : EmbeddingProvider {
    private val appContext = context.applicationContext
    private val env = OrtEnvironment.getEnvironment()

    private val tokenizer by lazy { WordPieceTokenizer(appContext, "bge_small_zh_vocab.txt") }

    private val session: OrtSession? by lazy {
        runCatching {
            val modelBytes = appContext.assets.open("bge_small_zh_quantized.onnx").readBytes()
            env.createSession(modelBytes, OrtSession.SessionOptions())
        }.getOrNull()
    }

    override fun isReady(): Boolean = session != null

    override suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.Default) {
        val s = session ?: return@withContext null
        runCatching {
            val (inputIds, attentionMask) = tokenizer.encode(text)
            val shape = longArrayOf(1, inputIds.size.toLong())

            OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape).use { idsTensor ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape).use { maskTensor ->
                    val inputs = mapOf(
                        "input_ids" to idsTensor,
                        "attention_mask" to maskTensor
                    )
                    s.run(inputs).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val lastHidden = (result[0].value as Array<Array<FloatArray>>)[0]
                        meanPool(lastHidden, attentionMask)
                    }
                }
            }
        }.getOrNull()
    }

    private fun meanPool(hidden: Array<FloatArray>, mask: LongArray): FloatArray {
        val dim = hidden[0].size
        val sum = FloatArray(dim)
        var validCount = 0
        for (i in hidden.indices) {
            if (mask[i] == 1L) {
                for (d in 0 until dim) sum[d] += hidden[i][d]
                validCount++
            }
        }
        if (validCount == 0) validCount = 1
        for (d in 0 until dim) sum[d] /= validCount

        var norm = 0f
        for (v in sum) norm += v * v
        norm = sqrt(norm).coerceAtLeast(1e-6f)
        for (d in 0 until dim) sum[d] /= norm
        return sum
    }
}

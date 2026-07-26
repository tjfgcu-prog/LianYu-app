package com.lianyu.ai.feature.memory.engine

interface EmbeddingProvider {
    suspend fun embed(text: String): FloatArray?
    fun isReady(): Boolean
}

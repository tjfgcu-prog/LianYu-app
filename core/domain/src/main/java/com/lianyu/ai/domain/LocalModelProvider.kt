package com.lianyu.ai.domain

/**
 * Provides access to the on-device AI model.
 * Implemented by feature:localmodel.
 */
interface LocalModelProvider {
    suspend fun isAvailable(): Boolean
    suspend fun generateResponse(prompt: String, context: String): String
    suspend fun preloadIfEnabled()
    fun getModelName(): String
    fun getModelVersion(): String
}

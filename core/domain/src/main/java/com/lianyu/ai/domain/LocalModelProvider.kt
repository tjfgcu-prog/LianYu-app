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

    /** 当前真实状态。 */
    fun getStatus(): LocalModelStatus

    /** 加载模型，返回真实结果（已加载则直接成功）。 */
    suspend fun loadModel(): LocalModelResult

    /** 卸载模型释放内存；正在生成时等这一条结束。 */
    suspend fun unloadModel()

    /** 加载（如未加载）并让模型生成一小段话，证明真的能用。 */
    suspend fun testModel(): LocalModelResult

    /** 上次真实加载的记录，没有则返回空串。 */
    fun getLastLoadRecord(): String
}

package com.lianyu.ai.domain

/** 本地模型当前状态，只反映本进程内存里的真实情况。 */
sealed interface LocalModelStatus {
    data object Unloaded : LocalModelStatus
    data class Loading(val startedAtMs: Long) : LocalModelStatus
    data class Loaded(val contextLength: Int, val loadMs: Long) : LocalModelStatus
    data class Generating(val contextLength: Int, val loadMs: Long) : LocalModelStatus
    data object Unloading : LocalModelStatus
    data class Failed(val reason: String) : LocalModelStatus
}

/** 一次加载或测试的结果。 */
data class LocalModelResult(
    val success: Boolean,
    val message: String
)

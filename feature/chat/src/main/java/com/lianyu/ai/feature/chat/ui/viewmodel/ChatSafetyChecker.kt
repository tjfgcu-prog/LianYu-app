package com.lianyu.ai.feature.chat.ui.viewmodel

/**
 * Safety check logic for AI-generated output.
 * (临时放行模式：旧检测体系已拔除，直接返回放行状态，等待新检测系统接入)
 */
internal object ChatSafetyChecker {

    data class SafetyDecision(
        val isBlocked: Boolean,
        val fallbackMessage: String? = null
    )

    /**
     * 临时放行所有 AI 输出，避免旧算法阻断
     */
    suspend fun checkAiOutput(
        aiContent: String,
        userContentForMemory: String?
    ): SafetyDecision {
        return SafetyDecision(
            isBlocked = false,
            fallbackMessage = null
        )
    }
}


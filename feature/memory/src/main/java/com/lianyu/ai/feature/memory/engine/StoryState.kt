package com.lianyu.ai.feature.memory.engine

import kotlinx.serialization.Serializable

/**
 * 剧情状态（每个角色一份，持久化为 story_state.json）。
 * MemoryItem = 长期事实；StoryState = 正在进行的故事。
 */
@Serializable
data class StoryState(
    val companionId: Long,
    val summary: String = "",
    
    val pendingTurns: List<String> = emptyList()
    
)

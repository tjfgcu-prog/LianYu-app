package com.lianyu.ai.domain

/**
 * 主动消息相关设置（domain 层纯数据类，零依赖）。
 *
 * 作为 feature:chat 的 [CompanionChatDetailSettings] 与
 * feature:notification 的 Worker 设置之间的共享契约，
 * 避免跨 feature 依赖、并消除字段漂移（历史上 Worker 自带的
 * ProactiveSettings 漏掉了 proactiveIntervalMinutes 等字段）。
 *
 * 序列化由各 feature 侧各自的 @Serializable 映射类负责；
 * 此类仅承载跨模块语义，不带序列化注解（domain 保持零依赖）。
 */
data class ProactiveMessageSettings(
    /** 主动消息总开关 */
    val proactiveEnabled: Boolean = true,
    /** 是否允许 AI 主动开启新话题（false=必须承接上一话题） */
    val allowNewTopic: Boolean = true,
    /** 是否允许在主动消息后追问 */
    val allowFollowUpMessage: Boolean = true,
    /** 免打扰开关 */
    val doNotDisturbEnabled: Boolean = false,
    /** 免打扰开始（一天内分钟数，如 23:00 = 1380） */
    val dndStartMinutes: Int = 23 * 60,
    /** 免打扰结束（一天内分钟数，如 08:00 = 480） */
    val dndEndMinutes: Int = 8 * 60,
    /** DND 时段是否允许深夜消息 */
    val allowLateNightMessage: Boolean = false,
    /** DND 时段是否允许优先级消息 */
    val allowPriorityMessageInDnd: Boolean = false,
    /** 是否屏蔽该伴侣的主动消息 */
    val blocked: Boolean = false
)

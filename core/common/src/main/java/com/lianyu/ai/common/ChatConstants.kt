package com.lianyu.ai.common

/**
 * 聊天业务层常量集中管理。
 *
 * 包括：
 * - 聊天消息分页参数
 * - 批量消息合并/拆分参数
 * - 消息通道背压参数
 * - 提示词/上下文截断长度
 * - 群聊上下文参数
 *
 * 这些数值原先散落在 ChatViewModel / GroupChatViewModel / AiService 中，
 * 集中后便于统一调整并保持多模块一致。
 */
object ChatConstants {

    // ── 聊天消息分页 ──
    /** 初始进入聊天时加载的消息条数 */
    const val CHAT_PAGE_SIZE = 50

    /** 点击"加载更多"时追加的历史消息条数 */
    const val CHAT_LOAD_MORE_SIZE = 30

    

    /** 批量窗口内轮询间隔（毫秒），避免 CPU 忙等待 */
    const val MESSAGE_BATCH_POLL_INTERVAL_MS = 100L

    /** 单批次最大消息数，超出时拆分为子批次 */
    const val MESSAGE_BATCH_MAX_SIZE = 10

    

    /** 追问/重生成等辅助流程使用的少量最近历史条数 */
    const val SHORT_HISTORY_LIMIT = 10

    /** AI 上下文从数据库拉取的安全上限（无论用户设置多少，不超过此值） */
    const val MAX_AI_CONTEXT_FETCH = 200

    /** UI 层内存中保留的最大消息条数 */
    const val MAX_UI_MESSAGES = 200

    /** AI 上下文解析缓存大小 */
    const val CONTEXT_CACHE_SIZE = 3

    

    // ── 群聊 ──
    /** 群聊自动对话默认轮数 */
    const val GROUP_CHAT_AUTO_ROUNDS = 2

    

    /** 群聊上下文窗口条数 */
    const val GROUP_CHAT_CONTEXT_WINDOW = 20

    /** 群聊 @ 判断阈值 */
    const val GROUP_CHAT_MENTION_JUDGE_THRESHOLD = 0.8f

    /** 群聊 @ 判断是否启用 */
    const val GROUP_CHAT_MENTION_JUDGE_ENABLED = true

    

    /** 追问触发后的基础延迟 */
    const val FOLLOW_UP_BASE_DELAY_MS = 2000L

    /** 追问触发后的随机延迟上限 */
    const val FOLLOW_UP_RANDOM_DELAY_MS = 3000L

    

    /** 是否"正在聊天"判定阈值（分钟）：最后一条消息（不分收发）距今在此范围内视为正在聊天，
     * 期间不触发"主动发信息"和"病娇模式" */
    const val ACTIVE_CHATTING_THRESHOLD_MINUTES = 5

    /** 主动消息 Worker 固定检查间隔（分钟）：没有聊天时，每此间隔滚动一次概率 */
    const val PROACTIVE_CHECK_INTERVAL_MINUTES = 60L

    /** 主动消息触发概率（百分比）：没有聊天时，每次检查有此概率发送 */
    const val PROACTIVE_TRIGGER_PROBABILITY_PERCENT = 70

    // ── 病娇模式 Worker ──
    /** 病娇消息 Worker 固定检查间隔（分钟） */
    const val YANDERE_CHECK_INTERVAL_MINUTES = 10L

    /** 病娇模式追发阈值（分钟）：AI 消息（含主动消息）发出后用户超过此时长未回复，则触发病娇追问 */
    const val YANDERE_ESCALATE_AFTER_MINUTES = 10

    /** 病娇模式空闲必发间隔（分钟）：完全没有聊天时，每此间隔必定触发一次病娇消息 */
    const val YANDERE_IDLE_INTERVAL_MINUTES = 120L

    

    

    /** 本地上下文压缩默认保留比例 */
    const val DEFAULT_COMPRESSION_KEEP_RATIO = 0.5f

    /** 本地上下文压缩默认最小保留条数 */
    const val DEFAULT_COMPRESSION_MIN_KEEP = 6

    

    /** 语音消息停止聊天多久后，再次开口需重新显示顶部时间分隔（毫秒） */
    const val CHAT_TIME_DIVIDER_GAP_MS = 5 * 60 * 1000L
}

/**
 * 语音消息 content 编码工具。
 *
 * 语音消息落库时既要保留时长（用于气泡展示），又要保留原始文字（用于气泡内文字展示，
 * 如同时展示"语音条 + 转写文字"）。使用不可见分隔符把两者编码进同一个 content 字段，
 * 避免额外的数据库字段迁移。
 */
object VoiceMessageCodec {
    /** content 中时长与原文之间的分隔符，选用普通文本不会出现的控制字符 */
    private const val SEPARATOR = "\u0002"

    /** 构建语音消息的 content：`[语音] <duration>"<SEPARATOR><原文文字>` */
    fun encode(duration: Int, text: String): String {
        return "[语音] ${duration}\"$SEPARATOR$text"
    }

    /** 从 content 中提取语音时长（秒），提取失败时返回 1 */
    fun extractDuration(content: String): Int {
        val head = content.substringBefore(SEPARATOR)
        val regex = Regex("\\[语音]\\s*(\\d+)[\"秒]")
        return regex.find(head)?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    /** 从 content 中提取语音对应的原始文字，没有则返回 null */
    fun extractText(content: String): String? {
        if (!content.contains(SEPARATOR)) return null
        val text = content.substringAfter(SEPARATOR)
        return text.ifBlank { null }
    }
}


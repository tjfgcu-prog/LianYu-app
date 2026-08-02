package com.lianyu.ai.feature.notification

import android.content.Context

import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.MessageType
import com.lianyu.ai.database.repository.ChatMessageCrypto
import com.lianyu.ai.database.repository.filterDecrypted
import com.lianyu.ai.domain.AiServiceProvider
import com.lianyu.ai.domain.AiCompanionInfo
import com.lianyu.ai.domain.AiChatMessage
import com.lianyu.ai.domain.AiMessageType
import com.lianyu.ai.domain.ProactiveMessageSettings
import com.lianyu.ai.domain.ServiceRegistry
import com.lianyu.ai.common.AppForegroundTracker

import com.lianyu.ai.common.ChatDetailSettingsDataStoreProvider
import com.lianyu.ai.common.ChatConstants
import com.lianyu.ai.common.SecureLog
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** 仅提取主动消息相关字段，避免跨 feature 依赖完整设置类。
 * 字段必须与 feature:chat 的 [CompanionChatDetailSettings] 中同名字段保持对齐，
 * 否则反序列化时 ignoreUnknownKeys=true 会静默丢弃。 */
@Serializable
data class ProactiveSettings(
    val proactiveEnabled: Boolean = true,
    /** 是否允许 AI 主动开启新话题 */
    val allowNewTopic: Boolean = true,
    /** 是否允许在主动消息后追加追问句 */
    val allowFollowUpMessage: Boolean = true,
    val doNotDisturbEnabled: Boolean = false,
    val dndStartMinutes: Int = 23 * 60,
    val dndEndMinutes: Int = 8 * 60,
    val allowLateNightMessage: Boolean = false,
    val allowPriorityMessageInDnd: Boolean = false,
    val blocked: Boolean = false
)

class CompanionMessageWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val aiServiceProvider: AiServiceProvider by lazy {
        ServiceRegistry.get(AiServiceProvider::class.java)
            ?: throw IllegalStateException("AiServiceProvider not registered in ServiceRegistry")
    }

    // 直接读取 chat_detail_settings DataStore，避免跨 feature 依赖
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
            val companionDao = database.companionDao()
            val chatMessageDao = database.chatMessageDao()

            val companions = companionDao.getAllCompanionsSync()
            if (companions.isEmpty()) return@withContext Result.success()

            // ── 筛选启用主动消息且未屏蔽的伴侣 ──
            val eligibleCompanions = companions.filter { companion ->
                runCatching { readCompanionSettings(companion.id) }.getOrNull()?.let { settings ->
                    settings.proactiveEnabled && !settings.blocked
                } ?: false
            }

            if (eligibleCompanions.isEmpty()) {
                SecureLog.d("CompanionMessageWorker", "No eligible companions (all disabled/blocked), reschedule")
                scheduleNext(context)
                return@withContext Result.success()
            }

            for (companionItem in eligibleCompanions) {
                val settings = runCatching { readCompanionSettings(companionItem.id) }.getOrNull()
                    ?: ProactiveSettings()

                // ── 免打扰检查 ──
                if (settings.doNotDisturbEnabled && !settings.allowPriorityMessageInDnd) {
                    val now = java.util.Calendar.getInstance()
                    val totalMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                    val inDndRange = if (settings.dndStartMinutes > settings.dndEndMinutes) {
                        totalMinutes >= settings.dndStartMinutes || totalMinutes < settings.dndEndMinutes
                    } else {
                        totalMinutes in settings.dndStartMinutes until settings.dndEndMinutes
                    }
                    if (inDndRange && !settings.allowLateNightMessage) {
                        SecureLog.d("CompanionMessageWorker", "DND active for ${companionItem.name}, skip")
                        continue
                    }
                }

                val recentMessages = chatMessageDao.getRecentMessagesSync(companionItem.id, 10)
                    .map { ChatMessageCrypto.decryptFromStorage(it) }
                    .filterDecrypted()

                // getRecentMessagesSync 按 timestamp DESC 排列，第一条即最新消息
                val lastMessage = recentMessages.firstOrNull()
                val nowMs = System.currentTimeMillis()
                val isActivelyChatting = lastMessage != null &&
                    (nowMs - lastMessage.timestamp) < TimeUnit.MINUTES.toMillis(ChatConstants.ACTIVE_CHATTING_THRESHOLD_MINUTES.toLong())

                // 规则①：正在聊天中，不触发主动发信息
                if (isActivelyChatting) {
                    SecureLog.d("CompanionMessageWorker", "Actively chatting with ${companionItem.name}, skip proactive message")
                    continue
                }

                // 规则②：没有聊天时，每次检查有 70% 概率触发
                if (Random.nextInt(100) >= ChatConstants.PROACTIVE_TRIGGER_PROBABILITY_PERCENT) {
                    SecureLog.d("CompanionMessageWorker", "Probability roll missed for ${companionItem.name}, skip this round")
                    continue
                }

                val domainSettings = settings.toDomain()
                val messageContent = aiServiceProvider.generateProactiveMessage(companionItem.toAiCompanionInfo(), recentMessages.toAiChatMessages(), domainSettings)
                    ?: continue

                val segments = splitIntoSegments(messageContent)

                for ((index, segment) in segments.withIndex()) {
                    if (index > 0) {
                        delay(Random.nextLong(1000L, 2000L))
                    }

                    val message = ChatMessage(
                        companionId = companionItem.id,
                        content = segment,
                        isFromUser = false
                    )
                    chatMessageDao.insertMessage(ChatMessageCrypto.encryptForStorage(message))
                }

                if (!AppForegroundTracker.isInForeground && segments.isNotEmpty()) {
                    NotificationHelper.showCompanionMessageNotification(
                        context,
                        companionItem.name,
                        segments.first(),
                        companionItem.id
                    )
                }
            }

            scheduleNext(context)

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    /**
     * 将长文本拆分为自然短段落。
     * 按中文句号/感叹号/问号/换行分割，每段不超过60字。
     */
    private fun splitIntoSegments(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.length <= 60) return listOf(trimmed)

        // 按句子边界分割
        val sentenceParts = trimmed.split(Regex("(?<=[。！？!?\n])"))
        val segments = mutableListOf<String>()
        var currentSegment = StringBuilder()

        for (part in sentenceParts) {
            val candidate = if (currentSegment.isEmpty()) part else "$currentSegment$part"
            if (candidate.length <= 60) {
                currentSegment = StringBuilder(candidate)
            } else {
                if (currentSegment.isNotEmpty()) {
                    segments.add(currentSegment.toString().trim())
                }
                currentSegment = StringBuilder(part)
            }
        }
        if (currentSegment.isNotEmpty()) {
            segments.add(currentSegment.toString().trim())
        }

        // 兜底：如果某段仍然过长，强制按字数截断
        return segments.flatMap { seg ->
            if (seg.length <= 60) listOf(seg) else seg.chunked(60)
        }.filter { it.isNotBlank() }
    }

    /**
     * 从 DataStore 读取指定伴侣的主动消息相关设置。
     * 直接读取与 ChatDetailSettingsStore 共享的同一 DataStore，避免跨 feature 依赖。
     */
    private suspend fun readCompanionSettings(companionId: Long): ProactiveSettings? {
        return runCatching {
            val dataStore = ChatDetailSettingsDataStoreProvider.get(context)
            val prefs = dataStore.data.first()
            val raw = prefs[stringPreferencesKey("companion_chat_detail_settings_map")] ?: return@runCatching null
            val settingsMap: Map<Long, ProactiveSettings> = json.decodeFromString(raw)
            settingsMap[companionId]
        }.getOrNull()
    }

    // ── 领域类型转换辅助 ──

    /** 将 Worker 侧 [ProactiveSettings] 映射为 domain 层 [ProactiveMessageSettings] */
    private fun ProactiveSettings.toDomain() = ProactiveMessageSettings(
        proactiveEnabled = proactiveEnabled,
        allowNewTopic = allowNewTopic,
        allowFollowUpMessage = allowFollowUpMessage,
        doNotDisturbEnabled = doNotDisturbEnabled,
        dndStartMinutes = dndStartMinutes,
        dndEndMinutes = dndEndMinutes,
        allowLateNightMessage = allowLateNightMessage,
        allowPriorityMessageInDnd = allowPriorityMessageInDnd,
        blocked = blocked
    )

    private fun com.lianyu.ai.database.model.CompanionEntity.toAiCompanionInfo() = AiCompanionInfo(
        id = id, name = name, personality = personality,
        age = age, backstory = backstory, speakingStyle = speakingStyle,
        systemPrompt = systemPrompt
    )

    private fun com.lianyu.ai.database.model.ChatMessage.toAiChatMessage() = AiChatMessage(
        isFromUser = isFromUser, content = content, timestamp = timestamp,
        type = when (type) {
            MessageType.IMAGE -> AiMessageType.IMAGE
            else -> AiMessageType.TEXT
        },
        companionId = companionId
    )

    private fun List<com.lianyu.ai.database.model.ChatMessage>.toAiChatMessages() = map { it.toAiChatMessage() }

    companion object {
        private const val WORK_NAME = "companion_message_work"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * 外部入口：首次调度。
         * 使用 enqueueUniqueWork + REPLACE 确保只保留最新一次调度，
         * 消除 KeepAliveService 15min 心跳 + MainActivity 启动反复 schedule 导致的请求堆叠。
         */
        fun schedule(context: Context) {
            enqueue(context, ChatConstants.PROACTIVE_CHECK_INTERVAL_MINUTES)
        }

        /**
         * 后续调度：固定每 [ChatConstants.PROACTIVE_CHECK_INTERVAL_MINUTES] 分钟检查一次。
         * 是否真正发送由 doWork() 内的"是否正在聊天"+"70% 概率"两条规则决定，
         * 不再支持用户自定义间隔。
         */
        private fun scheduleNext(context: Context) {
            enqueue(context, ChatConstants.PROACTIVE_CHECK_INTERVAL_MINUTES)
        }

        private fun enqueue(context: Context, delayMinutes: Long) {
            val workRequest = OneTimeWorkRequestBuilder<CompanionMessageWorker>()
                .setConstraints(networkConstraints)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

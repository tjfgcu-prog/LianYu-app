package com.lianyu.ai.feature.notification

import android.content.Context

import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

import com.lianyu.ai.common.AppForegroundTracker
import com.lianyu.ai.common.AppSettingsStore
import com.lianyu.ai.common.ChatConstants
import com.lianyu.ai.common.ChatDetailSettingsDataStoreProvider
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.MessageType
import com.lianyu.ai.database.repository.ChatMessageCrypto
import com.lianyu.ai.database.repository.filterDecrypted
import com.lianyu.ai.domain.AiChatMessage
import com.lianyu.ai.domain.AiCompanionInfo
import com.lianyu.ai.domain.AiMessageType
import com.lianyu.ai.domain.AiServiceProvider
import com.lianyu.ai.domain.ServiceRegistry
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class YandereMessageWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val aiServiceProvider: AiServiceProvider by lazy {
        ServiceRegistry.get(AiServiceProvider::class.java)
            ?: throw IllegalStateException("AiServiceProvider not registered in ServiceRegistry")
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val appSettingsStore = AppSettingsStore(context)
            if (!appSettingsStore.getYandereModeEnabled()) {
                scheduleNext(context)
                return@withContext Result.success()
            }

            val database = AppDatabase.getDatabase(context)
            val companionDao = database.companionDao()
            val chatMessageDao = database.chatMessageDao()

            val companions = companionDao.getAllCompanionsSync()
            if (companions.isEmpty()) {
                scheduleNext(context)
                return@withContext Result.success()
            }

            for (companionItem in companions) {
                val blocked = runCatching { readBlockedFlag(companionItem.id) }.getOrNull() ?: false
                if (blocked) continue

                val recentMessages = chatMessageDao.getRecentMessagesSync(companionItem.id, 10)
                    .map { ChatMessageCrypto.decryptFromStorage(it) }
                    .filterDecrypted()

                // getRecentMessagesSync 按 timestamp DESC 排列，第一条即最新消息
                val lastMessage = recentMessages.firstOrNull() ?: continue
                val nowMs = System.currentTimeMillis()
                val elapsedMinutes = TimeUnit.MILLISECONDS.toMinutes(nowMs - lastMessage.timestamp)

                // 规则①：正在聊天中，不触发
                if (elapsedMinutes < ChatConstants.ACTIVE_CHATTING_THRESHOLD_MINUTES) {
                    continue
                }

                val shouldTrigger = if (!lastMessage.isFromUser) {
                    // 规则②：最后一条是 AI 发的（含主动消息），超过阈值未回复则触发
                    elapsedMinutes >= ChatConstants.YANDERE_ESCALATE_AFTER_MINUTES
                } else {
                    // 规则③：完全没有聊天，按空闲计时器每 120 分钟必定触发一次
                    val idleGateElapsedMinutes = minutesSinceLastIdleTrigger(context, companionItem.id)
                    idleGateElapsedMinutes >= ChatConstants.YANDERE_IDLE_INTERVAL_MINUTES
                }

                if (!shouldTrigger) continue

                if (lastMessage.isFromUser) {
                    markIdleTriggerNow(context, companionItem.id)
                }

                val messageContent = aiServiceProvider.generateYandereMessage(
                    companionItem.toAiCompanionInfo(),
                    recentMessages.toAiChatMessages()
                ) ?: continue

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

    private fun splitIntoSegments(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.length <= 60) return listOf(trimmed)

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

        return segments.flatMap { seg ->
            if (seg.length <= 60) listOf(seg) else seg.chunked(60)
        }.filter { it.isNotBlank() }
    }

    private suspend fun readBlockedFlag(companionId: Long): Boolean? {
        return runCatching {
            val dataStore = ChatDetailSettingsDataStoreProvider.get(context)
            val prefs = dataStore.data.first()
            val raw = prefs[stringPreferencesKey("companion_chat_detail_settings_map")] ?: return@runCatching null
            val settingsMap: Map<Long, ProactiveSettings> = json.decodeFromString(raw)
            settingsMap[companionId]?.blocked
        }.getOrNull()
    }

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
        private const val WORK_NAME = "yandere_message_work"
        private const val IDLE_GATE_PREFS = "yandere_idle_gate"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private fun minutesSinceLastIdleTrigger(context: Context, companionId: Long): Long {
            val prefs = context.getSharedPreferences(IDLE_GATE_PREFS, Context.MODE_PRIVATE)
            val lastAt = prefs.getLong("last_$companionId", 0L)
            if (lastAt == 0L) return Long.MAX_VALUE
            return TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - lastAt)
        }

        private fun markIdleTriggerNow(context: Context, companionId: Long) {
            val prefs = context.getSharedPreferences(IDLE_GATE_PREFS, Context.MODE_PRIVATE)
            prefs.edit().putLong("last_$companionId", System.currentTimeMillis()).apply()
        }

        fun schedule(context: Context) {
            enqueue(context, ChatConstants.YANDERE_CHECK_INTERVAL_MINUTES)
        }

        private fun scheduleNext(context: Context) {
            enqueue(context, ChatConstants.YANDERE_CHECK_INTERVAL_MINUTES)
        }

        private fun enqueue(context: Context, delayMinutes: Long) {
            val workRequest = OneTimeWorkRequestBuilder<YandereMessageWorker>()
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

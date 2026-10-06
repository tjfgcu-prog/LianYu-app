package com.lianyu.ai.feature.backup

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lianyu.ai.common.ChatDetailSettingsDataStoreProvider
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * 读写"剧情模式"开关。
 * 开关存在聊天详情设置的 DataStore 里（与 feature:chat 共用同一份），这里直接按 JSON 读写，
 * 只动 storyModeEnabled 一个字段，避免依赖 feature:chat（与 feature:notification 的做法一致）。
 */
internal class StoryModeBackupStore(context: Context) {
    private val dataStore = ChatDetailSettingsDataStoreProvider.get(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("companion_chat_detail_settings_map")

    private fun decode(raw: String?): JsonObject =
        runCatching { raw?.let { json.parseToJsonElement(it) as? JsonObject } }.getOrNull()
            ?: JsonObject(emptyMap())

    /** 读出已开启剧情模式的角色 ID。 */
    suspend fun readEnabledIds(): Set<Long> {
        val map = decode(dataStore.data.first()[key])
        return map.entries.mapNotNull { (id, value) ->
            val enabled = ((value as? JsonObject)?.get("storyModeEnabled") as? JsonPrimitive)?.booleanOrNull ?: false
            if (enabled) id.toLongOrNull() else null
        }.toSet()
    }

    /** [allIds] 里在 [enabledIds] 中的写成开，其余写成关；其他设置项不动。 */
    suspend fun apply(allIds: Collection<Long>, enabledIds: Set<Long>) {
        dataStore.edit { prefs ->
            val current: MutableMap<String, JsonElement> = decode(prefs[key]).toMutableMap()
            allIds.forEach { id ->
                val entry: MutableMap<String, JsonElement> =
                    (current[id.toString()] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                entry["storyModeEnabled"] = JsonPrimitive(id in enabledIds)
                current[id.toString()] = JsonObject(entry)
            }
            prefs[key] = JsonObject(current).toString()
        }
    }
}

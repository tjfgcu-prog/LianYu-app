package com.lianyu.ai.network.tts

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localTtsDataStore by preferencesDataStore(name = "local_tts_settings")

/**
 * 本地离线 TTS 偏好持久化。只有一个手动导入的自定义模型，
 * 不再需要多模型选择 / 下载进度这些状态。
 */
class LocalTtsPreferences(private val context: Context) {

    private val dataStore = context.applicationContext.localTtsDataStore
    private val enabledKey = booleanPreferencesKey("custom_tts_enabled")

    val isEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[enabledKey] ?: false }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[enabledKey] = enabled }
    }
}

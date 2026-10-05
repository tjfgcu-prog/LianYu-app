package com.lianyu.ai.database.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.lianyu.ai.common.CompanionRole
import com.lianyu.ai.common.UserSelfProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class UserRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    private val _userName = MutableStateFlow(prefs.getString("user_name", "我") ?: "我")
    val userName: StateFlow<String> = _userName

    private val _userAvatar = MutableStateFlow(prefs.getString("user_avatar", null))
    val userAvatar: StateFlow<String?> = _userAvatar

    private val _userBanner = MutableStateFlow(prefs.getString("user_banner", null))
    val userBanner: StateFlow<String?> = _userBanner

    private val _selectedRole = MutableStateFlow(
        CompanionRole.fromName(prefs.getString("selected_role", null))
    )
    val selectedRole: StateFlow<CompanionRole> = _selectedRole

    fun updateUserName(name: String) {
        prefs.edit { putString("user_name", name) }
        _userName.value = name
    }

    fun updateUserAvatar(avatarUri: String?) {
        if (avatarUri != null) {
            prefs.edit { putString("user_avatar", avatarUri) }
        } else {
            prefs.edit { remove("user_avatar") }
        }
        _userAvatar.value = avatarUri
    }

    fun updateUserBanner(bannerUri: String?) {
        if (bannerUri != null) {
            prefs.edit { putString("user_banner", bannerUri) }
        } else {
            prefs.edit { remove("user_banner") }
        }
        _userBanner.value = bannerUri
    }

    fun updateSelectedRole(role: CompanionRole) {
        prefs.edit(commit = true) { putString("selected_role", role.name) }
        _selectedRole.value = role
    }
    // ── 自己设定 ──
    // 直接读写 SharedPreferences，不缓存：项目里有多个 UserRepository 实例，缓存会互相读到旧值。

    /** "我的信息"。姓名就是聊天昵称（user_name）；默认值"我"视为未填。 */
    fun getSelfProfile(): UserSelfProfile = UserSelfProfile(
        name = (prefs.getString("user_name", "") ?: "").takeIf { it != "我" } ?: "",
        gender = prefs.getString("self_gender", "") ?: "",
        age = prefs.getString("self_age", "") ?: "",
        occupation = prefs.getString("self_occupation", "") ?: "",
        callName = prefs.getString("self_call_name", "") ?: "",
        note = prefs.getString("self_note", "") ?: ""
    )

    /** 保存"我的信息"。姓名非空时同步改聊天昵称；姓名留空则不动昵称。 */
    fun updateSelfProfile(profile: UserSelfProfile) {
        if (profile.name.isNotBlank()) updateUserName(profile.name.trim())
        prefs.edit {
            putOrRemove("self_gender", profile.gender)
            putOrRemove("self_age", profile.age)
            putOrRemove("self_occupation", profile.occupation)
            putOrRemove("self_call_name", profile.callName)
            putOrRemove("self_note", profile.note.take(UserSelfProfile.NOTE_MAX))
        }
    }

    /** "这个角色眼中的我"的专属设定，只影响提示词，不改聊天昵称。 */
    fun getCompanionSelfProfile(companionId: Long): UserSelfProfile {
        val p = "self_c${companionId}_"
        return UserSelfProfile(
            name = prefs.getString(p + "name", "") ?: "",
            gender = prefs.getString(p + "gender", "") ?: "",
            age = prefs.getString(p + "age", "") ?: "",
            occupation = prefs.getString(p + "occupation", "") ?: "",
            callName = prefs.getString(p + "call_name", "") ?: "",
            note = prefs.getString(p + "note", "") ?: ""
        )
    }

    fun updateCompanionSelfProfile(companionId: Long, profile: UserSelfProfile) {
        val p = "self_c${companionId}_"
        prefs.edit {
            putOrRemove(p + "name", profile.name)
            putOrRemove(p + "gender", profile.gender)
            putOrRemove(p + "age", profile.age)
            putOrRemove(p + "occupation", profile.occupation)
            putOrRemove(p + "call_name", profile.callName)
            putOrRemove(p + "note", profile.note.take(UserSelfProfile.NOTE_MAX))
        }
    }

    /** 删除角色时调用。 */
    fun clearCompanionSelfProfile(companionId: Long) {
        updateCompanionSelfProfile(companionId, UserSelfProfile())
    }

    /** 提示词实际使用的版本：companionId 为 null（群聊）只用"我的信息"。 */
    fun getEffectiveSelfProfile(companionId: Long?): UserSelfProfile {
        val base = getSelfProfile()
        return if (companionId == null) base else getCompanionSelfProfile(companionId).overlay(base)
    }

    private fun SharedPreferences.Editor.putOrRemove(key: String, value: String) {
        val v = value.trim()
        if (v.isEmpty()) remove(key) else putString(key, v)
    }
}

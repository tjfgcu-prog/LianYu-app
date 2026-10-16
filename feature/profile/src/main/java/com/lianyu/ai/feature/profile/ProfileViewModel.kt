package com.lianyu.ai.feature.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.common.ImageUtils
import com.lianyu.ai.database.repository.UserRepository
import com.lianyu.ai.domain.ServiceRegistry
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.lianyu.ai.database.model.BannerTransform


class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository by lazy { ServiceRegistry.getOrThrow(UserRepository::class.java) }

    val userName: StateFlow<String> by lazy { repository.userName }
    val userAvatar: StateFlow<String?> by lazy { repository.userAvatar }
    val userBanner: StateFlow<String?> by lazy { repository.userBanner }

    fun updateUserName(name: String) {
        viewModelScope.launch { repository.updateUserName(name) }
    }

    fun updateUserAvatar(avatarUri: String?) {
        viewModelScope.launch {
            val savedUri = if (avatarUri != null) {
                ImageUtils.saveUriToInternalStorage(getApplication(), avatarUri)
            } else null
            repository.updateUserAvatar(savedUri)
        }
    }

    val bannerTransform: StateFlow<BannerTransform> by lazy { repository.bannerTransform }

    /** newUri 为 null 表示只改显示方式，不换图 */
    fun saveBanner(newUri: String?, transform: BannerTransform) {
        viewModelScope.launch {
            if (newUri != null) {
                val saved = ImageUtils.saveBannerToInternalStorage(getApplication(), newUri)
                    ?: return@launch
                repository.updateUserBanner(saved)
            }
            repository.updateBannerTransform(transform)
        }
    }
}

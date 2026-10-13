package com.lianyu.ai.feature.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.common.ImageUtils
import com.lianyu.ai.database.repository.UserRepository
import com.lianyu.ai.domain.ServiceRegistry
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

    fun updateUserBanner(bannerUri: String?) {
        viewModelScope.launch {
            val savedUri = if (bannerUri != null) {
                ImageUtils.saveBannerToInternalStorage(getApplication(), bannerUri)
            } else null
            repository.updateUserBanner(savedUri)
        }
    }
}

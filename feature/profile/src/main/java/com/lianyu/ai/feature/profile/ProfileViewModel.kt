package com.lianyu.ai.feature.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.common.ImageUtils
import com.lianyu.ai.database.repository.UserRepository
import com.lianyu.ai.domain.ServiceRegistry
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.widget.Toast
import com.lianyu.ai.feature.profile.R
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

    fun saveBanner(newUri: String?, transform: BannerTransform) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            if (newUri != null) {
                val saved = ImageUtils.saveBannerToInternalStorage(context, newUri)
                if (saved == null) {
                    Toast.makeText(context, R.string.banner_save_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                repository.updateUserBanner(saved)
                ImageUtils.cleanupBannerFiles(context, keepPath = saved)
            }
            repository.updateBannerTransform(transform)
            Toast.makeText(context, R.string.banner_save_success, Toast.LENGTH_SHORT).show()
        }
        }
        }
    }
}

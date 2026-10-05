package com.lianyu.ai.feature.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.common.UserSelfProfile
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.database.repository.CompanionRepository
import com.lianyu.ai.database.repository.UserRepository
import com.lianyu.ai.domain.ServiceRegistry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SelfProfileViewModel(application: Application) : AndroidViewModel(application) {
    // 用 ServiceRegistry 里的同一个 UserRepository，改姓名时聊天昵称的 StateFlow 才会同步更新
    private val userRepository by lazy { ServiceRegistry.getOrThrow(UserRepository::class.java) }
    private val companionRepository by lazy { ServiceRegistry.getOrThrow(CompanionRepository::class.java) }

    val companions: StateFlow<List<CompanionEntity>> by lazy {
        companionRepository.getAllCompanions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun getSelf(): UserSelfProfile = userRepository.getSelfProfile()
    fun saveSelf(profile: UserSelfProfile) = userRepository.updateSelfProfile(profile)
    fun getCompanionSelf(id: Long): UserSelfProfile = userRepository.getCompanionSelfProfile(id)
    fun saveCompanionSelf(id: Long, profile: UserSelfProfile) =
        userRepository.updateCompanionSelfProfile(id, profile)
}

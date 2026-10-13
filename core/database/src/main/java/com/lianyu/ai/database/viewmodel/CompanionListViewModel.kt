package com.lianyu.ai.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.DefaultCompanionSeeder
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.database.repository.CompanionRepository
import com.lianyu.ai.database.repository.UserRepository
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class CompanionListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CompanionRepository by lazy {
        CompanionRepository(AppDatabase.getDatabase(getApplication()).companionDao())
    }

    val companions: Flow<List<CompanionEntity>> = flow {
        emitAll(repository.getAllCompanions())
    }.flowOn(Dispatchers.IO)

    
}

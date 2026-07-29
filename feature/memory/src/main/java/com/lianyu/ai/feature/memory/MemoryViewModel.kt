package com.lianyu.ai.feature.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.database.repository.CompanionRepository
import com.lianyu.ai.feature.memory.engine.MemoryCategory
import com.lianyu.ai.feature.memory.engine.MemoryItem
import com.lianyu.ai.feature.memory.engine.MemoryManager
import com.lianyu.ai.feature.memory.engine.MemoryScope
import com.lianyu.ai.feature.memory.engine.MemorySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class MemoryViewModel(application: Application) : AndroidViewModel(application) {
    private val memoryManager = MemoryManager.getInstance(application)
    private val companionRepository: CompanionRepository

    val companions: Flow<List<CompanionEntity>>

    init {
        val database = AppDatabase.getDatabase(application)
        companionRepository = CompanionRepository(database.companionDao())
        companions = companionRepository.getAllCompanions()
    }

    fun getMemoriesForCompanion(companionId: Long): Flow<List<MemoryItem>> {
        return memoryManager.memoriesChanged
            .onStart { emit("") }
            .map { memoryManager.getMemories(MemoryScope.COMPANION, companionId) }
    }

    fun deleteMemory(companionId: Long, item: MemoryItem) {
        viewModelScope.launch {
            memoryManager.deleteMemory(MemoryScope.COMPANION, companionId, item.id)
        }
    }

    fun updateMemory(companionId: Long, item: MemoryItem, newContent: String, newCategory: MemoryCategory, newImportance: Float) {
        viewModelScope.launch {
            memoryManager.updateMemory(MemoryScope.COMPANION, companionId, item.id, newContent, newCategory, newImportance)
        }
    }

    fun addManualMemory(
        companionId: Long,
        content: String,
        category: MemoryCategory = MemoryCategory.FACT,
        importance: Float = 0.7f
    ) {
        viewModelScope.launch {
            memoryManager.saveMemory(content, category, importance, MemorySource.MANUAL, companionId, MemoryScope.COMPANION)
        }
    }

    fun deleteMemoriesForCompanion(companionId: Long) {
        viewModelScope.launch {
            memoryManager.deleteAllMemories(MemoryScope.COMPANION, companionId)
        }
    }
}

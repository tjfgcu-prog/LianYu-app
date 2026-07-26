package com.lianyu.ai.feature.memory.engine

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * 倒排索引序列化结构
 */
@Serializable
data class SerializedIndex(
    val keywordToIds: Map<String, List<String>>,
    val categoryToIds: Map<String, List<String>>,
    val importanceSorted: List<String>,
    val timeSorted: List<String>
)

/**
 * 记忆倒排索引
 * 支持关键词检索、类别过滤、重要度排序、时间排序
 * 解决现有 LIKE '%query%' 全表扫描的性能问题
 */
class MemoryIndex {
    private val keywordToIds = ConcurrentHashMap<String, MutableSet<String>>()
    private val categoryToIds = ConcurrentHashMap<MemoryCategory, MutableSet<String>>()
    private val importanceSorted = ConcurrentHashMap<String, Float>()
    private val timeSorted = ConcurrentHashMap<String, Long>()
    private val embeddings = ConcurrentHashMap<String, List<Float>>()

    /**
     * 添加记忆到索引
     */
    fun add(item: MemoryItem) {
        val tokens = MemoryTokenizer.tokenize(item.content)
        tokens.forEach { token ->
            keywordToIds.getOrPut(token) { ConcurrentHashMap.newKeySet() }.add(item.id)
        }
        categoryToIds.getOrPut(item.category) { ConcurrentHashMap.newKeySet() }.add(item.id)
        importanceSorted[item.id] = item.importance
        timeSorted[item.id] = item.timestamp
        item.tags.forEach { tag ->
            keywordToIds.getOrPut(tag) { ConcurrentHashMap.newKeySet() }.add(item.id)
        }
        item.embedding?.let { embeddings[item.id] = it }
    }

    /**
     * 从索引移除记忆
     */
    fun remove(id: String) {
        keywordToIds.values.forEach { it.remove(id) }
        categoryToIds.values.forEach { it.remove(id) }
        importanceSorted.remove(id)
        timeSorted.remove(id)
        embeddings.remove(id)
        keywordToIds.entries.removeAll { it.value.isEmpty() }
    }

    /**
     * 更新访问信息（不影响索引结构，仅更新排序权重）
     */
    fun touch(id: String, importanceBoost: Float = 0.01f) {
        importanceSorted.computeIfPresent(id) { _, v -> (v + importanceBoost).coerceAtMost(1.0f) }
    }

    /**
     * 搜索记忆
     * @param query 查询文本
     * @param category 类别过滤（可选）
     * @param limit 返回数量限制
     * @return 匹配的记忆ID列表（按相关度排序）
     */
    fun search(query: String, queryEmbedding: List<Float>? = null, category: MemoryCategory? = null, limit: Int = 5): List<String> {
        val tokens = MemoryTokenizer.tokenize(query)

        val coarseResult: List<String> = if (tokens.isEmpty()) {
            importanceSorted.entries.sortedByDescending { it.value }.map { it.key }
        } else {
            val candidateScores = ConcurrentHashMap<String, Int>()
            tokens.forEach { token ->
                keywordToIds[token]?.forEach { id ->
                    candidateScores.compute(id) { _, v -> (v ?: 0) + 1 }
                }
            }
            candidateScores.entries.sortedByDescending { it.value }.map { it.key }
        }

        val filtered = if (category != null) {
            val catIds = categoryToIds[category] ?: emptySet()
            coarseResult.filter { it in catIds }
        } else coarseResult

        if (queryEmbedding == null) return filtered.take(limit)

        val candidates = filtered.take(50)

        return candidates
            .mapNotNull { id -> embeddings[id]?.let { id to cosineSimilarity(queryEmbedding, it) } }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
            .ifEmpty { filtered.take(limit) }
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    /**
     * 获取所有记忆ID
     */
    fun allIds(): Set<String> {
        return importanceSorted.keys.toSet()
    }

    /**
     * 索引大小
     */
    fun size(): Int = importanceSorted.size

    /**
     * 序列化索引（用于持久化）
     */
    fun serialize(): SerializedIndex {
        return SerializedIndex(
            keywordToIds = keywordToIds.mapValues { it.value.toList() },
            categoryToIds = categoryToIds.mapKeys { it.key.name }.mapValues { it.value.toList() },
            importanceSorted = importanceSorted.entries.sortedByDescending { it.value }.map { it.key },
            timeSorted = timeSorted.entries.sortedByDescending { it.value }.map { it.key }
        )
    }

    /**
     * 从序列化数据恢复索引
     * 注意：importanceSorted 和 timeSorted 的值需要从实际 MemoryItem 重新填充
     */
    fun deserialize(data: SerializedIndex, items: Map<String, MemoryItem>) {
        clear()
        data.keywordToIds.forEach { (k, v) ->
            keywordToIds[k] = ConcurrentHashMap.newKeySet<String>().apply { addAll(v) }
        }
        data.categoryToIds.forEach { (k, v) ->
            val cat = MemoryCategory.valueOf(k)
            categoryToIds[cat] = ConcurrentHashMap.newKeySet<String>().apply { addAll(v) }
        }
        // 从实际记忆条目重建排序信息
        items.forEach { (id, item) ->
            importanceSorted[id] = item.importance
            timeSorted[id] = item.timestamp
            item.embedding?.let { embeddings[id] = it }
        }
    }

    /**
     * 清空索引
     */
    fun clear() {
        keywordToIds.clear()
        categoryToIds.clear()
        importanceSorted.clear()
        timeSorted.clear()
        embeddings.clear()
    }
}

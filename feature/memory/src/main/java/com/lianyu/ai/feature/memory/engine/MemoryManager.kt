package com.lianyu.ai.feature.memory.engine

import android.content.Context
import android.util.Log
import com.lianyu.ai.common.DeviceIdProvider
import com.lianyu.ai.domain.MemoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 记忆管理器（单例）
 *
 * 核心职责：
 * 1. 统一管理全局/角色/群聊三种作用域的记忆
 * 2. 分层存储：短期(内存)→中期(内存+文件)→长期(文件)
 * 3. 跨会话同步：群聊↔私聊通过全局池共享用户信息
 * 4. 倒排索引检索 + LRU缓存 + TTL过期清理
 *
 * 完全独立于 Room 数据库，使用 JSON 文件持久化
 *
 * 实现 [MemoryProvider] 接口，通过 ServiceRegistry 向 core:network 和 feature:groupchat 提供服务。
 */
class MemoryManager private constructor(
    private val context: Context,
    private val deviceId: String
) : MemoryProvider {
    companion object {
        private const val TAG = "MemoryManager"
        private const val SHORT_TERM_TTL_MS = 5L * 60 * 1000 // 5分钟
        private const val SHORT_TERM_MAX_PER_SCOPE = 50
        private const val MID_TERM_MAX_MEMORY = 500
        private const val CLEANUP_INTERVAL_MS = 5L * 60 * 1000 // 5分钟清理一次
        private const val SYNC_IMPORTANCE_THRESHOLD = 0.7f
        private const val DEDUP_SIMILARITY_THRESHOLD = 0.6f

        @Volatile
        private var instance: MemoryManager? = null

        fun getInstance(context: Context): MemoryManager {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val deviceId = DeviceIdProvider.getDeviceId(context)
                    MemoryManager(context.applicationContext, deviceId).also {
                        instance = it
                    }
                }
            }
        }
        private val _memoriesChanged = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)
        val memoriesChanged: kotlinx.coroutines.flow.SharedFlow<String> = _memoriesChanged.asSharedFlow()
    }

    private val store = MemoryStore(context, deviceId)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val embeddingProvider: EmbeddingProvider by lazy { OnnxEmbeddingProvider(context) }
    
    // 短期记忆：内存缓存，按作用域隔离
    private val shortTermCache = ConcurrentHashMap<String, MutableList<MemoryItem>>()

    // 中期记忆：内存 LRU 缓存
    private val midTermCache = ConcurrentHashMap<String, MutableList<MemoryItem>>()

    // 长期记忆：仅索引在内存，内容按需加载
    private val longTermIndex = ConcurrentHashMap<String, MemoryIndex>()

    // 索引缓存
    private val indexCache = ConcurrentHashMap<String, MemoryIndex>()

    // 查询缓存（LRU）
    private val queryCache = LinkedHashMap<String, List<MemoryItem>>(32, 0.75f, true)

    // 写入锁（按作用域）
    private val writeLocks = ConcurrentHashMap<String, Mutex>()

    // 是否已初始化
    @Volatile
    private var initialized = false
    // [R5 FIX] 初始化原子化锁：原 check-then-set（@Volatile 但无锁）在 AiService.init 和
    // GroupChatViewModel 冷启动并发调用时可同时通过判断，启动两个永久 while(true) 清理协程 + 重复磁盘加载。
    private val initLock = Any()

    /**
     * 初始化：加载持久化记忆到内存
     */
    override fun initialize() {
        // [R5 FIX] 用 synchronized 保证 check-then-set 原子性，避免并发重复初始化
        synchronized(initLock) {
            if (initialized) return
            initialized = true
        }
        ioScope.launch {
            runCatching {
                loadPersistedMemories()
                startCleanupTask()
            }.onFailure { Log.e(TAG, "初始化失败", it) }
        }
    }

    /**
     * 作用域键
     */
    private fun scopeKey(scope: MemoryScope, id: Long): String {
        return "${scope.name}_$id"
    }

    /**
     * 获取写入锁
     */
    private fun getLock(scope: MemoryScope, id: Long): Mutex {
        return writeLocks.computeIfAbsent(scopeKey(scope, id)) { Mutex() }
    }

    /**
     * 加载持久化记忆
     */
    private fun loadPersistedMemories() {
        // 加载全局记忆
        loadScopeFromDisk(MemoryScope.GLOBAL, 0L)

        // 加载所有角色记忆（通过扫描文件系统）
        val globalDir = java.io.File(context.filesDir, "memory/$deviceId")
        globalDir.listFiles()?.forEach { dir ->
            if (dir.name.startsWith("companion_")) {
                val id = dir.name.removePrefix("companion_").toLongOrNull()
                if (id != null) loadScopeFromDisk(MemoryScope.COMPANION, id)
            } else if (dir.name.startsWith("group_")) {
                val id = dir.name.removePrefix("group_").toLongOrNull()
                if (id != null) loadScopeFromDisk(MemoryScope.GROUP, id)
            }
        }
    }

    /**
     * 从磁盘加载单个作用域
     */
    private fun loadScopeFromDisk(scope: MemoryScope, id: Long) {
        val key = scopeKey(scope, id)
        runCatching {
            // 加载中期记忆到内存
            val midItems = store.loadTier(scope, id, MemoryTier.MID)
            if (midItems.isNotEmpty()) {
                midTermCache[key] = midItems.toMutableList()
            }

            // 加载长期记忆索引
            val longItems = store.loadTier(scope, id, MemoryTier.LONG)
            if (longItems.isNotEmpty()) {
                val index = MemoryIndex()
                longItems.forEach { index.add(it) }
                longTermIndex[key] = index
                // 同时缓存长期记忆内容供查询
                midTermCache[key]?.addAll(0, longItems) // 长期记忆优先
            }

            // 加载索引
            store.loadIndex(scope, id)?.let { serialized ->
                val index = MemoryIndex()
                val allItems = (longItems + midItems).associateBy { it.id }
                index.deserialize(serialized, allItems)
                indexCache[key] = index
            }
        }.onFailure { Log.e(TAG, "加载作用域 $key 失败", it) }
    }

    /**
     * 启动定时清理任务
     */
    private fun startCleanupTask() {
        ioScope.launch {
            while (true) {
                delay(CLEANUP_INTERVAL_MS)
                runCatching { cleanupExpiredMemories() }
                    .onFailure { Log.e(TAG, "清理任务失败", it) }
            }
        }
    }

    /**
     * 清理过期短期记忆 + LRU淘汰
     */
    private fun cleanupExpiredMemories() {
        val now = System.currentTimeMillis()
        shortTermCache.forEach { (key, items) ->
            synchronized(items) {
                items.removeAll { it.isExpired(now) }
                // LRU淘汰：超过容量时移除最旧的
                if (items.size > SHORT_TERM_MAX_PER_SCOPE) {
                    items.sortBy { it.lastAccessed }
                    val toRemove = items.size - SHORT_TERM_MAX_PER_SCOPE
                    repeat(toRemove) { items.removeAt(0) }
                }
            }
        }

        // 中期记忆全局LRU淘汰
        // [R10 FIX] 用快照遍历 + synchronized 保护，避免与 touchMemory/saveMemory 并发时 CME
        val midSnapshot = midTermCache.entries.toList()
        val totalMid = midSnapshot.sumOf { it.value.size }
        if (totalMid > MID_TERM_MAX_MEMORY) {
            // 按最后访问时间排序，淘汰最久未访问的
            val allMid = midSnapshot
                .flatMap { (key, items) ->
                    synchronized(items) { items.toList() }.map { key to it }
                }
                .sortedBy { it.second.lastAccessed }
            val toRemoveCount = totalMid - MID_TERM_MAX_MEMORY
            allMid.take(toRemoveCount).forEach { (key, item) ->
                midTermCache[key]?.let { items ->
                    synchronized(items) { items.removeAll { it.id == item.id } }
                }
            }
        }
    }

    /**
     * 获取记忆上下文（注入AI对话）
     *
     * @param companionId 角色ID（单聊时传入，群聊时为null）
     * @param groupId 群聊ID（群聊时传入，单聊时为null）
     * @param query 查询文本（当前用户消息）
     * @param limit 返回记忆数量限制
     * @return 格式化的记忆上下文字符串
     */
    override suspend fun getMemoryContext(
        companionId: Long?,
        groupId: Long?,
        query: String,
        limit: Int
    ): String {
        return runCatching {
            val memories = mutableListOf<MemoryItem>()

            // 1. 始终查询全局记忆
            memories.addAll(searchMemories(MemoryScope.GLOBAL, 0L, query, limit))

            // 2. 查询角色/群聊记忆
            when {
                companionId != null -> {
                    memories.addAll(searchMemories(MemoryScope.COMPANION, companionId, query, limit))
                }
                groupId != null -> {
                    memories.addAll(searchMemories(MemoryScope.GROUP, groupId, query, limit))
                }
            }

            // 去重 + 按重要度排序
            val deduped = memories.distinctBy { it.id }
                .sortedByDescending { it.importance }
                .take(limit)

            // 更新访问信息
            deduped.forEach { touchMemory(it) }

            formatMemoryContext(deduped)
        }.onFailure { Log.e(TAG, "获取记忆上下文失败", it) }
            .getOrElse { "" }
    }

    /**
     * 搜索记忆（分层查询：短期→中期→长期）
     */
    private suspend fun searchMemories(
        scope: MemoryScope,
        id: Long,
        query: String,
        limit: Int
    ): List<MemoryItem> {
        val key = scopeKey(scope, id)
        val result = mutableListOf<MemoryItem>()
        val resultIds = mutableSetOf<String>()

        // 查询缓存
        val cacheKey = "$key:$query:$limit"
        synchronized(queryCache) {
            queryCache[cacheKey]?.let { return it }
        }

        val queryEmbedding = runCatching { embeddingProvider.embed(query) }.getOrNull()?.toList()

        // 1. 搜索短期记忆
        shortTermCache[key]?.let { items ->
            synchronized(items) {
                val matched = MemoryIndex().apply {
                    items.forEach { add(it) }
                }.search(query, queryEmbedding, limit = limit)
                items.filter { it.id in matched }.forEach {
                    if (it.id !in resultIds) {
                        result.add(it)
                        resultIds.add(it.id)
                    }
                }
            }
        }

        // 2. 搜索中期+长期记忆（通过索引）
        val index = indexCache[key] ?: MemoryIndex()
        if (result.size < limit) {
            val matchedIds = index.search(query, queryEmbedding, limit = limit - result.size)
            // 从内存缓存加载
            midTermCache[key]?.let { cache ->
                cache.filter { it.id in matchedIds }.forEach {
                    if (it.id !in resultIds) {
                        result.add(it)
                        resultIds.add(it.id)
                    }
                }
            }
            // 如果内存缓存未命中，从磁盘加载长期记忆
            if (result.size < limit) {
                val stillNeeded = matchedIds.filter { it !in resultIds }
                if (stillNeeded.isNotEmpty()) {
                    val longItems = store.loadTier(scope, id, MemoryTier.LONG)
                    longItems.filter { it.id in stillNeeded }.forEach {
                        if (it.id !in resultIds) {
                            result.add(it)
                            resultIds.add(it.id)
                        }
                    }
                }
            }
        }

        // 更新查询缓存
        synchronized(queryCache) {
            if (queryCache.size > 32) {
                queryCache.remove(queryCache.keys.first())
            }
            queryCache[cacheKey] = result.toList()
        }

        return result
    }

    /**
     * 格式化记忆上下文
     */
    private fun formatMemoryContext(memories: List<MemoryItem>): String {
        if (memories.isEmpty()) return ""
        return buildString {
            append("\n=== 关于用户的记忆 ===\n")
            memories.forEach { item ->
                val categoryLabel = when (item.category) {
                    MemoryCategory.FACT -> "事实"
                    MemoryCategory.EMOTION -> "情感"
                    MemoryCategory.PREFERENCE -> "偏好"
                    MemoryCategory.EVENT -> "事件"
                    MemoryCategory.HABIT -> "习惯"
                    MemoryCategory.RELATIONSHIP -> "关系"
                }
                append("【$categoryLabel】${item.content}\n")
            }
        }
    }

    /**
     * 更新记忆访问信息
     */
    private fun touchMemory(item: MemoryItem) {
        val now = System.currentTimeMillis()
        val touched = item.touch(now)

        // 更新短期记忆缓存
        val key = scopeKey(item.scope, item.sourceId)
        shortTermCache[key]?.let { items ->
            synchronized(items) {
                val idx = items.indexOfFirst { it.id == item.id }
                if (idx >= 0) items[idx] = touched
            }
        }

        // 更新中期记忆缓存
        midTermCache[key]?.let { items ->
            synchronized(items) {
                val idx = items.indexOfFirst { it.id == item.id }
                if (idx >= 0) items[idx] = touched
            }
        }

        // 更新索引
        indexCache[key]?.touch(item.id)
    }

    /**
     * 保存记忆
     *
     * @param content 记忆内容
     * @param category 记忆类别
     * @param importance 重要度 [0.0, 1.0]
     * @param source 来源
     * @param sourceId 来源ID（companionId 或 groupId）
     * @param scope 作用域
     * @return 记忆ID（null表示保存失败或被去重合并）
     */
    suspend fun saveMemory(
        content: String,
        category: MemoryCategory,
        importance: Float,
        source: MemorySource,
        sourceId: Long,
        scope: MemoryScope
    ): String? {
        val key = scopeKey(scope, sourceId)
        return getLock(scope, sourceId).withLock {
            runCatching {
                // 去重检查
                val existing = findSimilar(key, content)
                if (existing != null) {
                    // 合并：更新重要度和访问次数
                    val merged = existing.copy(
                        importance = maxOf(existing.importance, importance),
                        accessCount = existing.accessCount + 1,
                        lastAccessed = System.currentTimeMillis()
                    )
                    updateMemoryInternal(scope, sourceId, merged)
                    _memoriesChanged.tryEmit(key)
                    return@runCatching existing.id
                }

                // 创建新记忆
                val now = System.currentTimeMillis()
                val computedEmbedding = runCatching { embeddingProvider.embed(content) }.getOrNull()
                // 事实/偏好/关系类，或重要度较高的记忆，属于"应该被长期记住"的核心信息
                // （比如"我是女生"），不该只给 5 分钟寿命就被清理任务删掉；
                // 直接跳过短期层，进中期存储且不设过期时间。
                val isDurable = scope == MemoryScope.GLOBAL ||
                    category in setOf(MemoryCategory.FACT, MemoryCategory.PREFERENCE, MemoryCategory.RELATIONSHIP) ||
                    importance >= 0.7f
                val item = MemoryItem(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    category = category,
                    importance = importance.coerceIn(0f, 1f),
                    timestamp = now,
                    lastAccessed = now,
                    source = source,
                    sourceId = sourceId,
                    scope = scope,
                    tags = MemoryTokenizer.extractKeywords(content),
                    expireAt = if (isDurable) null else now + SHORT_TERM_TTL_MS,
                    tier = if (isDurable) MemoryTier.MID else MemoryTier.SHORT,
                    embedding = computedEmbedding?.toList()
                )

                // 添加到短期记忆
                shortTermCache.computeIfAbsent(key) { mutableListOf() }
                    .let { items ->
                        synchronized(items) {
                            items.add(item)
                            // 超容量时晋级最旧的到中期
                            if (items.size > SHORT_TERM_MAX_PER_SCOPE) {
                                val toPromote = items.removeAt(0)
                                promoteToMid(scope, sourceId, toPromote)
                            }
                        }
                    }

                // 更新索引
                indexCache.computeIfAbsent(key) { MemoryIndex() }.add(item)

                // 同步到全局池（如果符合条件）
                if (scope != MemoryScope.GLOBAL && shouldSyncToGlobal(category, importance)) {
                    syncToGlobal(item)
                }

                // 立即同步持久化（不依赖任何后台协程/生命周期回调是否来得及执行，
                // 从根本上消除"划掉后台瞬间进程被杀、写盘还没发生"的竞态）
                persistNow(scope, sourceId)

                // [R9 FIX] 写入后清除查询缓存：原 queryCache 写入后不清除，saveMemory 后同 key
                // 查询返回旧快照，新记忆最多被 32 条查询掩盖。
                invalidateQueryCache()

                item.id
            }.onFailure { Log.e(TAG, "保存记忆失败", it) }
                .getOrNull()
        }
    }

    /**
     * [R9 FIX] 清除查询缓存：在任何写入操作后调用，保证后续查询读到最新数据。
     */
    private fun invalidateQueryCache() {
        synchronized(queryCache) {
            queryCache.clear()
        }
    }

    /**
     * 查找相似记忆（用于去重）
     */
    private fun findSimilar(key: String, content: String): MemoryItem? {
        val allItems = mutableListOf<MemoryItem>()
        shortTermCache[key]?.let { allItems.addAll(it) }
        midTermCache[key]?.let { allItems.addAll(it) }

        return allItems.firstOrNull { existing ->
            MemoryTokenizer.similarity(existing.content, content) > DEDUP_SIMILARITY_THRESHOLD
        }
    }

    /**
     * 判断是否应同步到全局池
     */
    private fun shouldSyncToGlobal(category: MemoryCategory, importance: Float): Boolean {
        if (importance < SYNC_IMPORTANCE_THRESHOLD) return false
        // EMOTION 和 EVENT 不同步到全局（角色/会话特定）
        return category !in setOf(MemoryCategory.EMOTION, MemoryCategory.EVENT)
    }

    /**
     * 同步到全局池
     */
    private suspend fun syncToGlobal(item: MemoryItem) {
        val globalItem = item.copy(
            id = UUID.randomUUID().toString(),
            scope = MemoryScope.GLOBAL,
            sourceId = 0L,
            tier = MemoryTier.MID,
            expireAt = null // 全局记忆不过期
        )

        val globalKey = scopeKey(MemoryScope.GLOBAL, 0L)
        shortTermCache.computeIfAbsent(globalKey) { mutableListOf() }
            .let { items ->
                synchronized(items) { items.add(globalItem) }
            }
        indexCache.computeIfAbsent(globalKey) { MemoryIndex() }.add(globalItem)
        schedulePersist(MemoryScope.GLOBAL, 0L)
    }

    /**
     * 短期记忆晋级到中期
     */
    private fun promoteToMid(scope: MemoryScope, id: Long, item: MemoryItem) {
        val key = scopeKey(scope, id)
        val promoted = item.copy(
            tier = MemoryTier.MID,
            expireAt = null
        )
        midTermCache.computeIfAbsent(key) { mutableListOf() }
            .let { items ->
                synchronized(items) { items.add(promoted) }
            }
        schedulePersist(scope, id)
    }

    /**
     * 中期记忆晋级到长期
     */
    private suspend fun promoteToLong(scope: MemoryScope, id: Long, item: MemoryItem) {
        val key = scopeKey(scope, id)
        val promoted = item.copy(tier = MemoryTier.LONG)

        // 从中期缓存移除
        midTermCache[key]?.let { items ->
            synchronized(items) { items.removeAll { it.id == item.id } }
        }

        // 写入长期存储
        val longItems = store.loadTier(scope, id, MemoryTier.LONG).toMutableList()
        longItems.add(promoted)
        store.saveTier(scope, id, MemoryTier.LONG, longItems)

        // 更新长期索引
        longTermIndex.computeIfAbsent(key) { MemoryIndex() }.add(promoted)
        indexCache[key]?.add(promoted)

        schedulePersist(scope, id)
    }

    /**
     * 更新记忆（内部）
     */
    private fun updateMemoryInternal(scope: MemoryScope, sourceId: Long, item: MemoryItem) {
        _memoriesChanged.tryEmit(scopeKey(scope, sourceId))
        val key = scopeKey(scope, sourceId)

        // 更新短期记忆
        shortTermCache[key]?.let { items ->
            synchronized(items) {
                val idx = items.indexOfFirst { it.id == item.id }
                if (idx >= 0) items[idx] = item
            }
        }
        // 更新中期记忆
        midTermCache[key]?.let { items ->
            synchronized(items) {
                val idx = items.indexOfFirst { it.id == item.id }
                if (idx >= 0) items[idx] = item
            }
        }

        // 检查是否应晋级到长期
        if (item.shouldPromoteToLong() && item.tier != MemoryTier.LONG) {
            ioScope.launch { promoteToLong(scope, sourceId, item) }
        }

        schedulePersist(scope, sourceId)
        // [R9 FIX] 更新后也清除查询缓存
        invalidateQueryCache()
    }

/**
     * 立即（同步）把所有当前在内存里但还没写盘的记忆刷到磁盘。
     * schedulePersist() 只是把落盘任务丢进 ioScope 异步执行，如果 App 在任务
     * 真正执行前就被系统杀掉进程（比如用户划掉后台），这次写入就彻底丢了——
     * 这也是"核心记忆"关闭 App 后消失的根因。这个方法在 App 进入后台时
     * （LianYuApplication.onTrimMemory）同步调用一次，确保杀进程前已经落盘。
     */
    fun flushAllPending() {
        val keys = shortTermCache.keys + midTermCache.keys + indexCache.keys
        keys.toSet().forEach { key ->
            runCatching {
                val id = key.substringAfterLast("_").toLongOrNull() ?: return@forEach
                val scope = MemoryScope.valueOf(key.substringBeforeLast("_"))
                val shortItems = shortTermCache[key]?.toList() ?: emptyList()
                val midItems = midTermCache[key]?.toList() ?: emptyList()
                store.saveTier(scope, id, MemoryTier.SHORT, shortItems)
                store.saveTier(scope, id, MemoryTier.MID, midItems)
                indexCache[key]?.let { index -> store.saveIndex(scope, id, index.serialize()) }
            }.onFailure { Log.e(TAG, "flushAllPending 失败 key=$key", it) }
        }
    }

    /**
     * 同步持久化：调用方 suspend 挂起直到真正写盘完成再返回。
     * 用在"新建记忆"这种不能接受丢失的关键路径上，避免依赖 ioScope 异步任务
     * 是否来得及在进程被杀之前跑完。
     */
    private suspend fun persistNow(scope: MemoryScope, id: Long) = withContext(Dispatchers.IO) {
        val key = scopeKey(scope, id)
        runCatching {
            val shortItems = shortTermCache[key]?.toList() ?: emptyList()
            val midItems = midTermCache[key]?.toList() ?: emptyList()

            store.saveTier(scope, id, MemoryTier.SHORT, shortItems)
            store.saveTier(scope, id, MemoryTier.MID, midItems)

            indexCache[key]?.let { index ->
                store.saveIndex(scope, id, index.serialize())
            }
        }.onFailure { Log.e(TAG, "持久化失败 scope=$key", it) }
    }

    /**
     * 调度异步持久化
     */
    private fun schedulePersist(scope: MemoryScope, id: Long) {
        val key = scopeKey(scope, id)
        ioScope.launch {
            runCatching {
                val shortItems = shortTermCache[key]?.toList() ?: emptyList()
                val midItems = midTermCache[key]?.toList() ?: emptyList()

                store.saveTier(scope, id, MemoryTier.SHORT, shortItems)
                store.saveTier(scope, id, MemoryTier.MID, midItems)

                indexCache[key]?.let { index ->
                    store.saveIndex(scope, id, index.serialize())
                }
            }.onFailure { Log.e(TAG, "持久化失败 scope=$key", it) }
        }
    }

    /**
     * 获取作用域下所有记忆（用于UI展示）
     */
    suspend fun getMemories(scope: MemoryScope, id: Long): List<MemoryItem> {
        val key = scopeKey(scope, id)
        val result = mutableListOf<MemoryItem>()

        shortTermCache[key]?.let { result.addAll(it) }
        midTermCache[key]?.let { result.addAll(it) }

        // 加载长期记忆
        result.addAll(store.loadTier(scope, id, MemoryTier.LONG))

        return result.sortedByDescending { it.timestamp }
    }

    /**
     * 删除记忆
     */
    suspend fun deleteMemory(scope: MemoryScope, sourceId: Long, id: String) {
        deleteMemoryFromScope(scope, sourceId, id)
    }

    suspend fun updateMemory(scope: MemoryScope, sourceId: Long, oldId: String, newContent: String, newCategory: MemoryCategory, newImportance: Float): String? {
        deleteMemoryFromScope(scope, sourceId, oldId)
        return saveMemory(newContent, newCategory, newImportance, MemorySource.MANUAL, sourceId, scope)
    }

    /**
     * 从指定作用域删除记忆
     */
    private suspend fun deleteMemoryFromScope(scope: MemoryScope, sourceId: Long, id: String) {
        val key = scopeKey(scope, sourceId)
        var deleted = false

        shortTermCache[key]?.let { items ->
            synchronized(items) { deleted = items.removeAll { it.id == id } || deleted }
        }
        midTermCache[key]?.let { items ->
            synchronized(items) { deleted = items.removeAll { it.id == id } || deleted }
        }
        indexCache[key]?.remove(id)

        if (deleted) {
            _memoriesChanged.tryEmit(key)
            schedulePersist(scope, sourceId)
            // [R9 FIX] 删除后也清除查询缓存
            invalidateQueryCache()
        }

        // 也检查长期记忆文件
        val longItems = store.loadTier(scope, sourceId, MemoryTier.LONG).toMutableList()
        if (longItems.removeAll { it.id == id }) {
            store.saveTier(scope, sourceId, MemoryTier.LONG, longItems)
            longTermIndex[key]?.remove(id)
        }
    }

    /**
     * 删除整个作用域的所有记忆
     */
    suspend fun deleteAllMemories(scope: MemoryScope, id: Long) {
        val key = scopeKey(scope, id)
        shortTermCache.remove(key)
        midTermCache.remove(key)
        indexCache.remove(key)
        longTermIndex.remove(key)
        store.deleteScope(scope, id)
    }

    /**
     * 从对话中提取并保存记忆
     *
     * @param userInput 用户输入
     * @param aiResponse AI回复
     * @param companionId 角色ID
     * @param groupId 群聊ID（群聊时传入）
     */
    override suspend fun extractAndSaveFromConversation(
        userInput: String,
        aiResponse: String,
        companionId: Long,
        groupId: Long?
    ) {
        runCatching {
            val scope = if (groupId != null) MemoryScope.GROUP else MemoryScope.COMPANION
            val sourceId = if (groupId != null) groupId else companionId
            val source = if (groupId != null) MemorySource.GROUP_CHAT else MemorySource.CHAT

            val extracted = extractMemories(userInput)
            extracted.forEach { (content, category, importance) ->
                saveMemory(content, category, importance, source, sourceId, scope)
            }
        }.onFailure { Log.e(TAG, "提取记忆失败", it) }
    }

    /**
     * 基于关键词的记忆提取
     * 改进版：支持中文，覆盖更多类别
     */
    /**
 * 简单判断是否为疑问句，避免把"我是男生还是女生"这类提问误存为事实
 */
private fun isLikelyQuestion(text: String): Boolean {
    val t = text.trim()
    if (t.endsWith("?") || t.endsWith("？")) return true
    if (t.endsWith("吗") || t.endsWith("呢")) return true
    val markers = listOf(
        "还是", "多少", "怎么样", "怎么", "为什么", "什么时候", "什么",
        "谁", "哪", "如何", "几点", "几岁", "是不是", "有没有", "对不对", "好不好"
    )
    return markers.any { t.contains(it) }
}
    private fun extractMemories(text: String): List<Triple<String, MemoryCategory, Float>> {
        val result = mutableListOf<Triple<String, MemoryCategory, Float>>()

        // 事实类
        val factPatterns = listOf("我叫", "我是", "我来自", "我在", "我的名字", "我住", "我工作", "我学", "我的职业")
        factPatterns.forEach { pattern ->
            if (text.contains(pattern)) {
                extractAfterPattern(text, pattern)?.let {
                    if (!isLikelyQuestion(it)) result.add(Triple(it, MemoryCategory.FACT, 0.8f))
                }
            }
        }

        // 偏好类
        val preferencePatterns = listOf("我喜欢", "我爱好", "我偏爱", "我讨厌", "我不喜欢", "我反感", "我爱", "我恨")
        preferencePatterns.forEach { pattern ->
            if (text.contains(pattern)) {
                extractAfterPattern(text, pattern)?.let {
                    if (!isLikelyQuestion(it)) result.add(Triple(it, MemoryCategory.PREFERENCE, 0.75f))
                }
            }
        }

        // 习惯类
        val habitPatterns = listOf("我每天", "我经常", "我总是", "我通常", "我习惯", "我一般")
        habitPatterns.forEach { pattern ->
            if (text.contains(pattern)) {
                extractAfterPattern(text, pattern)?.let {
                    if (!isLikelyQuestion(it)) result.add(Triple(it, MemoryCategory.HABIT, 0.7f))
                }
            }
        }

        // 关系类
        val relationshipPatterns = listOf("我的朋友", "我的家人", "我的父母", "我的同学", "我的同事", "我的男朋友", "我的女朋友")
        relationshipPatterns.forEach { pattern ->
            if (text.contains(pattern)) {
                extractAfterPattern(text, pattern)?.let {
                    if (!isLikelyQuestion(it)) result.add(Triple(it, MemoryCategory.RELATIONSHIP, 0.8f))
                }
            }
        }

        return result.distinctBy { it.first }
    }

    /**
     * 从模式后提取内容（到句号或换行）
     */
    private fun extractAfterPattern(text: String, pattern: String): String? {
        val idx = text.indexOf(pattern)
        if (idx < 0) return null
        val start = idx + pattern.length
        val endText = text.substring(start)
        val endIdx = endText.indexOfFirst { it in "。，！？；\n" }
        val content = if (endIdx > 0) endText.substring(0, endIdx) else endText.take(50)
        val full = "$pattern$content".trim()
        return if (full.length > pattern.length + 1) full else null
    }
}

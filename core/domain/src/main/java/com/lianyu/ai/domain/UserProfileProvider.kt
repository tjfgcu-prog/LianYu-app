package com.lianyu.ai.domain

/**
 * Provides user profile information to features that need it.
 * Implemented by feature:profile (or app-level bridge).
 *
 * 注意：此接口属于 core:domain 零依赖模块，不得引入 kotlinx.coroutines 等外部依赖。
 * 头像变化监听采用回调模式，返回取消订阅的函数。
 */
interface UserProfileProvider {
    fun getUserId(): String
    fun getNickname(): String
    fun getAvatar(): String?
        /**
     * 提供给提示词的"用户信息"文本（已合并角色专属设定）。
     * @param companionId 单聊传角色ID；群聊传 null（只用"我的信息"）
     * @return 无任何已填项时返回空串
     */
    fun getSelfProfilePrompt(companionId: Long?): String
    
    /**
     * 观察头像变化。调用方在协程中订阅，[onChange] 每次头像更新时回调。
     * @return 取消订阅的函数，调用后不再收到回调
     */
    fun observeAvatar(onChange: (String?) -> Unit): () -> Unit

    fun isLoggedIn(): Boolean
}

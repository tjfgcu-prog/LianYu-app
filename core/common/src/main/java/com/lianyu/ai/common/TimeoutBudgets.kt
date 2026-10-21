package com.lianyu.ai.common

/**
 * 时滞预算常量 — 每个异步操作的期望和最大时滞。
 * 
 * 控制论原则: 时滞 × 请求速率 = 在途请求数。
 * 时滞 > 波动周期时系统必然振荡。
 */
object TimeoutBudgets {
    

    // === 网络操作 ===
    const val TTS_SYNTH_MS = 10_000L         // TTS合成 (10s)
    
    // [M11 FIX] ChatViewModel 使用的实际超时值（OkHttp callTimeout 对齐，网络慢时需更长）
    const val CHAT_VM_API_TIMEOUT_MS = 30_000L      // ChatViewModel AI 调用超时
    const val CHAT_VM_LOCAL_MODEL_TIMEOUT_MS = 180_000L  // ChatViewModel 本地GGUF模型调用超时时（含加载耗时，给足3分钟）
    const val CHAT_VM_VISION_TIMEOUT_MS = 60_000L   // ChatViewModel 视觉调用超时
    
    const val CHAT_VM_MEMORY_EXTRACT_MS = 5_000L    // ChatViewModel 记忆提取超时
    const val STORY_SUMMARY_LOCAL_MS = 120_000L     // 剧情摘要（本地模型；须小于 CHAT_VM_LOCAL_MODEL_TIMEOUT_MS）
    const val STORY_SUMMARY_API_MS = 60_000L        // 剧情摘要（云端模型）
    
    const val CHAT_VM_BATCH_WINDOW_MS = 2_500L      // ChatViewModel 批量合并窗口
    
    const val API_CONFIG_WAIT_MS = 1_500L      // 冷启动等待 API 配置加载（竞态窗口）
    

    // === HTTP 客户端通用超时（OkHttp connectTimeout/readTimeout/writeTimeout/pingInterval） ===
    const val HTTP_CONNECT_MS = 10_000L        // TCP 连接建立
    const val HTTP_READ_MS = 30_000L           // 读响应（对齐 API_CHAT_MS 的 2x）
    const val HTTP_WRITE_MS = 10_000L          // 写请求体
    const val HTTP_PING_MS = 30_000L           // HTTP/2 ping 间隔（保活）

    

    // === AI 工具调用 (MCP / JSON-RPC over Streamable HTTP) ===
    const val MCP_READ_MS = 30_000L          // MCP 读取超时（普通 JSON 响应）
    
    
}

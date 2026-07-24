package com.lianyu.ai.feature.chat.ui.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ChatViewModel 消息流水线实现
 */
class MessagePipelineRunner : MessagePipeline {

    private val _pipelineState = MutableStateFlow(MessagePipeline.PipelineState())
    override val pipelineState: StateFlow<MessagePipeline.PipelineState> = _pipelineState

    private val _queueDepth = MutableStateFlow(0)
    override val queueDepth: StateFlow<Int> = _queueDepth

    override suspend fun execute(input: MessagePipeline.PipelineInput): Boolean {
        return true
    }
}

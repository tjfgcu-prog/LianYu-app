package com.lianyu.ai.feature.chat.ui.viewmodel

import com.lianyu.ai.common.StickerInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex

/**
 * Encapsulates per-turn mutable state for a chat conversation.
 *
 * Extracted from ChatViewModel to give explicit lifecycle to previously
 * scattered mutable fields: sticker state, send job, and concurrency mutex.
 */
class ChatTurnState {
    /** Whether a sticker was already sent during this turn. */
    var stickerSentThisTurn: Boolean = false

    /** Sticker queued by TextProcessor but not yet persisted. Used to randomize sticker/text order. */
    var pendingSticker: StickerInfo? = null

    /** Active message-sending coroutine job, cancelled when a new message starts. */
    var sendMessageJob: Job? = null

    /** Mutex for atomic sticker send operations. */
    val stickerMutex: Mutex = Mutex()

    /** Reset turn state for a new message. */
    fun reset() {
        stickerSentThisTurn = false
        pendingSticker = null
        
    }



    

}

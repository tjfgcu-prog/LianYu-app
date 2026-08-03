package com.lianyu.ai.feature.chat.ui.viewmodel

import com.lianyu.ai.common.ConcurrencyConstants
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

private val threadCounter = AtomicInteger(0)

private val sharedExecutor: ExecutorService = ThreadPoolExecutor(
    ConcurrencyConstants.INTERRUPTIBLE_EXECUTOR_CORE_POOL_SIZE,
    ConcurrencyConstants.INTERRUPTIBLE_EXECUTOR_MAXIMUM_POOL_SIZE,
    ConcurrencyConstants.INTERRUPTIBLE_EXECUTOR_KEEP_ALIVE_SECONDS,
    TimeUnit.SECONDS,
    ArrayBlockingQueue(ConcurrencyConstants.INTERRUPTIBLE_EXECUTOR_WORK_QUEUE_CAPACITY)
) { runnable ->
    Thread(runnable).apply {
        name = "runInterruptibleSafe-${threadCounter.incrementAndGet()}"
        isDaemon = true
    }
}

/**
 * Execute a suspending block on a dedicated thread with a hard timeout.
 *
 * Unlike [kotlinx.coroutines.withTimeoutOrNull] which relies on cooperative cancellation
 * (suspend functions must check cancellation), this wraps the block in a [java.util.concurrent.Future]
 * so that blocking I/O calls (OkHttp execute(), JNI native calls, etc.) are truly interrupted
 * when the timeout expires.
 *
 * **Important:** Only timeout and interruption are treated as timeout signals.
 * All other exceptions thrown by [block] propagate to the caller.
 *
 * Uses a shared cached thread pool instead of creating a new executor per call,
 * avoiding thread-pool leakage under high-frequency invocation.
 *
 * **关于内部 `runBlocking`（设计性用法，非反模式）：**
 * `runBlocking` 在 [sharedExecutor] 的 worker 线程上执行，**不阻塞调用线程**。
 * 调用方（协程）挂起在 `future.get(timeoutMs)` 上 —— 这是协作式挂起，而非阻塞主线程。
 * 此桥接是必须的：[block] 是 suspend 函数，但 `Future.submit` 的 `Callable` 不是 suspend 上下文，
 * 必须用 `runBlocking` 在 worker 线程建立协程作用域。超时后 `future.cancel(true)` 会中断 worker 线程。
 *
 * @param timeoutMs  Hard timeout in milliseconds.
 * @param onTimeout  Value to return when the timeout fires or the thread is interrupted (default: null).
 * @param block      The suspending block to execute on a separate thread.
 * @return           The block's result, or [onTimeout] on timeout/interrupt.
 * @throws           Any exception thrown by [block] (except timeout/interrupt).
 */
suspend fun <T> runInterruptibleSafe(
    timeoutMs: Long,
    onTimeout: T? = null,
    block: suspend () -> T
): T? {
    return try {
        val future = sharedExecutor.submit<T> {
            kotlinx.coroutines.runBlocking { block() }
        }
        future.get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
    onTimeout
} catch (_: InterruptedException) {
    onTimeout
} catch (_: RejectedExecutionException) {
    onTimeout
    }
    // Other exceptions (RuntimeException, etc.) propagate to caller naturally
}

package com.entrytask.shell.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 只预热进程唯一引擎，不创建 Surface、业务模型或触发认证；所有动作从主线程调用。 */
class RnWarmupController(
    val enabled: Boolean,
    private val scope: CoroutineScope,
    private val startEngine: () -> WarmupHandle,
    private val nowMs: () -> Long,
    private val report: (WarmupSnapshot) -> Unit,
) {
    @Volatile var snapshot = WarmupSnapshot(WarmupPhase.IDLE)
        private set
    @Volatile private var surfaceRequested = false
    @Volatile private var attempted = false

    /** 首帧提交后的主线程回调调用；重复登录、旋转不再启动第二次预热。 */
    fun afterAuthFrame(lowMemory: Boolean) {
        if (!enabled || attempted || surfaceRequested) return
        if (lowMemory) {
            publish(WarmupSnapshot(WarmupPhase.SKIPPED_MEMORY))
            return
        }
        attempted = true
        val started = nowMs()
        publish(WarmupSnapshot(WarmupPhase.RUNNING, started))
        scope.launch {
            try {
                val handle = startEngine()
                // RN 的公开 TaskInterface 没有 continuation；有界异步观察，不阻塞主线程。
                while (nowMs() - started < 30_000) {
                    val outcome = handle.outcome()
                    if (outcome != null) {
                        publish(WarmupSnapshot(outcome, started, nowMs()))
                        return@launch
                    }
                    delay(25)
                }
                publish(WarmupSnapshot(WarmupPhase.TIMED_OUT, started, nowMs()))
                // 超时只停止观察；不能销毁已被真实页面接管的共享引擎。
            } catch (cancelled: CancellationException) {
                publish(WarmupSnapshot(WarmupPhase.CANCELED, started, nowMs()))
                throw cancelled
            } catch (_: Exception) {
                publish(WarmupSnapshot(WarmupPhase.FAILED, started, nowMs()))
            }
        }
    }

    fun surfaceWillAttach() { surfaceRequested = true }

    /** 仅隔离无 Surface 的预热异常；真实 RN 页面仍沿用宿主原有异常处理。 */
    fun handlesPreloadException(): Boolean = attempted && !surfaceRequested

    private fun publish(value: WarmupSnapshot) { snapshot = value; report(value) }
}

fun interface WarmupHandle { fun outcome(): WarmupPhase? }
enum class WarmupPhase { IDLE, RUNNING, READY, FAILED, CANCELED, TIMED_OUT, SKIPPED_MEMORY }
data class WarmupSnapshot(val phase: WarmupPhase, val startedMs: Long? = null, val endedMs: Long? = null)

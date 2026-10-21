package com.entrytask.shell.data

import com.entrytask.shell.domain.NativeSessionPort
import com.entrytask.shell.domain.ShellRepository
import com.entrytask.shell.domain.ShellSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 进程级 Shell 状态。生产调用在主线程串行执行，锁保证原子快照和测试并发安全。 */
class DefaultShellRepository(private val session: NativeSessionPort) : ShellRepository {
    private val initial = session.current()
    private val state = MutableStateFlow(ShellSnapshot(
        authenticated = initial.authenticated, sessionEpoch = initial.epoch,
    ))
    override val snapshots = state.asStateFlow()
    private var selectionEstablished = false

    @Synchronized override fun selectTab(tabId: Int) {
        require(tabId in 1..4)
        selectionEstablished = true
        publish(state.value.copy(tabId = tabId))
    }

    @Synchronized override fun updateDepth(depth: Int) {
        require(depth in 0..20)
        publish(state.value.copy(depth = depth))
    }

    @Synchronized override fun refreshSession(forcePublish: Boolean) {
        val next = session.current()
        if (next.epoch < state.value.sessionEpoch) return
        val invalidated = state.value.authenticated && !next.authenticated
        publish(state.value.copy(
            authenticated = next.authenticated,
            sessionEpoch = next.epoch,
            tabId = if (invalidated) 1 else state.value.tabId,
            depth = if (invalidated) 0 else state.value.depth,
        ), forcePublish)
    }

    @Synchronized override fun restoreSelection(tabId: Int?) {
        // 同进程已有选择优先于旧 Bundle；新进程无会话时只恢复首页门禁。
        if (selectionEstablished) return
        selectionEstablished = true
        val selected = if (state.value.authenticated && tabId in 1..4) tabId!! else 1
        publish(state.value.copy(tabId = selected, depth = 0))
    }

    @Synchronized override fun logout() {
        session.logout()
        selectionEstablished = true
        val next = session.current()
        publish(state.value.copy(tabId = 1, depth = 0,
            authenticated = next.authenticated, sessionEpoch = next.epoch))
    }

    private fun publish(next: ShellSnapshot, force: Boolean = false) {
        if (!force && next == state.value) return
        state.value = next.copy(revision = state.value.revision + 1)
    }
}

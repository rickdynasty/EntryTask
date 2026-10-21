package com.entrytask.shell.domain

import kotlinx.coroutines.flow.StateFlow

/** Native 会话的安全快照；凭证始终留在认证模块。 */
data class SessionSnapshot(val authenticated: Boolean, val epoch: Long)

interface NativeSessionPort {
    fun current(): SessionSnapshot
    fun logout()
}

/** Shell 事实只有这一份；XML View 和 RN 都消费它的投影。 */
data class ShellSnapshot(
    val tabId: Int = 1,
    val depth: Int = 0,
    val authenticated: Boolean = false,
    val sessionEpoch: Long = 0,
    val revision: Long = 0,
)

interface ShellRepository {
    val snapshots: StateFlow<ShellSnapshot>
    fun selectTab(tabId: Int)
    fun updateDepth(depth: Int)
    fun refreshSession(forcePublish: Boolean = false)
    fun restoreSelection(tabId: Int?)
    fun logout()
}

/** Bridge 在边界解码后只传类型化命令，不让 JSONObject 进入 UI/VM。 */
sealed interface ShellCommand {
    data class SelectTab(val tabId: Int) : ShellCommand
    data class Navigation(val depth: Int) : ShellCommand
    data object Authenticate : ShellCommand
    data object Logout : ShellCommand
}

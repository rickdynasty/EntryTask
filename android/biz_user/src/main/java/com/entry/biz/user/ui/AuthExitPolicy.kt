package com.entry.biz.user.ui

/** 登录根页的双击退出窗口独立于首页，使用单调时钟，不共享全局点击计数。 */
internal class AuthExitPolicy(private val nowMs: () -> Long, private val intervalMs: Long = 2000) {
    private var lastBackMs: Long? = null
    fun reset() { lastBackMs = null }
    fun shouldExit(): Boolean {
        val now = nowMs()
        val previous = lastBackMs
        if (previous != null && now - previous in 0 until intervalMs) {
            reset()
            return true
        }
        lastBackMs = now
        return false
    }
}

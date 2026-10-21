package com.entrytask.shell.domain

enum class RootBackAction { Resynchronize, SwitchToHome, ShowExitHint, Exit }

/** 可测试的根返回策略；计时使用单调时钟，窗口保持原有 500ms。 */
class RootBackPolicy(private val nowMs: () -> Long, private val intervalMs: Long = 500) {
    private var lastPress: Long? = null
    fun reset() { lastPress = null }
    fun decide(snapshot: ShellSnapshot): RootBackAction {
        if (snapshot.depth > 0) { reset(); return RootBackAction.Resynchronize }
        if (snapshot.tabId != 1) { reset(); return RootBackAction.SwitchToHome }
        val now = nowMs()
        val previous = lastPress
        if (previous != null && now - previous in 0 until intervalMs) {
            reset()
            return RootBackAction.Exit
        }
        lastPress = now
        return RootBackAction.ShowExitHint
    }
}

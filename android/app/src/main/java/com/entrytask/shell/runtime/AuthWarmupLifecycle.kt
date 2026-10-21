package com.entrytask.shell.runtime

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import com.entry.biz.user.ui.AuthActivity
import java.util.IdentityHashMap

/** Native 登录帧提交后才安排预热；暂停/销毁时撤销尚未执行的 View 回调。 */
class AuthWarmupLifecycle(
    private val timing: AuthFrameTiming,
    private val afterFrame: () -> Unit,
) : Application.ActivityLifecycleCallbacks {
    private val registrations = IdentityHashMap<Activity, FrameRegistration>()
    override fun onActivityCreated(activity: Activity, state: Bundle?) {
        if (activity is AuthActivity) timing.created()
    }
    override fun onActivityResumed(activity: Activity) {
        if (activity is AuthActivity) {
            registrations.remove(activity)?.close()
            registrations[activity] = FrameRegistration(activity.window.decorView,
                { committed, atMs -> timing.frame(committed, atMs) }, afterFrame
            ).also { it.start() }
        }
    }
    override fun onActivityPaused(activity: Activity) { registrations.remove(activity)?.close() }
    override fun onActivityDestroyed(activity: Activity) { registrations.remove(activity)?.close() }
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

    private class FrameRegistration(
        private val view: View,
        private val frameObserved: (Boolean, Long) -> Unit,
        private val completed: () -> Unit,
    ) {
        private val main = Handler(Looper.getMainLooper())
        private var closed = false
        private var observed = false
        private val warmup = Runnable { if (!closed) completed() }
        private val committedFrame = Runnable {
            // FrameCommit 可能来自渲染线程；状态与 View 操作统一切回主线程。
            val atMs = SystemClock.elapsedRealtime()
            main.post { if (!closed) schedule(true, atMs) }
        }
        private val draw = ViewTreeObserver.OnDrawListener {
            // API<29/软件绘制只保证本次 traversal 已完成，不能称为物理屏幕呈现。
            val atMs = SystemClock.elapsedRealtime()
            main.post { if (!closed) schedule(false, atMs) }
        }
        private val attach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { view.removeOnAttachStateChangeListener(this); register() }
            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        fun start() {
            // onActivityResumed 可早于 DecorView 加入 Window；附着后再判断硬件绘制能力。
            if (view.isAttachedToWindow) register() else view.addOnAttachStateChangeListener(attach)
        }
        private fun register() {
            if (closed) return
            if (Build.VERSION.SDK_INT >= 29 && view.isHardwareAccelerated) {
                view.viewTreeObserver.registerFrameCommitCallback(committedFrame)
                view.invalidate()
            } else view.viewTreeObserver.addOnDrawListener(draw)
        }
        private fun schedule(committed: Boolean, atMs: Long) {
            if (observed) return
            observed = true
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(draw)
            frameObserved(committed, atMs)
            // 下一帧才调用引擎；不把引擎构造插入登录首帧 traversal。
            view.postOnAnimation(warmup)
        }
        fun close() {
            closed = true
            view.removeOnAttachStateChangeListener(attach)
            view.removeCallbacks(warmup)
            if (view.viewTreeObserver.isAlive) {
                if (Build.VERSION.SDK_INT >= 29) view.viewTreeObserver.unregisterFrameCommitCallback(committedFrame)
                view.viewTreeObserver.removeOnDrawListener(draw)
            }
        }
    }
}

/** 有界进程诊断：只保留首次登录帧时间，不记录表单或账户信息。 */
class AuthFrameTiming {
    @Volatile var createdMs: Long? = null
        private set
    @Volatile var frameMs: Long? = null
        private set
    @Volatile var hardwareCommit: Boolean = false
        private set
    fun created() { if (createdMs == null) createdMs = SystemClock.elapsedRealtime() }
    fun frame(committed: Boolean, atMs: Long) {
        if (frameMs == null) { hardwareCommit = committed; frameMs = atMs }
    }
}

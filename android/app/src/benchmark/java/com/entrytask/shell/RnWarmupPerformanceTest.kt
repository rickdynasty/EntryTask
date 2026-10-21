package com.entrytask.shell

import android.content.Intent
import android.os.Debug
import android.view.View
import java.util.concurrent.atomic.AtomicLong
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.entry.biz.user.manager.UserAuthManager
import com.entry.biz.user.ui.AuthActivity
import com.entrytask.BuildConfig
import com.entrytask.MainActivity
import com.entrytask.MainApplication
import com.entrytask.shell.runtime.WarmupPhase
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** 每次单独启动 instrumentation 得到冷进程样本；不注册账号、不改本地数据。 */
class RnWarmupPerformanceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MainApplication

    @Test fun coldLoginToHomeSample() {
        assertNull("Run each sample in a fresh process", app.authFrameTiming.frameMs)
        val dwellMs = InstrumentationRegistry.getArguments().getString("warmupDwellMs")?.toLongOrNull() ?: 3000L
        require(dwellMs in 0..15000)
        val launched = SystemClock.elapsedRealtime()
        instrumentation.runOnMainSync {
            UserAuthManager.logout()
            app.startActivity(Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        await { app.authFrameTiming.frameMs != null }
        val frameAt = app.authFrameTiming.frameMs!!
        // 两组均固定目标停留，不等待 READY 后再放行，以免偏袒预热组。
        val remaining = frameAt + dwellMs - SystemClock.elapsedRealtime()
        if (remaining > 0) Thread.sleep(remaining)
        var auth: AuthActivity? = null
        instrumentation.runOnMainSync {
            auth = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<AuthActivity>().single()
            val main = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STOPPED)
                .filterIsInstance<MainActivity>().single()
            assertNull("Warmup must not mount a business Surface", main.supportFragmentManager.findFragmentByTag(MainActivity.ROOT_TAG))
        }
        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss
        val warmup = app.rnWarmup.snapshot
        if (app.rnWarmup.enabled) {
            // 不筛除固定停留内尚未完成的慢样本，避免只报告成功预热的最好情况。
            assertTrue(warmup.phase in setOf(WarmupPhase.READY, WarmupPhase.RUNNING, WarmupPhase.SKIPPED_MEMORY))
            warmup.startedMs?.let { assertTrue("Warmup must follow first frame", it >= frameAt) }
        } else assertEquals(WarmupPhase.IDLE, warmup.phase)
        val preloadedHost = if (warmup.phase == WarmupPhase.READY) app.reactHost else null
        val returned = SystemClock.elapsedRealtime()
        instrumentation.runOnMainSync {
            // 验证 Native 会话提交到 RN 的边界；排除账号输入、认证Mock随机耗时。
            UserAuthManager.establishSession("warmup-performance", "memory-only-test-session")
            auth!!.finish()
        }
        val homeFrameAt = AtomicLong(0)
        var awaitingFrame = false
        await {
            instrumentation.runOnMainSync {
                val main = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().singleOrNull()
                if (main != null && !awaitingFrame) {
                    val views = ArrayList<View>()
                    val decor = main.window.decorView
                    decor.findViewsWithText(views, "搜索", View.FIND_VIEWS_WITH_TEXT or View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
                    if (views.any { it.isShown && it.width > 0 && it.height > 0 }) {
                        check(android.os.Build.VERSION.SDK_INT >= 29 && decor.isHardwareAccelerated)
                        awaitingFrame = true
                        // 观测到RN首页布局后记录下一提交帧；不等待Accessibility RPC的空闲窗口。
                        decor.viewTreeObserver.registerFrameCommitCallback {
                            homeFrameAt.compareAndSet(0, SystemClock.elapsedRealtime())
                        }
                        decor.invalidate()
                    }
                }
            }
            homeFrameAt.get() != 0L
        }
        val visible = homeFrameAt.get()
        if (preloadedHost != null) assertSame(preloadedHost, app.reactHost)
        val result = JSONObject()
            .put("warmupEnabled", app.rnWarmup.enabled)
            .put("debug", BuildConfig.DEBUG)
            .put("warmupPhaseAtReturn", warmup.phase.name)
            .put("launchToAuthFrameMs", frameAt - launched)
            .put("authCreateToFrameMs", frameAt - app.authFrameTiming.createdMs!!)
            .put("hardwareFrameCommit", app.authFrameTiming.hardwareCommit)
            .put("warmupDurationMs", warmup.endedMs?.minus(warmup.startedMs ?: 0))
            .put("returnToHomeFrameMs", visible - returned)
            .put("authPssKb", memory)
            .put("dwellMs", dwellMs)
            .put("actualDwellMs", returned - frameAt)
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("warmupSample", result.toString()) })
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach { it.finish() }
            UserAuthManager.logout()
        }
    }

    private fun await(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return
            Thread.sleep(25)
        }
        fail("Timed out waiting for startup milestone")
    }
}

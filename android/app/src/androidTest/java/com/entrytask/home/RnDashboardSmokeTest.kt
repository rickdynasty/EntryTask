package com.entrytask.home

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.entry.biz.user.manager.UserAuthManager
import com.entrytask.BuildConfig
import com.entrytask.MainActivity
import com.entrytask.shell.host.RootContentHostFragment
import org.junit.Assert.*
import org.junit.Test

/** 仅供RN构建：临时内存会话后验证唯一Surface和RN页面就绪，不读写注册账号。 */
class RnDashboardSmokeTest {
    @Test fun authenticatedCliBuildMountsReactSurfaceAndLoadsHome() {
        org.junit.Assume.assumeTrue("仅适用于RN构建", BuildConfig.USE_REACT_NATIVE_PAGES)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { UserAuthManager.establishSession("rn-ui-test-only", "memory-only") }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val deadline = SystemClock.elapsedRealtime() + 60000
                var ready = false
                while (SystemClock.elapsedRealtime() < deadline && !ready) {
                    scenario.onActivity { activity ->
                        val host = activity.supportFragmentManager.findFragmentByTag(MainActivity.ROOT_TAG) as? RootContentHostFragment
                        val react = host?.childFragmentManager?.findFragmentByTag("entrytask-rn-root")
                        assertTrue(host == null || host.childFragmentManager.fragments.size <= 1)
                        ready = react != null
                    }
                    // 不在主线程同步读取无障碍树，避免阻塞RN视图更新。
                    fun contains(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                        if (node == null) return false
                        if (node.text?.contains("金彩秋冬") == true) return true
                        return (0 until node.childCount).any { contains(node.getChild(it)) }
                    }
                    ready = ready && contains(instrumentation.uiAutomation.rootInActiveWindow)
                    if (!ready) Thread.sleep(200)
                }
                assertTrue("RN Surface或首页Mock内容未就绪", ready)
            }
        } finally { instrumentation.runOnMainSync { UserAuthManager.logout() } }
    }
}

package com.entrytask.auth

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.entry.biz.user.ui.AuthActivity
import com.entrytask.MainActivity
import com.entrytask.MainApplication
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** 不提交账号；验证登录返回不会露出后台首页，退出结束整个应用任务。 */
class AuthNavigationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MainApplication
    @After fun cleanup() = instrumentation.runOnMainSync {
        activities().forEach(Activity::finish)
        app.shellGraph.repository.logout()
    }
    @Test fun firstBackKeepsAuthenticationAndSecondBackClosesMainToo() {
        startGuest()
        instrumentation.runOnMainSync {
            val main = activities().filterIsInstance<MainActivity>().single()
            val auth = activities().filterIsInstance<AuthActivity>().single()
            auth.onBackPressedDispatcher.onBackPressed()
            assertFalse(auth.isFinishing)
            assertFalse(main.isFinishing)
            assertNull(main.supportFragmentManager.findFragmentByTag(MainActivity.ROOT_TAG))
            auth.onBackPressedDispatcher.onBackPressed()
        }
        await { var closed = false; instrumentation.runOnMainSync { closed = activities().isEmpty() }; closed }
    }
    @Test fun registerSystemBackReturnsToLoginAndDoesNotDismissAuth() {
        startGuest()
        click("去注册")
        await { hasText("注册新账号") }
        instrumentation.runOnMainSync { activities().filterIsInstance<AuthActivity>().single().onBackPressedDispatcher.onBackPressed() }
        await { hasText("账号登录") }
        instrumentation.runOnMainSync {
            val auth = activities().filterIsInstance<AuthActivity>().single()
            auth.onBackPressedDispatcher.onBackPressed()
            assertFalse(auth.isFinishing)
        }
    }
    private fun startGuest() {
        instrumentation.runOnMainSync {
            app.shellGraph.repository.logout()
            app.startActivity(Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        await { hasText("账号登录") }
        instrumentation.waitForIdleSync()
    }
    private fun activities() = Stage.values().filter { it != Stage.DESTROYED }.flatMap {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
    }.distinct().filter { !it.isFinishing }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) { result += node; repeat(node.childCount) { node.getChild(it)?.let(::visit) } }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
        return result
    }
    private fun hasText(text: String) = nodes().any { it.text?.contains(text) == true }
    private fun click(text: String) {
        var node = nodes().first { it.text?.contains(text) == true }
        while (!node.isClickable) node = node.parent ?: error("missing action")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30000
        while (SystemClock.elapsedRealtime() < deadline) { if (condition()) return; Thread.sleep(50) }
        fail("认证导航超时")
    }
}

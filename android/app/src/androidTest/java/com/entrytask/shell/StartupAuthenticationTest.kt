package com.entrytask.shell

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.entry.biz.user.ui.AuthActivity
import com.entrytask.MainActivity
import com.entrytask.MainApplication
import com.entrytask.shell.domain.ShellCommand
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** 只操作内存会话和导航，不读取、提交或修改本地演示账号。 */
class StartupAuthenticationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MainApplication

    @After fun finishTestActivities() = instrumentation.runOnMainSync {
        activities().forEach { it.finish() }
        app.shellGraph.repository.logout()
    }

    @Test fun guestStartupAuthenticatesBeforeRnAndCancelAllowsPublicHelpWithoutReopeningLogin() {
        instrumentation.runOnMainSync {
            app.shellGraph.repository.logout()
            app.startActivity(Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        await { auth() != null }
        var original: MainActivity? = null
        instrumentation.runOnMainSync {
            original = activities().filterIsInstance<MainActivity>().single()
            assertNull(original!!.supportFragmentManager.findFragmentByTag(MainActivity.ROOT_TAG))
            // 登录覆盖时 MainActivity 已解绑前台端点；重复 Bridge 请求仍应被受理。
            app.shellGraph.bridge.execute(ShellCommand.Authenticate)
            app.shellGraph.bridge.execute(ShellCommand.Authenticate)
            assertEquals(1, activities().filterIsInstance<AuthActivity>().size)
            auth()!!.finish()
        }
        await { hasText("前往登录") }
        instrumentation.runOnMainSync {
            assertSame(original, activities().filterIsInstance<MainActivity>().single())
            assertNull(auth())
            assertNotNull(original!!.supportFragmentManager.findFragmentByTag(MainActivity.ROOT_TAG))
        }
        clickText("帮助中心")
        await { hasText("欢迎使用 EntryTask") }
        instrumentation.runOnMainSync { assertNull(auth()) }
        instrumentation.runOnMainSync { original!!.onBackPressedDispatcher.onBackPressed() }
        await { hasText("前往登录") }
        clickText("前往登录")
        await { auth() != null }
        instrumentation.runOnMainSync { auth()!!.finish() }
        await { hasText("前往登录") }
        instrumentation.runOnMainSync {
            assertNull(auth())
            assertSame(original, activities().filterIsInstance<MainActivity>().single())
        }
    }

    @Test fun singleTaskReentryReplacesOldAttemptAndCancelDoesNotLoop() {
        instrumentation.runOnMainSync {
            app.shellGraph.repository.logout()
            app.startActivity(Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        await { auth() != null }
        var previous: AuthActivity? = null
        var main: MainActivity? = null
        instrumentation.runOnMainSync {
            previous = auth()
            main = activities().filterIsInstance<MainActivity>().single()
            app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        await { auth()?.let { it !== previous } == true }
        instrumentation.runOnMainSync {
            assertSame(main, activities().filterIsInstance<MainActivity>().single())
            assertEquals(1, activities().filterIsInstance<AuthActivity>().size)
            auth()!!.finish()
        }
        await { hasText("前往登录") }
        instrumentation.runOnMainSync { assertNull(auth()) }
    }

    private fun activities(): List<android.app.Activity> = Stage.values()
        .filter { it != Stage.DESTROYED }
        .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }.distinct().filter { !it.isFinishing }
    private fun auth(): AuthActivity? {
        var value: AuthActivity? = null
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            return ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<AuthActivity>().singleOrNull()
        }
        instrumentation.runOnMainSync { value = auth() }
        return value
    }
    private fun hasText(text: String): Boolean = instrumentation.uiAutomation.rootInActiveWindow
        ?.findAccessibilityNodeInfosByText(text)?.isNotEmpty() == true
    private fun clickText(text: String) {
        val nodes = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text).orEmpty()
        var node = nodes.firstOrNull() ?: error("Missing control: $text")
        while (!node.isClickable) node = node.parent ?: error("Control is not clickable: $text")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 30000
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return
            Thread.sleep(100)
        }
        fail("Timed out waiting for authentication lifecycle")
    }
}

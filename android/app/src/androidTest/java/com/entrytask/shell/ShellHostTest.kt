package com.entrytask.shell

import android.app.Activity
import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.entry.biz.user.manager.UserAuthManager
import com.entry.biz.user.ui.AuthActivity
import com.entrytask.MainActivity
import com.entrytask.MainApplication
import com.entrytask.home.HomeFragment
import com.entrytask.shell.domain.ShellCommand
import com.facebook.react.ReactFragment
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/** 使用测试进程内会话模拟认证提交，不读取/修改用户已注册的持久化账号。 */
class ShellHostTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MainApplication

    @Before fun createMemorySession() = instrumentation.runOnMainSync {
        UserAuthManager.establishSession("shell-host-test", "instrumentation-memory-only")
        app.shellGraph.repository.refreshSession()
    }
    @After fun clearMemorySession() = instrumentation.runOnMainSync {
        app.shellGraph.repository.logout()
    }

    @Test fun tabChangesAndActivityRecreationKeepOneRootAndOneProcessHost() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitRoot(scenario)
            val reactHost = app.reactHost
            var original: HomeFragment? = null
            scenario.onActivity { original = root(it) }
            repeat(20) { index ->
                scenario.onActivity { app.shellGraph.repository.selectTab(index % 4 + 1) }
                instrumentation.waitForIdleSync()
            }
            scenario.onActivity {
                assertSame(original, root(it))
                app.shellGraph.repository.selectTab(3)
            }
            scenario.recreate()
            awaitRoot(scenario)
            scenario.onActivity {
                assertEquals(1, it.supportFragmentManager.fragments.filterIsInstance<HomeFragment>().size)
                assertEquals(3, app.shellGraph.repository.snapshots.value.tabId)
                assertSame(reactHost, app.reactHost)
                val rn = root(it)!!.childFragmentManager.fragments.filterIsInstance<ReactFragment>()
                assertEquals(1, rn.size)
                // XML 宿主不能让 RN 业务区域侵入状态栏或刘海区。
                val position = IntArray(2)
                root(it)!!.requireView().getLocationOnScreen(position)
                val top = ViewCompat.getRootWindowInsets(it.window.decorView)
                    ?.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())?.top ?: 0
                assertTrue("RN viewport must start below system top inset", position[1] >= top)
            }
            // Fragment 恢复成功不等于 JS 已重新渲染；等待真实业务文本，防止白屏被误报通过。
            awaitCondition {
                instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("已领权益")?.isNotEmpty() == true
            }
            // 无障碍树先于合成帧更新，等页面事件稳定后再抓取像素，避免抓到重建过渡白帧。
            instrumentation.uiAutomation.waitForIdle(1000, 10000)
            instrumentation.waitForIdleSync()
            // 仅截取测试会话的演示页面供人工检查Insets/底栏，不包含真实账号。
            instrumentation.uiAutomation.takeScreenshot()?.useBitmap { bitmap ->
                File(app.cacheDir, "xml-shell.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test fun xmlRootTagIsReusedAfterActivityRestoration() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitRoot(scenario)
            scenario.onActivity {
                assertSame(root(it), it.supportFragmentManager.findFragmentByTag(MainActivity.ROOT_TAG))
            }
            scenario.recreate()
            awaitRoot(scenario)
            scenario.onActivity {
                assertSame(root(it), it.supportFragmentManager.findFragmentByTag(MainActivity.ROOT_TAG))
                assertEquals(1, it.supportFragmentManager.fragments.filterIsInstance<HomeFragment>().size)
            }
        }
    }

    @Test fun rnNavigationRepairsStaleDepthWithoutClosingMainActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitRoot(scenario)
            awaitCondition {
                instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("搜索")?.isNotEmpty() == true
            }
            scenario.onActivity { app.shellGraph.repository.updateDepth(1) }
            awaitCondition { app.shellGraph.repository.snapshots.value.depth == 0 }
            scenario.onActivity { assertFalse(it.isFinishing) }
            // 从真实 RN 入口进入子页，再走 Activity → ReactFragment → RN 的系统返回链。
            val search = instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText("搜索")?.firstOrNull { it.isClickable }
            assertNotNull(search)
            assertTrue(search!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            awaitCondition { app.shellGraph.repository.snapshots.value.depth == 1 }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitCondition { app.shellGraph.repository.snapshots.value.depth == 0 }
            scenario.onActivity {
                assertFalse(it.isFinishing)
                app.shellGraph.repository.selectTab(2)
            }
            awaitCondition { app.shellGraph.repository.snapshots.value.tabId == 2 }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitCondition { app.shellGraph.repository.snapshots.value.tabId == 1 }
        }
    }

    @Test fun authenticationCancelAndSuccessReturnToTheSameMainActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitRoot(scenario)
            var main: MainActivity? = null
            scenario.onActivity { main = it }
            awaitCondition {
                instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("搜索")?.isNotEmpty() == true
            }
            instrumentation.runOnMainSync {
                app.shellGraph.repository.logout()
                app.shellGraph.bridge.execute(ShellCommand.Authenticate)
                app.shellGraph.bridge.execute(ShellCommand.Authenticate)
            }
            awaitCondition { resumedAuth() != null }
            instrumentation.runOnMainSync { resumedAuth()!!.finish() }
            awaitMain(scenario)
            scenario.onActivity {
                assertSame(main, it)
                assertFalse(app.shellGraph.repository.snapshots.value.authenticated)
                app.shellGraph.bridge.execute(ShellCommand.Authenticate)
            }
            awaitCondition { resumedAuth() != null }
            instrumentation.runOnMainSync {
                UserAuthManager.establishSession("shell-host-test", "instrumentation-memory-only")
                resumedAuth()!!.apply { setResult(Activity.RESULT_OK); finish() }
            }
            awaitMain(scenario)
            scenario.onActivity {
                assertSame(main, it)
                assertFalse(it.isFinishing)
                assertTrue(app.shellGraph.repository.snapshots.value.authenticated)
            }
        }
    }

    private fun root(activity: MainActivity): HomeFragment? =
        activity.supportFragmentManager.fragments.filterIsInstance<HomeFragment>().singleOrNull()

    private fun awaitRoot(scenario: ActivityScenario<MainActivity>) {
        awaitCondition {
            var attached = false
            scenario.onActivity { attached = root(it)?.view != null }
            attached
        }
    }
    private fun awaitMain(scenario: ActivityScenario<MainActivity>) {
        awaitCondition {
            var mainResumed = false
            instrumentation.runOnMainSync {
                mainResumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).any { it is MainActivity }
            }
            mainResumed
        }
        scenario.onActivity { assertTrue(it.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    }
    private fun resumedAuth(): AuthActivity? {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            return ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<AuthActivity>().singleOrNull()
        }
        var result: AuthActivity? = null
        instrumentation.runOnMainSync {
            result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<AuthActivity>().singleOrNull()
        }
        return result
    }
    private fun awaitCondition(condition: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 30000
        while (android.os.SystemClock.elapsedRealtime() < end) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("Timed out waiting for Shell lifecycle")
    }
    private fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }
}

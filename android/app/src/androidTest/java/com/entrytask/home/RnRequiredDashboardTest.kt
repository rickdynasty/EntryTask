package com.entrytask.home

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.entry.biz.user.manager.UserAuthManager
import com.entrytask.BuildConfig
import com.entrytask.MainActivity
import com.entrytask.MainApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.entrytask.shell.host.RootContentHostFragment
import org.junit.Assert.*
import org.junit.Test

/** CLI安装后验证真实RN Surface与Native Mock；不创建第二个runtime或持久测试账号。 */
class RnRequiredDashboardTest {
    private fun description(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString() == label) return node
        for (index in 0 until node.childCount) description(node.getChild(index), label)?.let {
            return it
        }
        return null
    }

    private fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (index in 0 until node.childCount) scrollable(node.getChild(index))?.let {
            return it
        }
        return null
    }

    private fun awaitCondition(message: String, timeoutMs: Long = 30000, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            Thread.sleep(100)
        }
        fail(message)
    }

    @Test
    fun officialCliSurfaceRendersRequiredTabsThroughNativeMock() {
        org.junit.Assume.assumeTrue("仅适用于RN构建", BuildConfig.USE_REACT_NATIVE_PAGES)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            UserAuthManager.establishSession("rn-ui-test-only", "memory-only")
            val app = instrumentation.targetContext.applicationContext as MainApplication
            app.shellGraph.repository.refreshSession()
            app.shellGraph.repository.selectTab(1)
            app.shellGraph.repository.updateDepth(0)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                // Debug首次Metro加载和JS握手独立计时；每个业务区域仍沿用30秒断言。
                awaitCondition("RN入口未就绪：检查Metro/Bundle/bootstrap", 60000) {
                    description(instrumentation.uiAutomation.rootInActiveWindow, "搜索") != null
                }
                for ((tab, marker) in
                    listOf(1 to "金彩秋冬", 2 to "总资产（元）", 3 to "会员专属活动", 4 to "月度收支")) {
                    scenario.onActivity {
                        (it.application as MainApplication).shellGraph.repository.selectTab(tab)
                    }
                    awaitCondition("RN Tab $tab 未显示必做区域") {
                        instrumentation.uiAutomation.rootInActiveWindow
                            ?.findAccessibilityNodeInfosByText(marker)
                            ?.isNotEmpty() == true
                    }
                    if (tab == 1) {
                        for (title in
                            listOf("智慧存款", "财富精选", "安稳纯债基金A", "特色单品", "尊享服务", "投研论道", "私行中心")) {
                            var attempts = 0
                            awaitCondition("RN首页缺少$title") {
                                val root = instrumentation.uiAutomation.rootInActiveWindow
                                if (
                                    root?.findAccessibilityNodeInfosByText(title)?.isNotEmpty() ==
                                        true
                                )
                                    true
                                else {
                                    if (++attempts % 5 == 0)
                                        scrollable(root)
                                            ?.performAction(
                                                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                                            )
                                    false
                                }
                            }
                        }
                    }
                    if (tab == 2) {
                        val eye =
                            description(instrumentation.uiAutomation.rootInActiveWindow, "显示资产")
                        assertNotNull("财富页眼睛必须可通过无障碍触达", eye)
                        assertTrue(eye!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        awaitCondition("点击眼睛后必须切换会话隐私") {
                            description(instrumentation.uiAutomation.rootInActiveWindow, "隐藏资产") !=
                                null
                        }
                    }
                    if (tab == 3) {
                        awaitCondition("会员菜单必须来自Native Mock") {
                            instrumentation.uiAutomation.rootInActiveWindow
                                ?.findAccessibilityNodeInfosByText("200能量值")
                                ?.isNotEmpty() == true
                        }
                        awaitCondition("签到日期和规则必须来自账本响应") {
                            instrumentation.uiAutomation.rootInActiveWindow
                                ?.findAccessibilityNodeInfosByText("连续签到第3天奖励30能量值")
                                ?.isNotEmpty() == true
                        }
                        awaitCondition("VIP等级必须来自摘要响应") {
                            instrumentation.uiAutomation.rootInActiveWindow
                                ?.findAccessibilityNodeInfosByText("R1 中信红会员")
                                ?.isNotEmpty() == true
                        }
                    }
                    if (tab == 4) {
                        assertNotNull(
                            "我的页沿用财富页开启的隐私状态",
                            description(instrumentation.uiAutomation.rootInActiveWindow, "隐藏资产"),
                        )
                        val month =
                            SimpleDateFormat("yyyy-MM", Locale.US)
                                .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
                                .format(Date())
                        awaitCondition("财务概览必须完成Bridge请求") {
                            instrumentation.uiAutomation.rootInActiveWindow
                                ?.findAccessibilityNodeInfosByText(month)
                                ?.isNotEmpty() == true
                        }
                    }
                    scenario.onActivity { activity ->
                        val host =
                            activity.supportFragmentManager.findFragmentByTag(MainActivity.ROOT_TAG)
                                as RootContentHostFragment
                        assertNotNull(
                            host.childFragmentManager.findFragmentByTag("entrytask-rn-root")
                        )
                    }
                    instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                        java.io
                            .File(
                                instrumentation.targetContext.cacheDir,
                                "required-rn-tab-$tab.png",
                            )
                            .outputStream()
                            .use {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                            }
                        bitmap.recycle()
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync { UserAuthManager.logout() }
        }
    }
}

package com.entrytask.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.entry.biz.user.manager.UserAuthManager
import com.entrytask.BuildConfig
import com.entrytask.MainActivity
import com.entrytask.MainApplication
import com.entrytask.shell.host.RootContentHostFragment
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 使用独立临时内存会话，只读Mock，不注册账号、不覆盖设备既有SP。 */
class NativeDashboardTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun fourRequiredTabsPrivacyAndRecreationUseComposeWithoutReactSurface() {
        org.junit.Assume.assumeFalse("仅适用于Native构建", BuildConfig.USE_REACT_NATIVE_PAGES)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            UserAuthManager.establishSession("native-ui-test-only", "memory-only")
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val markers = listOf(1 to "金彩秋冬", 2 to "总资产（元）", 3 to "会员专属活动", 4 to "月度收支")
                for ((tab, marker) in markers) {
                    scenario.onActivity {
                        (it.application as MainApplication).shellGraph.repository.selectTab(tab)
                    }
                    compose.waitUntil(25000) {
                        compose
                            .onAllNodesWithText(marker, substring = true)
                            .fetchSemanticsNodes()
                            .isNotEmpty()
                    }
                    compose.waitForIdle()
                    scenario.onActivity { activity ->
                        val host =
                            activity.supportFragmentManager.findFragmentByTag(MainActivity.ROOT_TAG)
                                as RootContentHostFragment
                        assertNull(host.childFragmentManager.findFragmentByTag("entrytask-rn-root"))
                    }
                    if (tab == 1) {
                        // 回归：四个头部区域以外的内容必须真实存在，不能提前显示页尾。
                        for (title in
                            listOf("智慧存款", "财富精选", "安稳纯债基金A", "特色单品", "尊享服务", "投研论道", "私行中心")) {
                            compose
                                .onNodeWithTag("dashboard-list")
                                .performScrollToNode(hasText(title))
                            compose.onNodeWithText(title).assertExists()
                            if (title in listOf("财富精选", "投研论道")) {
                                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                                    java.io
                                        .File(
                                            instrumentation.targetContext.cacheDir,
                                            "home-native-$title.png",
                                        )
                                        .outputStream()
                                        .use {
                                            bitmap.compress(
                                                android.graphics.Bitmap.CompressFormat.PNG,
                                                100,
                                                it,
                                            )
                                        }
                                    bitmap.recycle()
                                }
                            }
                        }
                        compose.onNodeWithTag("dashboard-list").performScrollToIndex(0)
                    }
                    if (tab == 2) {
                        compose.onNodeWithContentDescription("显示资产").performClick()
                        compose.waitUntil(20000) {
                            compose.onAllNodesWithText("0.00").fetchSemanticsNodes().isNotEmpty()
                        }
                    }
                    if (tab == 3) {
                        compose.waitUntil(20000) {
                            compose
                                .onAllNodesWithText("200能量值")
                                .fetchSemanticsNodes()
                                .isNotEmpty() &&
                                compose
                                    .onAllNodesWithText("R1 中信红会员")
                                    .fetchSemanticsNodes()
                                    .isNotEmpty()
                        }
                    }
                    if (tab == 4) {
                        // 同一会话财富页打开眼睛后，我的页应沿用同一个隐私状态。
                        compose.onNodeWithContentDescription("隐藏资产").assertExists()
                        compose
                            .onNodeWithTag("dashboard-list")
                            .performScrollToNode(hasText("信用卡账单"))
                        compose.waitUntil(20000) {
                            compose
                                .onAllNodesWithText("尚未绑定信用卡，暂无账单")
                                .fetchSemanticsNodes()
                                .isNotEmpty()
                        }
                        compose.onNodeWithText("尚未绑定信用卡，暂无账单").assertExists()
                    }
                    instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                        java.io
                            .File(
                                instrumentation.targetContext.cacheDir,
                                "required-native-tab-$tab.png",
                            )
                            .outputStream()
                            .use {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                            }
                        bitmap.recycle()
                    }
                }
                scenario.recreate()
                compose.waitUntil(20000) {
                    compose
                        .onAllNodesWithContentDescription("隐藏资产")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                compose
                    .onNodeWithTag("dashboard-list")
                    .performScrollToNode(hasContentDescription("中信银行，CHINA CITIC BANK，让财富有温度"))
            }
        } finally {
            instrumentation.runOnMainSync { UserAuthManager.logout() }
        }
    }
}

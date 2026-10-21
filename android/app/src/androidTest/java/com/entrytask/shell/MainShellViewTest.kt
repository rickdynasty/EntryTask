package com.entrytask.shell

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.entry.lib.ui.BottomTabBar
import com.entrytask.R
import com.entrytask.shell.domain.ShellSnapshot
import com.entrytask.shell.presentation.AuthLaunchState
import com.entrytask.shell.presentation.MainShellView
import com.entrytask.shell.presentation.MainUiState
import org.junit.Assert.*
import org.junit.Test

/** 不创建 RN 的 XML 渲染测试：点击只上报意图，选中/显隐完全由 UiState 决定。 */
class MainShellViewTest {
    @Test fun tabsAndGateAreStateDrivenWithoutReplacingHostContent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.AppTheme)
            val root = LayoutInflater.from(context).inflate(R.layout.activity_home, null)
            val host = root.findViewById<FrameLayout>(R.id.fragmentContainer)
            val child = View(context).also { host.addView(it) }
            val tabs = root.findViewById<BottomTabBar>(R.id.bottomTabBar)
            val chrome = root.findViewById<View>(R.id.bottomChrome)
            var requested: Int? = null
            val renderer = MainShellView(root) { requested = it }
            var state = MainUiState(ShellSnapshot(authenticated = true), AuthLaunchState.Idle)
            renderer.render(state)
            assertTrue(tabs.getChildAt(0).isSelected)
            tabs.getChildAt(1).performClick()
            assertEquals(2, requested)
            assertTrue(tabs.getChildAt(0).isSelected)
            state = state.copy(shell = state.shell.copy(tabId = 2))
            renderer.render(state)
            assertTrue(tabs.getChildAt(1).isSelected)
            renderer.render(state.copy(shell = state.shell.copy(depth = 1)))
            assertEquals(View.GONE, chrome.visibility)
            renderer.render(state.copy(shell = state.shell.copy(authenticated = false)))
            assertEquals(View.GONE, chrome.visibility)
            renderer.render(state)
            assertEquals(View.VISIBLE, chrome.visibility)
            assertSame(child, host.getChildAt(0))
        }
    }
}

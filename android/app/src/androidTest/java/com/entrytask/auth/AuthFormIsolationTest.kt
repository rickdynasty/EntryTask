package com.entrytask.auth

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.entry.biz.user.ui.AuthActivity
import org.junit.Assert.*
import org.junit.Test

/** 仅输入合成表单并切页，不提交登录/注册，不访问或改写已有账号。 */
class AuthFormIsolationTest {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    @Test fun switchingFormsDoesNotTransferCredentialsAndClearsPasswordOnReturn() {
        ActivityScenario.launch(AuthActivity::class.java).use { scenario ->
            awaitPage("账号登录")
            setText(fields()[0], "login-demo")
            setText(fields()[1], "test-password-only")
            click("去注册")
            awaitPage("注册新账号")
            assertTrue(fields().all { it.text.isNullOrEmpty() })
            setText(fields()[0], "register-demo")
            setText(fields()[1], "register-test-only")
            click("直接登录")
            awaitPage("账号登录")
            assertEquals("login-demo", fields()[0].text.toString())
            assertTrue(fields()[1].text.isNullOrEmpty())
            scenario.recreate()
            awaitPage("账号登录")
            assertTrue(fields()[1].text.isNullOrEmpty())
            click("去注册")
            awaitPage("注册新账号")
            assertTrue(fields().all { it.text.isNullOrEmpty() })
        }
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result.add(node)
            repeat(node.childCount) { node.getChild(it)?.let(::visit) }
        }
        automation.rootInActiveWindow?.let(::visit)
        return result
    }
    private fun fields() = nodes().filter { it.className == "android.widget.EditText" }
    // Compose 的虚拟节点不保证实现框架 findByText，直接遍历公开无障碍树定位文本。
    private fun hasTitle(title: String) = nodes().any { it.text?.contains(title) == true }
    private fun awaitPage(title: String) {
        val end = SystemClock.elapsedRealtime() + 30000
        while (SystemClock.elapsedRealtime() < end) {
            if (hasTitle(title) && fields().size == 2) {
                automation.waitForIdle(500, 5000)
                return
            }
            Thread.sleep(50)
        }
        val root = automation.rootInActiveWindow
        fail("认证表单未就绪: package=${root?.packageName}, titleVisible=${hasTitle(title)}, fieldCount=${fields().size}")
    }
    private fun setText(node: AccessibilityNodeInfo, text: String) {
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }))
        automation.waitForIdle(300, 5000)
    }
    private fun click(text: String) {
        var node = nodes().first { it.text?.contains(text) == true }
        while (!node.isClickable) node = node.parent ?: error("缺少表单切换动作")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
}

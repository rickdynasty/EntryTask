package com.entrytask.shell.presentation

import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.entry.lib.base.data.TabItem
import com.entry.lib.ui.BottomTabBar
import com.entrytask.R

/** XML 的纯渲染适配：状态下行、点击上报；不访问 Repository、Bridge 或会话。 */
class MainShellView(root: View, onTabSelected: (Int) -> Unit) {
    private val host = root.findViewById<FrameLayout>(R.id.fragmentContainer)
    private val chrome = root.findViewById<View>(R.id.bottomChrome)
    private val tabs = root.findViewById<BottomTabBar>(R.id.bottomTabBar)

    init {
        tabs.setTabItems(listOf(
            TabItem(1, "首页", R.drawable.ic_tab_home),
            TabItem(2, "财富", R.drawable.ic_tab_trade),
            TabItem(3, "会员", R.drawable.ic_tab_message),
            TabItem(4, "我的", R.drawable.ic_tab_profile),
        )) { _, id -> onTabSelected(id) }
        // 系统安全区由宿主预留；RN 按实际裁剪视口计算剩余 Insets，避免重复留白。
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(0, 0, 0, safe.bottom)
            host.setPadding(safe.left, safe.top, safe.right, 0)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    fun render(state: MainUiState) {
        tabs.selectTab(state.shell.tabId)
        chrome.visibility = if (state.bottomBarVisible) View.VISIBLE else View.GONE
        // 宿主容器始终存在，未认证时仍由 RN 门禁跨端请求 Native 登录。
    }
}

package com.entrytask.shell.presentation

import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.entrytask.R
import com.entrytask.shell.host.ShellInsetsAdapter
import com.entrytask.shell.presentation.compose.RootTabBar

/** 静态XML只提供恢复容器；底栏绘制迁移Compose，VM及Shell状态契约保持不变。 */
class MainShellView(root: View, onTabSelected: (Int) -> Unit) {
    private val chrome = root.findViewById<View>(R.id.bottomChrome)
    private var selected by mutableIntStateOf(1)

    init {
        root.findViewById<ComposeView>(R.id.bottomTabBar).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { MaterialTheme { RootTabBar(selected, onTabSelected) } }
        }
        ShellInsetsAdapter(root, root.findViewById(R.id.fragmentContainer))
    }

    fun render(state: MainUiState) {
        selected = state.shell.tabId
        chrome.visibility = if (state.bottomBarVisible) View.VISIBLE else View.GONE
    }
}

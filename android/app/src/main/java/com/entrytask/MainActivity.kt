package com.entrytask

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.entry.biz.user.ui.AuthActivity
import com.entry.lib.base.mvvm.BaseActivity
import com.entry.lib.log.MLog
import com.entry.lib.ui.BackPressHelper
import com.entrytask.home.HomeFragment
import com.entrytask.shell.domain.RootBackAction
import com.entrytask.shell.host.HostFragmentHandle
import com.entrytask.shell.presentation.AuthLaunchState
import com.entrytask.shell.presentation.MainShellView
import com.entrytask.shell.presentation.MainViewModel
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import com.swmansion.rnscreens.fragment.restoration.RNScreensFragmentFactory
import kotlinx.coroutines.launch

/** XML Shell 的平台执行入口；状态、认证防重和根返回策略由 MainViewModel 管理。 */
class MainActivity : BaseActivity(), DefaultHardwareBackBtnHandler {
    private val graph get() = (application as MainApplication).shellGraph
    private val hostHandle = HostFragmentHandle()
    private var unbindForeground: (() -> Unit)? = null
    private var legacyTab: Int? = null
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                val savedState = createSavedStateHandle()
                if (!savedState.contains(MainViewModel.SELECTED_TAB) && legacyTab != null) {
                    savedState[MainViewModel.SELECTED_TAB] = legacyTab
                }
                MainViewModel(graph.repository, savedState, SystemClock::elapsedRealtime)
            }
        }
    }
    private val authentication = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Result 只是返回通知；是否成功必须重读 Native 会话，不能信任 RESULT_OK 置登录。
        viewModel.authenticationReturned()
        MLog.i(TAG, "authentication.returned result=${it.resultCode}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = RNScreensFragmentFactory()
        super.onCreate(savedInstanceState)
        legacyTab = savedInstanceState?.takeIf { it.containsKey("root_tab") }?.getInt("root_tab")
        setContentView(R.layout.activity_home)
        bindRootHost()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val shellView = MainShellView(findViewById(R.id.shellRoot), viewModel::selectTab)
        shellView.render(viewModel.uiState.value)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(shellView::render)
            }
        }
        MLog.i(TAG, "shell.created renderer=xml")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.authLaunch.collect { launch ->
                    if (launch is AuthLaunchState.Requested && viewModel.authenticationStarted(launch.id)) {
                        try {
                            check(!isFinishing && !isDestroyed)
                            MLog.i(TAG, "authentication.launch.started")
                            authentication.launch(Intent(this@MainActivity, AuthActivity::class.java))
                        } catch (error: Exception) {
                            viewModel.authenticationFailed(launch.id)
                            MLog.e(TAG, "authentication.launch.failed exception=${error.javaClass.simpleName}")
                        }
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (BackPressHelper.hideKeyboardIfVisible(this@MainActivity)) return
                // true 仅代表已交给 RN；未消费时 RN 会调用 invokeDefaultOnBackPressed。
                if (!hostHandle.dispatchBack()) invokeDefaultOnBackPressed()
            }
        })
    }

    /** 复用框架恢复的根片段；稳定 tag/容器保持原 XML 版本的恢复约定。 */
    private fun bindRootHost() {
        val restored = supportFragmentManager.findFragmentByTag(ROOT_TAG)
        check(restored == null || restored is HomeFragment)
        val host = restored as? HomeFragment ?: HomeFragment().also {
            check(!supportFragmentManager.isStateSaved)
            supportFragmentManager.beginTransaction().add(R.id.fragmentContainer, it, ROOT_TAG).commitNow()
        }
        hostHandle.bind(host)
        MLog.i(TAG, "host.content.attached restored=${restored != null}")
    }
    override fun onResume() {
        super.onResume()
        unbindForeground?.invoke()
        unbindForeground = graph.foreground.bind(viewModel::requestAuthentication)
        viewModel.onHostResumed()
        MLog.i(TAG, "shell.resumed")
    }
    override fun onPause() {
        unbindForeground?.invoke()
        unbindForeground = null
        super.onPause()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTask 重入可能移除上层认证页，解除孤立展示状态并重读事实。
        viewModel.authenticationReturned()
        MLog.i(TAG, "shell.new-intent")
    }
    override fun onDestroy() {
        hostHandle.clear()
        MLog.i(TAG, "host.content.detached")
        super.onDestroy()
    }
    override fun invokeDefaultOnBackPressed() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        when (viewModel.defaultBack()) {
            RootBackAction.Resynchronize -> MLog.w(TAG, "back.depth.pending code=RN_BACK_UNHANDLED")
            RootBackAction.SwitchToHome -> Unit
            RootBackAction.ShowExitHint -> Toast.makeText(this, "双击退出应用", Toast.LENGTH_SHORT).show()
            RootBackAction.Exit -> finishAffinity()
        }
    }
    companion object {
        private const val TAG = "EntryTask.MainActivity"
        const val ROOT_TAG = "entrytask-root-hub"
    }
}

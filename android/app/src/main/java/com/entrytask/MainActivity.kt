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
import com.entrytask.shell.domain.RootBackAction
import com.swmansion.rnscreens.fragment.restoration.RNScreensFragmentFactory
import com.entrytask.shell.host.RootContentHostFragment
import com.entrytask.shell.host.HostFragmentHandle
import com.entrytask.shell.presentation.AuthLaunchState
import com.entrytask.shell.presentation.MainShellView
import com.entrytask.shell.presentation.MainViewModel
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import kotlinx.coroutines.launch

/** 静态宿主槽与Compose底栏的平台执行入口；状态、认证防重和根返回策略由 MainViewModel 管理。 */
class MainActivity : BaseActivity(), DefaultHardwareBackBtnHandler {
    private val graph get() = (application as MainApplication).shellGraph
    private val hostHandle = HostFragmentHandle()
    private var unbindForeground: (() -> Unit)? = null
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                val savedState = createSavedStateHandle()
                MainViewModel(graph.repository, savedState, SystemClock::elapsedRealtime)
            }
        }
    }
    private val authentication = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Result 只是返回通知；是否成功必须重读 Native 会话，不能信任 RESULT_OK 置登录。
        val id = it.data?.takeIf { data -> data.hasExtra(AuthActivity.EXTRA_AUTH_FLOW_ID) }
            ?.getLongExtra(AuthActivity.EXTRA_AUTH_FLOW_ID, -1)
        val accepted = viewModel.authenticationReturned(id)
        if (accepted) {
            graph.foreground.authenticationFinished(id ?: viewModel.authenticationFlowId)
        }
        MLog.i(TAG, "authentication.returned flowId=$id result=${it.resultCode} accepted=$accepted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = RNScreensFragmentFactory()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        // 框架可能早于门禁恢复旧Fragment；它已属于真实页面需求，不能按无Surface预热隔离异常。
        if (RootContentHostFragment.usesReactNative() && supportFragmentManager.findFragmentByTag(ROOT_TAG) != null) {
            (application as MainApplication).rnWarmup.surfaceWillAttach()
        }
        viewModel.onHostCreated()
        when (val launch = viewModel.authLaunch.value) {
            is AuthLaunchState.Requested -> graph.foreground.authenticationStarted(launch.id)
            is AuthLaunchState.InFlight -> graph.foreground.authenticationStarted(launch.id)
            else -> Unit
        }
        MLog.i(TAG, "authentication.startup.checked authenticated=${graph.repository.snapshots.value.authenticated}")
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
        MLog.i(TAG, "shell.created renderer=compose-chrome")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.authLaunch.collect { launch ->
                    if (launch is AuthLaunchState.Requested && viewModel.authenticationStarted(launch.id)) {
                        try {
                            check(!isFinishing && !isDestroyed)
                            graph.foreground.authenticationStarted(launch.id)
                            MLog.i(TAG, "authentication.launch.started flowId=${launch.id}")
                            authentication.launch(Intent(this@MainActivity, AuthActivity::class.java)
                                .putExtra(AuthActivity.EXTRA_AUTH_FLOW_ID, launch.id))
                        } catch (error: Exception) {
                            graph.foreground.authenticationFinished(launch.id)
                            viewModel.authenticationFailed(launch.id)
                            MLog.e(TAG, "authentication.launch.failed exception=${error.javaClass.simpleName}")
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiState.collect { state ->
                    // 首次未登录先展示 Native 认证；返回/失败后才准备 RN 门禁和公开页。
                    if (state.canAttachContent) window.decorView.post {
                        // 避开 FragmentManager 正在派发生命周期的事务窗口，并重读最新认证状态。
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                            viewModel.canAttachContent() && !supportFragmentManager.isStateSaved) bindRootHost()
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
        check(restored == null || restored is RootContentHostFragment)
        if (RootContentHostFragment.usesReactNative()) (application as MainApplication).rnWarmup.surfaceWillAttach()
        val host = restored as? RootContentHostFragment ?: RootContentHostFragment().also {
            check(!supportFragmentManager.isStateSaved)
            supportFragmentManager.beginTransaction().add(R.id.fragmentContainer, it, ROOT_TAG).commitNow()
        }
        hostHandle.bind(host)
        if (restored == null) MLog.i(TAG, "host.content.attached restored=false")
    }
    override fun onResume() {
        super.onResume()
        unbindForeground?.invoke()
        unbindForeground = graph.foreground.bind(viewModel::requestAuthentication)
        viewModel.onHostResumed()
        if (viewModel.authLaunch.value !is AuthLaunchState.InFlight) {
            graph.foreground.authenticationFinished(viewModel.authenticationFlowId)
        }
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
        graph.foreground.authenticationFinished(viewModel.authenticationFlowId)
        viewModel.onNewEntry()
        MLog.i(TAG, "shell.new-intent")
    }
    override fun onDestroy() {
        hostHandle.clear()
        if (isFinishing) graph.foreground.authenticationFinished(viewModel.authenticationFlowId)
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

package com.entrytask.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.entry.biz.dashboard.data.pages.NativeApiClient
import com.entry.biz.dashboard.data.pages.RootPageRepositories
import com.entry.biz.dashboard.presentation.pages.*
import com.entry.biz.dashboard.ui.compose.NativeDashboardRoute
import com.entry.biz.user.manager.UserAuthManager
import com.entry.lib.log.MLog
import com.entrytask.BuildConfig
import com.entrytask.MainApplication
import com.entrytask.R
import com.entrytask.base.BaseFragment
import kotlinx.coroutines.launch

/** 稳定根Fragment：CLI复用唯一RN Surface，IDE分支绘制四个Compose页面。 */
class HomeFragment : BaseFragment() {
    companion object {
        fun usesReactNative(): Boolean = BuildConfig.USE_REACT_NATIVE_PAGES
    }

    private val app
        get() = requireActivity().application as MainApplication

    private val coordinator: DashboardCoordinatorViewModel by viewModels {
        viewModelFactory {
            initializer {
                DashboardCoordinatorViewModel(
                    RootPageRepositories(
                        NativeApiClient(
                            app.assets,
                            app.prototypeNetwork,
                            UserAuthManager::storageScope,
                            { UserAuthManager.sessionEpoch },
                        )
                    )
                )
            }
        }
    }
    private val home: HomePageViewModel by viewModels {
        viewModelFactory { initializer { HomePageViewModel(coordinator.repositories) } }
    }
    private val wealth: WealthPageViewModel by viewModels {
        viewModelFactory { initializer { WealthPageViewModel(coordinator.repositories) } }
    }
    private val member: MemberPageViewModel by viewModels {
        viewModelFactory { initializer { MemberPageViewModel(coordinator.repositories) } }
    }
    private val profile: ProfilePageViewModel by viewModels {
        viewModelFactory { initializer { ProfilePageViewModel(coordinator.repositories) } }
    }

    override fun getComponentName(): String? = if (usesReactNative()) "EntryTask" else null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        if (usesReactNative()) return super.onCreateView(inflater, container, savedInstanceState)
        return ComposeView(requireContext()).apply {
            id = R.id.native_dashboard
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
    }

    override fun initView(view: View, savedInstanceState: Bundle?) {
        val pages = listOf(home, wealth, member, profile)
        (view as ComposeView).setContent {
            val shell by coordinator.renderSession.collectAsStateWithLifecycle()
            MaterialTheme {
                NativeDashboardRoute(
                    shell.tab,
                    shell.authenticated,
                    shell.epoch,
                    coordinator,
                    home,
                    wealth,
                    member,
                    profile,
                ) {
                    app.shellGraph.foreground.authenticate()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    app.shellGraph.repository.snapshots.collect { shell ->
                        pages.forEachIndexed { index, page ->
                            page.show(
                                shell.authenticated && shell.tabId == index + 1,
                                shell.sessionEpoch,
                            )
                        }
                        // 先清理/切换VM，再发布可渲染的会话，避免新账户短暂看到旧投影。
                        coordinator.session(shell.sessionEpoch, shell.authenticated, shell.tabId)
                    }
                } finally {
                    val epoch = app.shellGraph.repository.snapshots.value.sessionEpoch
                    pages.forEach { it.show(false, epoch) }
                }
            }
        }
        MLog.i("EntryTask.NativeDashboard", "host.attached renderer=compose")
    }
}

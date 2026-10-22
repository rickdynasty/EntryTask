package com.entry.biz.user.ui

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import android.content.Intent
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.entry.biz.user.viewmodel.UserViewModel
import com.entry.biz.user.manager.UserAuthManager
import com.entry.biz.user.network.userService
import com.entry.biz.user.repository.UserRepository
import com.entry.biz.user.ui.navigation.AuthNavHost
import com.entry.lib.base.mvvm.BaseActivity
import com.entry.lib.log.MLog
import com.entry.lib.ui.BackPressHelper

/**
 * 认证宿主 Activity
 * 承载登录与注册的 Jetpack Compose 导航流
 */
class AuthActivity : BaseActivity() {

    companion object {
        /** 由调用方传入并原样返回，隔离 singleTask 重入后迟到的认证结果。 */
        const val EXTRA_AUTH_FLOW_ID = "entrytask.auth.flowId"
        private const val TAG = "Rick_tan:AuthActivity"
    }

    private val exitPolicy = AuthExitPolicy(SystemClock::elapsedRealtime)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 认证页使用浅色背景，系统栏保持深色图标，避免白色图标不可读。
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val result = Intent().apply {
            if (intent.hasExtra(EXTRA_AUTH_FLOW_ID)) {
                putExtra(EXTRA_AUTH_FLOW_ID, intent.getLongExtra(EXTRA_AUTH_FLOW_ID, -1))
            }
        }
        // 默认取消也携带关联编号，成功只改变结果码；事实仍由 Native 会话决定。
        setResult(RESULT_CANCELED, result)
        //使用 AndroidX OnBackPressedDispatcher 注册返回事件回调
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleLoginBack()
            }
        })

        MLog.d(TAG, "AuthActivity 创建，初始化 ViewModel 与 Compose 布局")

        // Repository 可复用；表单 VM 由每个导航页面独立创建。
        val userRepository = UserRepository(userService)
        val viewModelFactory = UserViewModel.Factory(userRepository)

        setContent {
            // 3. 渲染导航宿主，并传入 ViewModel 与登录成功回调
            AuthNavHost(
                viewModelFactory = viewModelFactory,
                onLoginBack = ::handleLoginBack,
                onFormChanged = exitPolicy::reset,
                onLoginSuccess = {
                    MLog.d(TAG, "登录成功，关闭 AuthActivity 返回首页")
                    // 只关闭认证页；singleTask 的 MainActivity 保留在栈底并在恢复时发布会话快照。
                    setResult(RESULT_OK, result)
                    finish()
                })
        }
    }

    /** 未认证时返回不能关闭认证并露出首页；第一次提示，2秒内再次返回结束整个任务。 */
    private fun handleLoginBack() {
        if (BackPressHelper.hideKeyboardIfVisible(this)) { exitPolicy.reset(); return }
        if (exitPolicy.shouldExit()) {
            MLog.i(TAG, "authentication.exit.confirmed")
            UserAuthManager.logout() // 递增epoch，拒绝尚未结束的认证请求迟到回写。
            // finishAffinity不允许携带Activity Result；只有退出整个任务时清掉返回载荷。
            setResult(RESULT_CANCELED)
            finishAffinity()
        } else {
            MLog.i(TAG, "authentication.exit.prompted")
            Toast.makeText(this, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        exitPolicy.reset()
        super.onPause()
    }

}

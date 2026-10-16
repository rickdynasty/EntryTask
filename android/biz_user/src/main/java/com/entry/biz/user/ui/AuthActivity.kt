package com.entry.biz.user.ui

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.entry.biz.user.net.RetrofitClient
import com.entry.biz.user.repository.UserRepository
import com.entry.biz.user.ui.navigation.AuthNavHost
import com.entry.biz.user.viewmodel.UserViewModel
import com.entry.lib.base.mvvm.BaseActivity
import com.entry.lib.log.MLog
import com.entry.lib.ui.BackPressHelper

/**
 * 认证宿主 Activity
 * 承载登录与注册的 Jetpack Compose 导航流
 */
class AuthActivity : BaseActivity() {

    companion object {
        private const val TAG = "Rick_tan:AuthActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 【新增】使用 AndroidX OnBackPressedDispatcher 注册返回事件回调
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                BackPressHelper.handleBackPress(this@AuthActivity) {
                    finishAffinity() // 连续双击两次，退出应用
                }
            }
        })

        MLog.d(TAG, "AuthActivity 创建，初始化 ViewModel 与 Compose 布局")

        // 1. 构建依赖链：UserService -> UserRepository -> UserViewModel.Factory
        val userService = RetrofitClient.userService
        val userRepository = UserRepository(userService)
        val viewModelFactory = UserViewModel.Factory(userRepository)

        // 2. 获取 UserViewModel 实例
        val viewModel = ViewModelProvider(this, viewModelFactory)[UserViewModel::class.java]

        setContent {
            // 3. 渲染导航宿主，并传入 ViewModel 与登录成功回调
            AuthNavHost(
                viewModel = viewModel, onLoginSuccess = {
                    MLog.d(TAG, "登录成功，关闭 AuthActivity 返回首页")
                    // 登录成功后销毁当前认证界面，回到 HomeActivity
                    finish()
                })
        }
    }
}
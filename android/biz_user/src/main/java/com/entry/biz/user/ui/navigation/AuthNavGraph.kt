package com.entry.biz.user.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.entry.biz.user.ui.LoginScreen
import com.entry.biz.user.ui.RegisterScreen
import com.entry.biz.user.viewmodel.UserViewModel
import com.entry.lib.log.MLog

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
}

/**
 * 认证模块导航宿主
 * 管理登录与注册两个 Compose 界面之间的切换
 */
@Composable
fun AuthNavHost(viewModel: UserViewModel, onLoginSuccess: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Login.route) {
        // 登录页面
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = viewModel,
                onNavigateToRegister = {
                    MLog.d("AuthNavGraph", "用户点击跳转到注册页面")
                    navController.navigate(Screen.Register.route)
                },
                onLoginSuccess = {
                    MLog.d("AuthNavGraph", "登录成功回调触发，通知宿主进入主界面")
                    onLoginSuccess()
                }
            )
        }

        // 注册页面
        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = viewModel,
                onBackToLogin = {
                    MLog.d("AuthNavGraph", "用户点击返回登录页面")
                    navController.popBackStack()
                },
                onRegisterSuccess = {
                    MLog.d("AuthNavGraph", "注册成功默认登陆成功，安全弹栈切回登录界面")
                    onLoginSuccess()
                }
            )
        }
    }
}
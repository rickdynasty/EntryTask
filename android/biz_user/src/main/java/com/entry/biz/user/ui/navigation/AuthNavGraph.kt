package com.entry.biz.user.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.entry.biz.user.viewmodel.UserViewModel
import com.entry.biz.user.ui.LoginRoute
import com.entry.biz.user.ui.RegisterRoute
import com.entry.lib.log.MLog

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
}

@Composable
fun AuthNavHost(viewModel: UserViewModel, onLoginSuccess: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Login.route) {
        // 登录页面路由
        composable(Screen.Login.route) {
            LoginRoute(viewModel = viewModel, onNavigateToRegister = {
                MLog.d("AuthNavGraph", "跳转到注册页面")
                navController.navigate(Screen.Register.route)
            }, onLoginSuccess = {
                MLog.d("AuthNavGraph", "登录成功，通知宿主")
                onLoginSuccess()
            })
        }

        // 注册页面路由
        composable(Screen.Register.route) {
            RegisterRoute(viewModel = viewModel, onBackToLogin = {
                MLog.d("AuthNavGraph", "返回登录页面")
                navController.popBackStack()
            }, onRegisterSuccess = {
                MLog.d("AuthNavGraph", "注册成功，进入主界面")
                onLoginSuccess()
            })
        }
    }
}
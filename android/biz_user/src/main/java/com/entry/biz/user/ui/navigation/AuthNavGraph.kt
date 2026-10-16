package com.entry.biz.user.ui.navigation

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
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

@Composable
fun AuthNavHost() {
    val navController = rememberNavController()
    val viewModel: UserViewModel = viewModel(factory = UserViewModel.Factory)
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Screen.Login.route) {
        // 登录页
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = viewModel,
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onLoginSuccess = {
                    MLog.d("登录成功")
                    // 登录成功：关闭 AuthActivity，返回/拉起 HomeActivity
                    val activity = context as? Activity
                    val intent = Intent().setClassName(
                        context,
                        "com.entrytask.HomeActivity"
                    ) // 或直接 HomeActivity::class.java
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    MLog.d("call startActivity "+ intent.component?.className)
                    context.startActivity(intent)
                    MLog.d("call activity?.finish() ")
                    activity?.finish()
                }
            )
        }

        // 注册页
        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = viewModel,
                onBackToLogin = {
                    navController.popBackStack()
                },
                onRegisterSuccess = {
                    // 注册成功自动回到登录页或直接登录
                    navController.popBackStack()
                }
            )
        }
    }
}
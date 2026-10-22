package com.entry.biz.user.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
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
fun AuthNavHost(
    viewModelFactory: ViewModelProvider.Factory,
    onLoginSuccess: () -> Unit,
    onLoginBack: () -> Unit,
    onFormChanged: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Login.route) {
        // 登录页面路由
        composable(Screen.Login.route) { entry ->
            // 根页拦截系统返回；注册页仍由NavHost消费返回，回到登录而非Native首页。
            BackHandler(onBack = onLoginBack)
            // 同一 VM 类型按 NavBackStackEntry 隔离，登录与注册不共享表单/事件。
            val viewModel: UserViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory)
            LoginRoute(viewModel = viewModel, onNavigateToRegister = {
                MLog.d("AuthNavGraph", "跳转到注册页面")
                viewModel.onFormLeft()
                onFormChanged()
                navController.navigate(Screen.Register.route) { launchSingleTop = true }
            }, onLoginSuccess = {
                MLog.d("AuthNavGraph", "登录成功，通知宿主")
                onLoginSuccess()
            })
        }

        // 注册页面路由
        composable(Screen.Register.route) { entry ->
            val viewModel: UserViewModel = viewModel(viewModelStoreOwner = entry, factory = viewModelFactory)
            RegisterRoute(viewModel = viewModel, onBackToLogin = {
                MLog.d("AuthNavGraph", "返回登录页面")
                viewModel.onFormLeft()
                onFormChanged()
                navController.popBackStack()
            }, onRegisterSuccess = {
                MLog.d("AuthNavGraph", "注册成功，进入主界面")
                onLoginSuccess()
            })
        }
    }
}
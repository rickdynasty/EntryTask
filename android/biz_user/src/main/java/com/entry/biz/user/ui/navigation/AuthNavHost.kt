package com.entry.biz.user.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.entry.biz.user.ui.AuthRoute
import com.entry.biz.user.viewmodel.UserViewModel
import com.entry.lib.log.MLog

/** 认证导航的内部目的地；只描述当前登录和注册两个目的地。 */
private sealed class AuthDestination(val route: String) {
    object Login : AuthDestination("login")
    object Register : AuthDestination("register")
}

/** 只协调认证页切换；会话事实由Native维护，成功后交给Activity决定如何返回。 */
@Composable
fun AuthNavHost(
    viewModelFactory: ViewModelProvider.Factory,
    onAuthSuccess: () -> Unit,
    onLoginBackPressed: () -> Unit,
    onAuthPageChanged: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AuthDestination.Login.route) {
        composable(AuthDestination.Login.route) { loginEntry ->
            // 登录根页的系统返回交给宿主处理双击退出；注册页由NavHost返回登录。
            BackHandler(onBack = onLoginBackPressed)
            // 每个导航entry持有独立表单VM，避免登录和注册共享输入/效果。
            val loginViewModel: UserViewModel = viewModel(
                viewModelStoreOwner = loginEntry,
                factory = viewModelFactory,
            )
            AuthRoute(
                viewModel = loginViewModel,
                isRegistration = false,
                onSwitchForm = {
                    MLog.d("AuthNavHost", "跳转到注册页面")
                    loginViewModel.onFormLeft()
                    onAuthPageChanged()
                    navController.navigate(AuthDestination.Register.route) {
                        launchSingleTop = true
                    }
                },
                onAuthSuccess = {
                    MLog.d("AuthNavHost", "登录认证成功，通知宿主")
                    onAuthSuccess()
                },
            )
        }

        composable(AuthDestination.Register.route) { registerEntry ->
            val registerViewModel: UserViewModel = viewModel(
                viewModelStoreOwner = registerEntry,
                factory = viewModelFactory,
            )
            val backToLogin = {
                registerViewModel.onFormLeft()
                onAuthPageChanged()
                navController.popBackStack()
                Unit
            }
            BackHandler(onBack = backToLogin)
            AuthRoute(
                viewModel = registerViewModel,
                isRegistration = true,
                onSwitchForm = backToLogin,
                onAuthSuccess = {
                    MLog.d("AuthNavHost", "注册认证成功，通知宿主")
                    onAuthSuccess()
                },
            )
        }
    }
}

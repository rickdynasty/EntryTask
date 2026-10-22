package com.entry.biz.user.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.entry.biz.user.ui.contract.AuthUiEffect
import com.entry.biz.user.viewmodel.UserViewModel
import com.entry.lib.base.mvvm.ui.ComposableScreen

/** 登录路由只收集本页状态和一次性副作用，视觉与交互复用认证表单。 */
@Composable
fun LoginRoute(
    viewModel: UserViewModel,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    // 离页取消请求并清除密码，不在导航页面之间传递凭证。
    DisposableEffect(viewModel) { onDispose { viewModel.onFormLeft() } }
    val context = LocalContext.current
    ComposableScreen(viewModel = viewModel, onEffect = { effect ->
        when (effect) {
            is AuthUiEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            is AuthUiEffect.NavigateToMain -> onLoginSuccess()
        }
    }) { state, onIntent ->
        AuthFormContent(state = state, isRegistration = false,
            onIntent = onIntent, onSwitchForm = onNavigateToRegister)
    }
}

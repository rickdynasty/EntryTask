package com.entry.biz.user.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.entry.biz.user.ui.contract.AuthUiIntent
import com.entry.biz.user.viewmodel.UserViewModel

/** 只在当前导航页RESUMED时消费提示和成功状态；后台完成认证后恢复仍可返回宿主。 */
@Composable
internal fun AuthRoute(
    viewModel: UserViewModel,
    isRegistration: Boolean,
    onSwitchForm: () -> Unit,
    onAuthSuccess: () -> Unit,
) {
    DisposableEffect(viewModel) { onDispose { viewModel.onFormLeft() } }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val authSuccess by rememberUpdatedState(onAuthSuccess)
    LaunchedEffect(viewModel, lifecycle, context) {
        var successDelivered = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.uiState.collect { current ->
                current.message?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    viewModel.handleIntent(AuthUiIntent.MessageShown)
                }
                if (current.authenticated && !successDelivered) {
                    successDelivered = true
                    authSuccess()
                }
            }
        }
    }
    AuthFormContent(state, isRegistration, viewModel::handleIntent, onSwitchForm)
}

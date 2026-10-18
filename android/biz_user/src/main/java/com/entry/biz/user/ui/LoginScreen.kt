package com.entry.biz.user.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.entry.biz.user.ui.contract.AuthUiEffect
import com.entry.biz.user.ui.contract.AuthUiIntent
import com.entry.biz.user.ui.contract.AuthUiState
import com.entry.biz.user.viewmodel.UserViewModel
import com.entry.lib.base.mvvm.ui.ComposableScreen

/**
 * 登录智能组件（Route 容器）：通过 ComposableScreen 统一管理状态与 Side-Effect
 */
@Composable
fun LoginRoute(
    viewModel: UserViewModel,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current

    ComposableScreen(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is AuthUiEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is AuthUiEffect.NavigateToMain -> onLoginSuccess()
            }
        }
    ) { state, onIntent ->
        LoginScreenContent(state = state, onIntent = onIntent, onNavigateToRegister = onNavigateToRegister)
    }
}

/**
 * 登录纯 UI 组件（Dumb Component）：零业务逻辑，极易进行 @Preview 预览
 */
@Composable
private fun LoginScreenContent(
    state: AuthUiState,
    onIntent: (AuthUiIntent) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "账号登录", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = state.username,
                    onValueChange = { onIntent(AuthUiIntent.UpdateUsername(it)) },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = { onIntent(AuthUiIntent.UpdatePassword(it)) },
                    label = { Text("密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !state.isLoading
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onIntent(AuthUiIntent.SubmitLogin) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !state.isLoading
                ) {
                    Text(text = "登 录", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "还没有账号？", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "去注册",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = !state.isLoading) { onNavigateToRegister() }
                    )
                }
            }
        }

        // 全屏 Loading 遮罩
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = Color.White, shadowElevation = 8.dp) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = "正在登录中...", fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
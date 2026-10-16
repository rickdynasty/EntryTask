package com.entry.biz.user.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.entry.biz.user.viewmodel.UserViewModel
import com.entry.lib.log.MLog

/**
 * 账号注册界面
 * 负责收集新用户信息，成功后写入 Mock 文件并引导切回登录页，带全屏 Loading 遮罩
 */
@Composable
fun RegisterScreen(
    viewModel: UserViewModel, onBackToLogin: () -> Unit, onRegisterSuccess: () -> Unit
) {
    // LocalContext.current 是 Compose 的内置属性，它会自动获取当前组件所处的宿主 Activity 或 Context 上下文，非常安全且便捷。
    val context = LocalContext.current

    val username by viewModel.usernameState.collectAsState()
    val password by viewModel.passwordState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // 最外层使用 Box 承载内容与全屏 Loading 遮罩
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. 正常的表单卡片内容
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "注册新账号",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 用户名输入框
                OutlinedTextField(
                    value = username,
                    onValueChange = { viewModel.usernameState.value = it },
                    label = { Text("设置用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading // 加载时禁用输入
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 密码输入框
                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.passwordState.value = it },
                    label = { Text("设置密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isLoading // 加载时禁用输入
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 注册提交按钮
                Button(
                    onClick = {
                        MLog.d("RegisterScreen", "用户点击注册按钮，准备写入 Mock 数据并提交")
                        viewModel.register {
                            MLog.d("RegisterScreen", "注册成功回调触发")
                            // 1. 弹出成功 Toast 提示
                            android.widget.Toast.makeText(
                                context,
                                "注册成功，已自动为您登录",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()

                            // 2. 执行成功回调（关闭 AuthActivity，回到首页）
                            onRegisterSuccess()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isLoading
                ) {
                    Text(text = "注 册", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部返回登录提示
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "已有账号？", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "直接登录",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = !isLoading) {
                            MLog.d("RegisterScreen", "用户点击返回登录页面")
                            onBackToLogin()
                        })
                }
            }
        }

        // 2. 全屏透明遮罩与 Loading 弹框
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)) // 半透明遮罩，阻止底层点击
                    .clickable(enabled = false) {}, // 拦截点击事件
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    shadowElevation = 8.dp // Material3 兼容的阴影高度
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "正在注册中...", fontSize = 15.sp, color = Color(0xFF333333)
                        )
                    }
                }
            }
        }
    }
}
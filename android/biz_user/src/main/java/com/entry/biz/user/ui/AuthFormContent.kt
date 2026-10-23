package com.entry.biz.user.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.entry.biz.user.R
import com.entry.biz.user.ui.contract.AuthUiIntent
import com.entry.biz.user.ui.contract.AuthUiState

private val BrandRed = Color(0xFFC92336)
private val Ink = Color(0xFF282523)
private val Muted = Color(0xFF817A75)

/** 登录/注册共用的纯渲染表单；业务状态和请求仍归页面独立的 VM 所有。 */
@Composable
internal fun AuthFormContent(
    state: AuthUiState,
    isRegistration: Boolean,
    onIntent: (AuthUiIntent) -> Unit,
    onSwitchForm: () -> Unit,
) {
    // 只保留瞬时显隐状态，不用 rememberSaveable，离页/重建后默认隐藏。
    var passwordVisible by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) passwordVisible = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        passwordVisible = false
        if (!state.isLoading) onIntent(if (isRegistration) AuthUiIntent.SubmitRegister else AuthUiIntent.SubmitLogin)
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = BrandRed, onPrimary = Color.White,
        background = Color.White, surface = Color.White, onSurface = Ink, onSurfaceVariant = Muted)) {
        Box(Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF3E9), Color.White)))
            .windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
            contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(44.dp))
                BankBrand()
                Spacer(Modifier.height(44.dp))
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(if (isRegistration) R.string.auth_register_title else R.string.auth_login_title),
                        Modifier.semantics { heading() }, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(if (isRegistration) R.string.auth_register_subtitle else R.string.auth_login_subtitle),
                        fontSize = 14.sp, color = Muted)
                    Spacer(Modifier.height(36.dp))
                    AuthInput(
                        label = stringResource(if (isRegistration) R.string.auth_register_username else R.string.auth_username),
                        placeholder = stringResource(if (isRegistration) R.string.auth_register_username_hint else R.string.auth_username_hint),
                        value = state.username,
                        onValueChange = { onIntent(AuthUiIntent.UpdateUsername(it)) },
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                    )
                    Spacer(Modifier.height(24.dp))
                    AuthInput(
                        label = stringResource(if (isRegistration) R.string.auth_register_password else R.string.auth_password),
                        placeholder = stringResource(if (isRegistration) R.string.auth_register_password_hint else R.string.auth_password_hint),
                        value = state.password,
                        onValueChange = { onIntent(AuthUiIntent.UpdatePassword(it)) },
                        enabled = !state.isLoading,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !state.isLoading) {
                                Icon(painterResource(if (passwordVisible) R.drawable.ic_auth_visibility_off else R.drawable.ic_auth_visibility),
                                    stringResource(if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password))
                            }
                        },
                    )
                    Spacer(Modifier.height(36.dp))
                    Button(onClick = { submit() }, enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(disabledContainerColor = BrandRed.copy(alpha = 0.55f), disabledContentColor = Color.White)) {
                        if (state.isLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(stringResource(if (state.isLoading) {
                            if (isRegistration) R.string.auth_registering else R.string.auth_logging_in
                        } else if (isRegistration) R.string.auth_register else R.string.auth_login), fontSize = 17.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                // TextButton保留至少48dp点击区域，同时提交时禁用切页防止重复操作。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (isRegistration) R.string.auth_has_account else R.string.auth_no_account),
                        fontSize = 14.sp, color = Muted)
                    TextButton(onClick = onSwitchForm, enabled = !state.isLoading) {
                        Text(stringResource(if (isRegistration) R.string.auth_go_login else R.string.auth_go_register), fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** 沿用已有原型名称和首页线条标志，不引入外部银行品牌资源。 */
@Composable
private fun BankBrand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(R.drawable.ic_auth_bank), contentDescription = null,
            modifier = Modifier.size(48.dp), tint = BrandRed)
        Column {
            Text(stringResource(R.string.auth_bank_name), fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(stringResource(R.string.auth_bank_english), fontSize = 10.sp, letterSpacing = 1.sp, color = Muted)
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.auth_welcome), fontSize = 13.sp, color = Muted)
}

/** 固定外置标签与框内输入提示，空值和聚焦时都不会让标签跳动。 */
@Composable
private fun AuthInput(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Text(label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(value = value, onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }, singleLine = true, enabled = enabled,
        placeholder = { Text(placeholder, color = Muted, fontSize = 15.sp) },
        // label留在框外；无障碍语义仍提供字段名称。
        visualTransformation = visualTransformation, trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFFE4DEDA),
            focusedBorderColor = BrandRed, unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White, disabledContainerColor = Color(0xFFF6F4F2)))
}

// 预览直接构造UI状态，不创建VM、不访问网络或账号存储；业务动作使用空回调。
@Preview(name = "登录 · 初始", group = "认证表单", widthDp = 360, heightDp = 800, locale = "zh-rCN", showBackground = true)
@Composable
private fun LoginFormPreview() {
    AuthFormContent(
        state = AuthUiState(),
        isRegistration = false,
        onIntent = {},
        onSwitchForm = {},
    )
}

@Preview(name = "注册 · 初始", group = "认证表单", widthDp = 360, heightDp = 800, locale = "zh-rCN", showBackground = true)
@Composable
private fun RegisterFormPreview() {
    AuthFormContent(
        state = AuthUiState(),
        isRegistration = true,
        onIntent = {},
        onSwitchForm = {},
    )
}

@Preview(name = "登录 · 提交中", group = "认证表单", widthDp = 360, heightDp = 800, locale = "zh-rCN", showBackground = false)
@Composable
private fun LoginFormLoadingPreview() {
    AuthFormContent(
        state = AuthUiState(username = "preview_user", password = "preview_only", isLoading = true),
        isRegistration = false,
        onIntent = {},
        onSwitchForm = {},
    )
}

@Preview(name = "注册 · 提交中", group = "认证表单", widthDp = 360, heightDp = 800, locale = "zh-rCN", showBackground = true)
@Composable
private fun RegisterFormLoadingPreview() {
    AuthFormContent(
        state = AuthUiState(username = "preview_user", password = "preview_only", isLoading = true),
        isRegistration = true,
        onIntent = {},
        onSwitchForm = {},
    )
}

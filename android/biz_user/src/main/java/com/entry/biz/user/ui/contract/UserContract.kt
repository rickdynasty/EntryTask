package com.entry.biz.user.ui.contract

import com.entry.lib.base.mvvm.contract.IUiEffect
import com.entry.lib.base.mvvm.contract.IUiIntent
import com.entry.lib.base.mvvm.contract.IUiState

/**
 * 登录/注册模块的唯一 UI 状态
 */
data class AuthUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false
) : IUiState

/**
 * 登录/注册模块的一次性副作用
 */
sealed interface AuthUiEffect : IUiEffect {
    data class ShowToast(val message: String) : AuthUiEffect
    data object NavigateToMain : AuthUiEffect
}

/**
 * 用户交互意图
 */
sealed interface AuthUiIntent : IUiIntent {
    data class UpdateUsername(val value: String) : AuthUiIntent
    data class UpdatePassword(val value: String) : AuthUiIntent
    data object SubmitLogin : AuthUiIntent
    data object SubmitRegister : AuthUiIntent
}
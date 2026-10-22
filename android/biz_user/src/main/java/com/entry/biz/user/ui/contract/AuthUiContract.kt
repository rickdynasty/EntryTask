package com.entry.biz.user.ui.contract

import com.entry.lib.base.mvvm.contract.IUiEffect
import com.entry.lib.base.mvvm.contract.IUiIntent
import com.entry.lib.base.mvvm.contract.IUiState

/**
 * 单个认证页面的 UI 状态类型；各导航页面持有独立实例
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
    /** Native会话已提交成功；只通知宿主，不指定目的页面或新建Activity。 */
    data object AuthSucceeded : AuthUiEffect
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
package com.entry.biz.user.ui.contract

/** 每个导航页独享状态；关键认证结果保留到宿主消费，重建/后台期间不会丢失。 */
data class AuthUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val authenticated: Boolean = false,
    val message: String? = null,
)

/** View只提交用户动作，业务判断留在VM。 */
sealed interface AuthUiIntent {
    data class UpdateUsername(val value: String) : AuthUiIntent
    data class UpdatePassword(val value: String) : AuthUiIntent
    data object SubmitLogin : AuthUiIntent
    data object SubmitRegister : AuthUiIntent
    data object MessageShown : AuthUiIntent
}

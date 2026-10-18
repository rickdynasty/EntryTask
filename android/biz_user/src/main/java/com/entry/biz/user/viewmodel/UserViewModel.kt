package com.entry.biz.user.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.entry.biz.user.ui.contract.AuthUiEffect
import com.entry.biz.user.ui.contract.AuthUiIntent
import com.entry.biz.user.ui.contract.AuthUiState
import com.entry.biz.user.repository.UserRepository
import com.entry.lib.base.mvvm.viewmodel.BaseViewModel
import com.entry.lib.log.MLog
import kotlinx.coroutines.launch

class UserViewModel(private val repository: UserRepository) : BaseViewModel<AuthUiState, AuthUiEffect, AuthUiIntent>(
    AuthUiState()
) {

    companion object {
        private const val TAG = "Rick_tan:UserViewModel"
    }

    override fun handleIntent(intent: AuthUiIntent) {
        when (intent) {
            is AuthUiIntent.UpdateUsername -> updateState { copy(username = intent.value) }
            is AuthUiIntent.UpdatePassword -> updateState { copy(password = intent.value) }
            is AuthUiIntent.SubmitLogin -> login()
            is AuthUiIntent.SubmitRegister -> register()
        }
    }

    private fun login() {
        val (username, password) = currentState
        if (username.isBlank() || password.isBlank()) {
            sendEffect { AuthUiEffect.ShowToast("账号或密码不能为空") }
            return
        }

        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            MLog.d(TAG, "开始执行登录网络请求")
            try {
                repository.login(username, password)
                MLog.d(TAG, "登录成功，发送导航通知")
                sendEffect { AuthUiEffect.NavigateToMain }
            } catch (e: Exception) {
                MLog.e(TAG, "登录失败: ${e.message}", e)
                sendEffect { AuthUiEffect.ShowToast(e.message ?: "登录失败，请检查账号密码") }
            } finally {
                updateState { copy(isLoading = false) }
            }
        }
    }

    private fun register() {
        val (username, password) = currentState
        if (username.isBlank() || password.isBlank()) {
            sendEffect { AuthUiEffect.ShowToast("账号或密码不能为空") }
            return
        }

        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            MLog.d(TAG, "开始执行注册网络请求")
            try {
                repository.register(username, password)
                MLog.d(TAG, "注册成功，自动登录并进入主页")
                sendEffect { AuthUiEffect.ShowToast("注册成功") }
                sendEffect { AuthUiEffect.NavigateToMain }
            } catch (e: Exception) {
                MLog.e(TAG, "注册失败: ${e.message}", e)
                sendEffect { AuthUiEffect.ShowToast(e.message ?: "注册失败，请稍后重试") }
            } finally {
                updateState { copy(isLoading = false) }
            }
        }
    }

    class Factory(private val repository: UserRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST") return UserViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
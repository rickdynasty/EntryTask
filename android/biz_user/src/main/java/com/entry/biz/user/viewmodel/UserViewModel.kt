package com.entry.biz.user.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.entry.biz.user.manager.UserAuthManager
import com.entry.biz.user.repository.UserRepository
import com.entry.lib.log.MLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 用户模块 ViewModel
 * 负责管理账号密码输入状态、Loading 加载态以及登录/注册异步交互逻辑
 */
class UserViewModel(private val repository: UserRepository) : ViewModel() {

    companion object {
        private const val TAG = "Rick_tan:UserViewModel"
    }

    // UI 输入框双向绑定状态
    val usernameState = MutableStateFlow("")
    val passwordState = MutableStateFlow("")

    // 内部加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /**
     * 登录逻辑
     */
    fun login(onSuccess: () -> Unit) {
        val username = usernameState.value
        val password = passwordState.value

        if (username.isBlank() || password.isBlank()) {
            MLog.w(TAG, "登录拦截：账号或密码不能为空")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            MLog.d(TAG, "开始执行登录网络请求: username = $username")
            try {
                // 调用仓库层进行登录（内部会通过拦截器、写入缓存并更新内存登录态）
                repository.login(username, password)
                MLog.d(TAG, "登录成功，触发回调")
                onSuccess()
            } catch (e: Exception) {
                MLog.e(TAG, "登录失败: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 注册逻辑
     */
    fun register(onSuccess: () -> Unit) {
        val username = usernameState.value
        val password = passwordState.value

        if (username.isBlank() || password.isBlank()) {
            MLog.w(TAG, "注册拦截：账号或密码不能为空")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            MLog.d(TAG, "开始执行注册请求: username = $username")
            try {
                // 1. 调用仓库层注册（将账号密码写入本地 Mock 的 SP 文件）
                repository.register(username, password)

                // 2. 注册成功后，直接设置内存登录态为成功（视为注册即登录）
                UserAuthManager.setLoginSuccess(true)

                MLog.d(TAG, "注册成功，已自动更新内存登录态，触发成功回调")
                onSuccess()
            } catch (e: Exception) {
                MLog.e(TAG, "注册失败: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * ViewModel 工厂类（用于向 ViewModel 注入带参数的 UserRepository）
     */
    class Factory(private val repository: UserRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST") return UserViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
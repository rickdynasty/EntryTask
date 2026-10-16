package com.entry.biz.user.viewmodel

import androidx.lifecycle.ViewModelProvider
import com.entry.biz.user.net.RetrofitClient
import com.entry.biz.user.repository.UserRepository
import com.entry.lib.base.mvvm.viewmodel.BaseViewModel
import com.entry.lib.base.mvvm.viewmodel.UiEvent
import kotlinx.coroutines.flow.MutableStateFlow

class UserViewModel(
    private val userRepository: UserRepository
) : BaseViewModel() {

    val usernameState = MutableStateFlow("")
    val passwordState = MutableStateFlow("")

    fun login(onSuccess: () -> Unit) {
        val username = usernameState.value.trim()
        val password = passwordState.value.trim()
        if (username.isEmpty() || password.isEmpty()) {
            sendEvent(UiEvent.ShowToast("账号或密码不能为空"))
            return
        }

        launchSafe(showLoading = true) {
            userRepository.login(username, password)
            sendEvent(UiEvent.ShowToast("登录成功"))
            onSuccess()
        }
    }

    fun register(onSuccess: () -> Unit) {
        val username = usernameState.value.trim()
        val password = passwordState.value.trim()
        if (username.isEmpty() || password.isEmpty()) {
            sendEvent(UiEvent.ShowToast("账号或密码不能为空"))
            return
        }

        launchSafe(showLoading = true) {
            userRepository.register(username, password)
            sendEvent(UiEvent.ShowToast("注册成功"))
            onSuccess()
        }
    }

    /**
     * 提供标准的 ViewModel Factory，用于在 Compose 或 Activity 中实例化带参数的 ViewModel
     */
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val repository = UserRepository(RetrofitClient.userService)
                return UserViewModel(repository) as T
            }
        }
    }
}
package com.entry.biz.user.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.entry.biz.user.ui.contract.AuthUiIntent
import com.entry.biz.user.ui.contract.AuthUiState
import com.entry.biz.user.domain.AuthRejectedException
import com.entry.biz.user.domain.AuthRepository
import java.io.IOException
import java.net.SocketTimeoutException
import com.entry.lib.log.MLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 每个认证页独立持有状态；成功是可观察事实，导航由Route/宿主执行。 */
class UserViewModel(private val repository: AuthRepository) : ViewModel() {
    private val mutable = MutableStateFlow(AuthUiState())
    val uiState = mutable.asStateFlow()
    private val currentState get() = mutable.value
    private fun updateState(reducer: AuthUiState.() -> AuthUiState) = mutable.update(reducer)

    companion object {
        private const val TAG = "EntryTask.Auth"
    }

    fun handleIntent(intent: AuthUiIntent) {
        when (intent) {
            is AuthUiIntent.UpdateUsername -> if (!currentState.isLoading && !currentState.authenticated) {
                updateState { copy(username = intent.value) }
            }
            is AuthUiIntent.UpdatePassword -> if (!currentState.isLoading && !currentState.authenticated) {
                updateState { copy(password = intent.value) }
            }
            is AuthUiIntent.SubmitLogin -> login()
            is AuthUiIntent.SubmitRegister -> register()
            AuthUiIntent.MessageShown -> updateState { copy(message = null) }
        }
    }

    private var requestJob: Job? = null
    private var requestVersion = 0L

    /** 离页即清密码并取消本页请求；保留本页账号，不向其他页面传递字段。 */
    fun onFormLeft() {
        requestVersion++
        requestJob?.cancel()
        requestJob = null
        updateState { copy(password = "", isLoading = false, message = null) }
        MLog.d(TAG, "form.left passwordCleared=true")
    }

    private fun login() = submit(isRegistration = false)
    private fun register() = submit(isRegistration = true)

    private fun submit(isRegistration: Boolean) {
        if (currentState.isLoading || currentState.authenticated) return
        val (username, password) = currentState
        if (username.isBlank() || password.isBlank()) {
            updateState { copy(message = "账号或密码不能为空") }
            return
        }
        // 先同步置忙，避免协程尚未运行时重复受理；旧请求终态不能覆盖新页面状态。
        val version = ++requestVersion
        val operation = if (isRegistration) "register" else "login"
        updateState { copy(isLoading = true, message = null) }
        requestJob = viewModelScope.launch {
            MLog.d(TAG, "request.started operation=$operation flowId=$version")
            try {
                if (isRegistration) repository.register(username, password)
                else repository.login(username, password)
                currentCoroutineContext().ensureActive()
                if (version != requestVersion) return@launch
                updateState {
                    copy(password = "", authenticated = true, message = if (isRegistration) "注册成功" else null)
                }
                MLog.d(TAG, "request.completed operation=$operation flowId=$version")
            } catch (cancelled: CancellationException) {
                MLog.d(TAG, "request.cancelled operation=$operation flowId=$version")
                throw cancelled
            } catch (error: Exception) {
                if (version == requestVersion) {
                    MLog.e(TAG, "request.failed operation=$operation flowId=$version exception=${error.javaClass.simpleName}")
                    // 失败只走一次性Toast，网络诊断不直接暴露给用户。
                    val message = when (error) {
                        is AuthRejectedException -> error.userMessage
                        is SocketTimeoutException -> "网络请求超时，请稍后重试"
                        is IOException -> "网络连接失败，请检查网络后重试"
                        else -> if (isRegistration) "注册失败，请稍后重试" else "登录失败，请稍后重试"
                    }
                    updateState { copy(message = message) }
                }
            } finally {
                if (version == requestVersion) updateState { copy(isLoading = false) }
            }
        }
    }

    override fun onCleared() {
        onFormLeft()
        super.onCleared()
    }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST") return UserViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

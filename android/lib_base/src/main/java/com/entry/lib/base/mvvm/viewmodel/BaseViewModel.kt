package com.entry.lib.base.mvvm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    protected fun showLoading(show: Boolean) {
        _isLoading.value = show
    }

    protected fun sendEvent(event: UiEvent) {
        viewModelScope.launch {
            _uiEvent.emit(event)
        }
    }

    protected fun launchSafe(
        showLoading: Boolean = false,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
    ) {
        viewModelScope.launch {
            if (showLoading) _isLoading.value = true
            try {
                block()
            } catch (e: Throwable) {
                if (onError != null) {
                    onError(e)
                } else {
                    sendEvent(UiEvent.ShowToast(e.message ?: "未知错误"))
                }
            } finally {
                if (showLoading) _isLoading.value = false
            }
        }
    }
}

sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
    object NavigateBack : UiEvent
    data class NavigateTo(val route: String) : UiEvent
}
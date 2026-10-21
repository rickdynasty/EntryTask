package com.entrytask.shell.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entrytask.shell.domain.RootBackAction
import com.entrytask.shell.domain.RootBackPolicy
import com.entrytask.shell.domain.ShellRepository
import com.entrytask.shell.domain.ShellSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthLaunchState {
    data object Idle : AuthLaunchState
    data class Requested(val id: Long) : AuthLaunchState
    data class InFlight(val id: Long) : AuthLaunchState
    data class Failed(val id: Long) : AuthLaunchState
}

/** 仅保存 Shell UI 投影；不持有 Fragment、Activity、JSONObject 或凭证。 */
data class MainUiState(val shell: ShellSnapshot, val authLaunch: AuthLaunchState) {
    val bottomBarVisible: Boolean get() = shell.authenticated && shell.depth == 0
}

class MainViewModel(
    private val repository: ShellRepository,
    private val savedState: SavedStateHandle,
    clock: () -> Long,
) : ViewModel() {
    private var sequence = 0L
    private val auth = MutableStateFlow<AuthLaunchState>(AuthLaunchState.Idle)
    val authLaunch = auth.asStateFlow()
    private val backPolicy = RootBackPolicy(clock)
    val uiState = combine(repository.snapshots, auth) { shell, launch -> MainUiState(shell, launch) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState(repository.snapshots.value, auth.value))

    init {
        repository.restoreSelection(savedState[SELECTED_TAB])
        viewModelScope.launch {
            var previous = repository.snapshots.value
            repository.snapshots.collect { next ->
                if (next.tabId != previous.tabId || next.depth != previous.depth ||
                    next.sessionEpoch != previous.sessionEpoch) backPolicy.reset()
                previous = next
            }
        }
        viewModelScope.launch {
            repository.snapshots.map { it.tabId }.distinctUntilChanged().collect { savedState[SELECTED_TAB] = it }
        }
    }
    fun selectTab(tabId: Int) = repository.selectTab(tabId)

    fun onHostResumed() {
        repository.refreshSession(forcePublish = true)
        if (repository.snapshots.value.authenticated) auth.value = AuthLaunchState.Idle
    }
    fun requestAuthentication() {
        repository.refreshSession()
        if (repository.snapshots.value.authenticated) return
        if (auth.value is AuthLaunchState.Requested || auth.value is AuthLaunchState.InFlight) return
        auth.value = AuthLaunchState.Requested(++sequence)
    }
    /** 在平台 launch 前确认一次，重复收集或配置恢复不会重复提交同一请求。 */
    fun authenticationStarted(id: Long): Boolean {
        if (auth.value != AuthLaunchState.Requested(id)) return false
        auth.value = AuthLaunchState.InFlight(id)
        return true
    }
    fun authenticationFailed(id: Long) {
        if (auth.value == AuthLaunchState.InFlight(id)) auth.value = AuthLaunchState.Failed(id)
    }
    fun authenticationReturned() {
        auth.value = AuthLaunchState.Idle
        repository.refreshSession(forcePublish = true)
    }
    fun defaultBack(): RootBackAction {
        val result = backPolicy.decide(repository.snapshots.value)
        when (result) {
            RootBackAction.SwitchToHome -> repository.selectTab(1)
            RootBackAction.Resynchronize -> repository.refreshSession(forcePublish = true)
            else -> Unit
        }
        return result
    }
    companion object { const val SELECTED_TAB = "shell.selected_tab" }
}

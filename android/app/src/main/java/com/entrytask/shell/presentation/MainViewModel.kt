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
import java.util.concurrent.atomic.AtomicLong

sealed interface AuthLaunchState {
    data object Idle : AuthLaunchState
    data class Requested(val id: Long) : AuthLaunchState
    data class InFlight(val id: Long) : AuthLaunchState
    data class Failed(val id: Long) : AuthLaunchState
    data class Canceled(val id: Long) : AuthLaunchState
}

/** 仅保存 Shell UI 投影；不持有 Fragment、Activity、JSONObject 或凭证。 */
data class MainUiState(val shell: ShellSnapshot, val authLaunch: AuthLaunchState) {
    // 冷启动认证优先；认证取消/失败后允许挂载 RN 门禁与公开页面。
    val canAttachContent: Boolean get() = shell.authenticated ||
        authLaunch is AuthLaunchState.Canceled || authLaunch is AuthLaunchState.Failed ||
        authLaunch is AuthLaunchState.Idle
    val bottomBarVisible: Boolean get() = shell.authenticated && shell.depth == 0
}

class MainViewModel(
    private val repository: ShellRepository,
    private val savedState: SavedStateHandle,
    clock: () -> Long,
) : ViewModel() {
    // singleTask 清除认证页时，系统可能丢弃 Result data；记录待消费的旧取消通知。
    private var supersededResults = savedState.get<Int>(SUPERSEDED_RESULTS) ?: 0
    private var sequence = savedState.get<Long>(AUTH_SEQUENCE) ?: 0L
    private val auth = MutableStateFlow<AuthLaunchState>(when (savedState.get<String>(AUTH_PHASE)) {
        "requested" -> AuthLaunchState.Requested(sequence)
        "inFlight" -> AuthLaunchState.InFlight(sequence)
        "canceled" -> AuthLaunchState.Canceled(sequence)
        "failed" -> AuthLaunchState.Failed(sequence)
        else -> AuthLaunchState.Idle
    })
    val authLaunch = auth.asStateFlow()
    val authenticationFlowId: Long get() = sequence
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
    fun canAttachContent(): Boolean = MainUiState(repository.snapshots.value, auth.value).canAttachContent
    fun selectTab(tabId: Int) = repository.selectTab(tabId)

    /** onCreate 即检查 Native 内存会话；不等待 RN 或 Bridge 就绪。 */
    fun onHostCreated() {
        repository.refreshSession(forcePublish = true)
        if (savedState.get<Boolean>(STARTUP_CHECKED) != true) {
            savedState[STARTUP_CHECKED] = true
            requestAuthentication()
        }
    }
    fun onHostResumed() {
        repository.refreshSession(forcePublish = true)
        if (repository.snapshots.value.authenticated) setAuthentication(AuthLaunchState.Idle)
        // 不在每次恢复时重弹：取消后保持门禁，等待用户主动重试。
    }
    fun requestAuthentication() {
        repository.refreshSession()
        if (repository.snapshots.value.authenticated) return
        if (auth.value is AuthLaunchState.Requested || auth.value is AuthLaunchState.InFlight) return
        // 新任务与旧 Activity 的销毁回调可能交错；进程内编号不因重建新 VM 而复用。
        sequence = authenticationIds.updateAndGet { maxOf(it, sequence) + 1 }
        savedState[AUTH_SEQUENCE] = sequence
        setAuthentication(AuthLaunchState.Requested(sequence))
    }
    /** 在平台 launch 前确认一次，重复收集或配置恢复不会重复提交同一请求。 */
    fun authenticationStarted(id: Long): Boolean {
        if (auth.value != AuthLaunchState.Requested(id)) return false
        setAuthentication(AuthLaunchState.InFlight(id))
        return true
    }
    fun authenticationFailed(id: Long) {
        if (auth.value == AuthLaunchState.InFlight(id)) setAuthentication(AuthLaunchState.Failed(id))
    }
    fun authenticationReturned(id: Long? = null): Boolean {
        repository.refreshSession(forcePublish = true)
        // CLEAR_TOP 式系统销毁可能只回 RESULT_CANCELED/null；先消费被替换流程的结果。
        if (supersededResults > 0 && (id == null || id < sequence)) {
            savedState[SUPERSEDED_RESULTS] = --supersededResults
            return false
        }
        if (id != null && id != sequence) return false
        if (repository.snapshots.value.authenticated) setAuthentication(AuthLaunchState.Idle)
        else setAuthentication(AuthLaunchState.Canceled(sequence))
        return true
    }
    /** singleTask 重入代表新的访问意图，上层认证页可能被系统移除。 */
    fun onNewEntry() {
        if (auth.value is AuthLaunchState.InFlight) savedState[SUPERSEDED_RESULTS] = ++supersededResults
        setAuthentication(AuthLaunchState.Idle)
        requestAuthentication()
    }
    private fun setAuthentication(next: AuthLaunchState) {
        savedState[AUTH_PHASE] = when (next) {
            is AuthLaunchState.Requested -> "requested"
            is AuthLaunchState.InFlight -> "inFlight"
            is AuthLaunchState.Canceled -> "canceled"
            is AuthLaunchState.Failed -> "failed"
            AuthLaunchState.Idle -> "idle"
        }
        auth.value = next
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
    companion object {
        private val authenticationIds = AtomicLong()
        const val SELECTED_TAB = "shell.selected_tab"
        private const val STARTUP_CHECKED = "shell.startup.checked"
        private const val AUTH_SEQUENCE = "shell.auth.sequence"
        private const val SUPERSEDED_RESULTS = "shell.auth.supersededResults"
        private const val AUTH_PHASE = "shell.auth.phase"
    }
}

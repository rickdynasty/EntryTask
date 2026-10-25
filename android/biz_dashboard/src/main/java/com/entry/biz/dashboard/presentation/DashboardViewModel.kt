package com.entry.biz.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entry.biz.dashboard.domain.DashboardPage
import com.entry.biz.dashboard.domain.DashboardRepository
import com.entry.lib.log.MLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DashboardUiState(
    val tab: Int = 1, val page: DashboardPage? = null, val loading: Boolean = false,
    val error: String? = null, val authenticated: Boolean = false,
    val revealAssets: Boolean = false, val category: String? = null,
)

/** 页面VM只接收Shell的Tab/会话投影；取消旧请求，拒绝旧会话/旧选择的迟到响应。 */
class DashboardViewModel(private val repository: DashboardRepository) : ViewModel() {
    private val state = MutableStateFlow(DashboardUiState())
    val uiState = state.asStateFlow()
    private var sessionEpoch: Long? = null
    private var request: Job? = null
    private var generation = 0L
    fun show(tab: Int, authenticated: Boolean, epoch: Long) {
        require(tab in 1..4)
        if (state.value.tab == tab && state.value.authenticated == authenticated && sessionEpoch == epoch) return
        val sameSession = sessionEpoch == epoch
        sessionEpoch = epoch
        state.value = DashboardUiState(tab = tab, authenticated = authenticated,
            revealAssets = sameSession && state.value.revealAssets,
            category = when(tab) { 1 -> "all"; 2 -> "fund"; else -> null })
        reload()
    }
    fun reload() {
        request?.cancel()
        val id = ++generation
        if (!state.value.authenticated) {
            state.value = state.value.copy(page = null, loading = false, error = "请先登录后查看")
            return
        }
        val current = state.value
        state.value = current.copy(loading = true, error = null)
        request = viewModelScope.launch {
            MLog.i("EntryTask.NativeDashboard", "page.load.started tab=${current.tab} flowId=$id")
            try {
                val result = repository.load(current.tab, current.category)
                ensureActive()
                if (generation == id) state.value = state.value.copy(page = result, loading = false)
                MLog.i("EntryTask.NativeDashboard", "page.load.completed tab=${current.tab} flowId=$id")
            } catch (cancelled: CancellationException) {
                MLog.d("EntryTask.NativeDashboard", "page.load.cancelled tab=${current.tab} flowId=$id")
                throw cancelled
            } catch (error: Exception) {
                if (generation == id) state.value = state.value.copy(loading = false, error = "加载失败，请重试")
                MLog.e("EntryTask.NativeDashboard", "page.load.failed tab=${current.tab} flowId=$id exception=${error.javaClass.simpleName}")
            }
        }
    }
    fun selectCategory(category: String) {
        if (state.value.category == category) return
        state.value = state.value.copy(category = category)
        reload()
    }
    fun toggleAssets() { state.value = state.value.copy(revealAssets = !state.value.revealAssets) }
}

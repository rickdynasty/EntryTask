package com.entry.biz.dashboard.presentation.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entry.lib.log.MLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 区域失败独立，刷新失败保留旧值；取消不显示网络错误。 */
data class RegionState<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class PageResource<T>(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(RegionState<T>())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L

    fun load(request: suspend () -> T) {
        cancel()
        val version = generation
        mutable.value = mutable.value.copy(loading = true, error = null)
        job =
            scope.launch {
                try {
                    val data = request()
                    ensureActive()
                    if (version == generation) mutable.value = RegionState(data)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (version == generation)
                        mutable.value = mutable.value.copy(loading = false, error = "加载失败，请重试")
                    MLog.e(
                        "EntryTask.NativeDashboard",
                        "region.failed exception=${e.javaClass.simpleName}",
                    )
                }
            }
    }

    fun replace(data: T) {
        cancel()
        mutable.value = RegionState(data)
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        mutable.value = mutable.value.copy(loading = false)
    }

    fun clear() {
        cancel()
        mutable.value = RegionState()
    }
}

/** VM只接收会话版本和活动状态；离页取消IO，新会话清掉所有旧投影。 */
abstract class RootPageViewModel : ViewModel() {
    private val resources = mutableListOf<PageResource<*>>()
    protected var active = false
    protected var epoch = -1L

    protected fun <T> resource() = PageResource<T>(viewModelScope).also { resources += it }

    fun show(enabled: Boolean, sessionEpoch: Long) {
        if (sessionEpoch != epoch) {
            active = false
            epoch = sessionEpoch
            resources.forEach { it.clear() }
            resetSession()
        }
        if (enabled == active) return
        active = enabled
        if (enabled) refresh()
        else {
            resources.forEach { it.cancel() }
            onInactive()
        }
    }

    protected open fun resetSession() {}

    protected open fun onInactive() {}

    abstract fun refresh()
}

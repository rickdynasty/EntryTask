package com.entry.lib.base.mvvm.viewmodel

/**
 * 收敛状态管理。把传统 ViewModel 散落的多个 MutableStateFlow 收敛到单一 UiState 中，并将导航/Toast 等副作用隔离至 Channel。
 */
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entry.lib.base.mvvm.contract.IUiEffect
import com.entry.lib.base.mvvm.contract.IUiIntent
import com.entry.lib.base.mvvm.contract.IUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BaseViewModel<State : IUiState, Effect : IUiEffect, Intent : IUiIntent>(
    initialState: State
) : ViewModel() {

    // 内部可变的状态流（热流：必须有一个初始值，它永远在内存中保留最新状态）
    private val _uiState = MutableStateFlow(initialState)

    /** 另一种冷流的使用场景：搜索(热流 + 冷流执行)
    // 1. 这是一个【热流】（StateFlow）
    // 它的值一直存在内存里。不管有没有人 collect，只要你调用 onQueryChanged，它的 value 就变了。
    private val searchQueryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            searchQueryFlow
            .debounce(300L)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                // 2. 注意这里！这是一个标准的【冷流】（flow { ... }）
                // 只有当 searchQueryFlow 有了新值、且通过了前面的防抖过滤后，
                // 这个 flow 才会真正被创建并执行里面的网络请求代码！
                flow {
                    emit(apiService.search(query))
                }.flowOn(Dispatchers.IO)
            }
            .collect { results ->
                // 3. 收集结果
            }
        }
    }
     */

    // 外部只读的状态流，确保严格的单向数据流 (SSOT)
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    // 一次性事件通道（BUFFERED 策略防止背压丢失）
    private val _uiEffect = Channel<Effect>(Channel.BUFFERED)
    val uiEffect: Flow<Effect> = _uiEffect.receiveAsFlow()

    // 获取当前状态快照
    protected val currentState: State
        get() = _uiState.value

    /**
     * 统一的意图分发入口：View 层的所有点击或输入动作均交由此方法处理
     */
    abstract fun handleIntent(intent: Intent)

    /**
     * 线程安全地更新 UI 状态
     */
    protected fun updateState(reducer: State.() -> State) {
        _uiState.update { it.reducer() }
    }

    /**
     * 发送一次性副作用（如弹 Toast、页面跳转）
     */
    protected fun sendEffect(builder: () -> Effect) {
        viewModelScope.launch {
            _uiEffect.send(builder())
        }
    }
}
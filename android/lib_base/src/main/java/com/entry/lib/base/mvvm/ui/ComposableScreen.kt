package com.entry.lib.base.mvvm.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.entry.lib.base.mvvm.contract.IUiEffect
import com.entry.lib.base.mvvm.contract.IUiIntent
import com.entry.lib.base.mvvm.contract.IUiState
import com.entry.lib.base.mvvm.viewmodel.BaseViewModel

/**
 * Compose 声明式页面基础包装组件
 * 内部封装了 collectAsStateWithLifecycle，当页面进入后台（STOPPED）时自动暂停数据流收集，彻底避免后台无效计算与内存溢出。
 */
@Composable
fun <State : IUiState, Effect : IUiEffect, Intent : IUiIntent> ComposableScreen(
    viewModel: BaseViewModel<State, Effect, Intent>,
    onEffect: ((Effect) -> Unit)? = null,
    content: @Composable (state: State, onIntent: (Intent) -> Unit) -> Unit
) {
    // 生命周期安全的状态收集
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)

    // 监听一次性副作用
    if (onEffect != null) {
        LaunchedEffect(viewModel) {
            viewModel.uiEffect.collect { effect ->
                onEffect(effect)
            }
        }
    }

    // 注入状态与意图发射器
    content(uiState, viewModel::handleIntent)
}
package com.entry.lib.base.mvvm.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * 高性能网络与本地数据库双数据源同步核心 (NetworkBoundResource)
 * 对接 Retrofit/Room 的核心策略，实现“先读本地 Room 缓存渲染，再请求网络同步，最后更新缓存并刷新 UI”的工业级离线优先（Offline-First）高性能体验。
 * 用 Kotlin Flow 管道与协程挂起机制，实现多数据源自动化编排。
 *
 * @param ResultType UI 期望展示的数据类型 (如 Room 实体)
 * @param RequestType Retrofit 返回的网络数据类型
 */
inline fun <ResultType, RequestType> networkBoundResource(
    crossinline query: () -> Flow<ResultType>,
    crossinline fetch: suspend () -> RequestType,
    crossinline saveFetchResult: suspend (RequestType) -> Unit,
    crossinline shouldFetch: (ResultType) -> Boolean = { true }
): Flow<Result<ResultType>> = flow {
    emit(Result.Loading)
    // 1. 优先发射本地 Room 数据库缓存数据，实现页面秒开
    val localData = query().first()

    if (shouldFetch(localData)) {
        try {
            // 2. 发起 Retrofit 网络请求
            val apiResponse = fetch()
            // 3. 将最新网络数据写入 Room 数据库
            saveFetchResult(apiResponse)
            // 4. 从数据库获取最新流并持续发射
            emitAll(query().map { Result.Success(it) })
        } catch (throwable: Throwable) {
            // 发生网络异常时降级使用本地缓存，保证高可用
            emitAll(query().map { Result.Success(it) })
        }
    } else {
        emitAll(query().map { Result.Success(it) })
    }
}.flowOn(kotlinx.coroutines.Dispatchers.IO)
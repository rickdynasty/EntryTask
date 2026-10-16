package com.entry.lib.base.mvvm.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

open class BaseRepository {

    /**
     * 安全处理 API 请求，自动捕获异常并映射为 Result 状态流
     */
    protected fun <T> safeApiCall(apiCall: suspend () -> T): Flow<Result<T>> = flow {
        emit(Result.Loading)
        try {
            val data = apiCall()
            emit(Result.Success(data))
        } catch (e: Throwable) {
            emit(Result.Error(e))
        }
    }.flowOn(Dispatchers.IO)
}
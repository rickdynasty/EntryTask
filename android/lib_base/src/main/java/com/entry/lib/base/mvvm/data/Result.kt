package com.entry.lib.base.mvvm.data

/**
 * 统一的数据响应包装密封类
 * 标准化所有异步数据源（Retrofit 网络请求、Room 数据库查询）的返回结构
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val code: Int? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}
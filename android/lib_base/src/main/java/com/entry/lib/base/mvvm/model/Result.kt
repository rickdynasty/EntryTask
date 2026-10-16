package com.entry.lib.base.mvvm.model

sealed interface Result<out T> {
    // 移除 Success 后面的 <T>，直接使用接口的 out T
    data class Success<T>(val data: T) : Result<T>

    // Error 不需要泛型 T，直接继承 Result<Nothing> 或不传泛型
    data class Error(val exception: Throwable, val message: String? = exception.localizedMessage) :
        Result<Nothing>

    // 单例 Loading 继承 Result<Nothing>
    object Loading : Result<Nothing>
    object Idle : Result<Nothing> // 增加空闲态，用于表单初始未提交状态
}
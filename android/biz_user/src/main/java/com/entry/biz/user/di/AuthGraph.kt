package com.entry.biz.user.di

import com.entry.biz.user.network.userService
import com.entry.biz.user.repository.UserRepository
import com.entry.biz.user.viewmodel.UserViewModel

/** 认证模块组装入口：View不认识网络实现，VM只依赖领域Repository。 */
object AuthGraph {
    fun viewModelFactory() = UserViewModel.Factory(UserRepository(userService))
}

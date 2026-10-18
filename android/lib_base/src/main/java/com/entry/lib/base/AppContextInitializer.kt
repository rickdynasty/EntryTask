package com.entry.lib.base

import android.content.Context
import androidx.startup.Initializer

class AppContextInitializer : Initializer<Context> {

    override fun create(context: Context): Context {
        // 获取全局的 Application Context 并注入到我们的代理类中
        val appContext = context.applicationContext
        AppGlobals.initialize(appContext)

        // 这里还可以做一些 lib_base 内部通用的基础初始化（如日志、MMKV等）

        return appContext
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // 如果当前模块的初始化依赖于其他 Initializer，可以在这里返回它们的 Class
        // 如果没有依赖，返回空列表
        return emptyList()
    }
}
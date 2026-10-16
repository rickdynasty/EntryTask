package com.entrytask

import android.app.Application
import com.entry.biz.user.net.RetrofitClient
import com.entry.lib.base.mvvm.cache.CacheManager
import com.entry.lib.log.MLog
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

class MainApplication : Application(), ReactApplication {

    override val reactHost: ReactHost by lazy {
        getDefaultReactHost(
            context = applicationContext,
            packageList =
                PackageList(this).packages.apply {
                    // Packages that cannot be autolinked yet can be added manually here, for example:
                    // add(MyReactNativePackage())
                },
        )
    }

    override fun onCreate() {
        super.onCreate()
        // 初始化 MMKV
        CacheManager.init(this)
        RetrofitClient.init(this)
        // 根据 BuildConfig.DEBUG 自动配置：
        // Debug 模式下输出所有等级日志 (VERBOSE)
        // Release 模式下仅输出 WARN 和 ERROR 等级日志，甚至可以全部关闭 (NONE)
        val isDebug = BuildConfig.DEBUG // 实际项目中通常使用 com.entry.lib.base.BuildConfig.DEBUG
        MLog.init(
            enable = true,
            level = if (isDebug) MLog.Level.VERBOSE else MLog.Level.WARN,
            tag = "EntryApp"
        )

        loadReactNative(this)
    }
}

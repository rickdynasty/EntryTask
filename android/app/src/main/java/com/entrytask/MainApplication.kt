package com.entrytask

import android.app.Application
import com.entry.lib.log.MLog
import com.entry.lib.net.NetManager
import com.entry.lib.stroage.StorageManager
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

class MainApplication : Application(), ReactApplication {
    val shellGraph by lazy { com.entrytask.shell.di.ShellGraph() }
    val prototypeNetwork: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(false)
            .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(com.entry.demo.PrototypeMockInterceptor(this)).build()
    }


    override val reactHost: ReactHost by lazy {
        getDefaultReactHost(
            context = applicationContext,
            jsMainModulePath = "src/index",
            packageList =
                PackageList(this).packages.apply {
                    // Packages that cannot be autolinked yet can be added manually here, for example:
                    add(com.entrytask.bridge.EntryTaskPackage())
                },
        )
    }

    override fun onCreate() {
        super.onCreate()
        // 根据 BuildConfig.DEBUG 自动配置：
        // Debug 模式下输出所有等级日志 (VERBOSE)
        // Release 模式下仅输出 WARN 和 ERROR 等级日志，甚至可以全部关闭 (NONE)
        val isDebug = BuildConfig.DEBUG // 实际项目中通常使用 com.entry.lib.base.BuildConfig.DEBUG
        MLog.init(
            enable = true,
            level = if (isDebug) MLog.Level.VERBOSE else MLog.Level.WARN,
            tag = "EntryApp"
        )

        MLog.i("EntryTask.Bridge", "application.started version=${BuildConfig.VERSION_NAME} debug=${BuildConfig.DEBUG} entry=src/index")
        // 初始化 MMKV
        StorageManager.init(this)
        getSharedPreferences("app_default_pref", MODE_PRIVATE).edit()
            .remove("key_token").remove("mock_db_password").remove("mock_db_username").apply()
        NetManager.initMock()

        loadReactNative(this)
    }
}

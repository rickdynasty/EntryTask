package com.entrytask

import android.app.Application
import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.os.SystemClock
import com.entrytask.shell.host.RootContentHostFragment
import com.entrytask.shell.runtime.AuthFrameTiming
import com.entrytask.shell.runtime.AuthWarmupLifecycle
import com.entrytask.shell.runtime.RnWarmupController
import com.entrytask.shell.runtime.WarmupHandle
import com.entrytask.shell.runtime.WarmupPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.entry.lib.log.MLog
import com.entry.lib.net.NetManager
import com.entry.lib.stroage.StorageManager
import com.entry.lib.stroage.room.RoomStorage
import com.entry.lib.net.MockInterceptor
import com.entry.lib.net.mock.MockAccountStore
import com.entrytask.storage.RoomMockAccountStorage
import com.entrytask.storage.SharedPreferencesLegacyAccounts
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

class MainApplication : Application(), ReactApplication {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val authFrameTiming = AuthFrameTiming()
    @Volatile private var memoryPressure = false
    val rnWarmup by lazy {
        RnWarmupController(BuildConfig.RN_WARMUP_ENABLED, runtimeScope, {
            // start() 初始化现有 Host；不加载 ReactFragment、不改变其宿主生命周期。
            val task = reactHost.start()
            WarmupHandle {
                when {
                    !task.isCompleted() -> null
                    task.isCancelled() -> WarmupPhase.CANCELED
                    task.isFaulted() -> WarmupPhase.FAILED
                    else -> WarmupPhase.READY
                }
            }
        }, SystemClock::elapsedRealtime) { value ->
            val elapsed = value.endedMs?.let { it - (value.startedMs ?: it) }
            val message = "warmup.${value.phase.name.lowercase()} durationMs=$elapsed"
            if (value.phase in setOf(WarmupPhase.FAILED, WarmupPhase.TIMED_OUT)) MLog.w("EntryTask.RNStartup", message)
            else MLog.i("EntryTask.RNStartup", message)
        }
    }
    // Application只持有一个Room实例；实际打开和迁移延后到后台认证请求。
    private val authDatabase by lazy { RoomStorage.open(this) }
    private val authAccounts by lazy {
        MockAccountStore(
            RoomMockAccountStorage(
                authDatabase.credentials,
                SharedPreferencesLegacyAccounts(getSharedPreferences("app_default_pref", MODE_PRIVATE)),
            )
        )
    }
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
            exceptionHandler = { error ->
                if (rnWarmup.handlesPreloadException()) {
                    // 不能让可选预热使 Native 登录崩溃；不记录异常载荷或吞掉页面运行异常。
                    MLog.e("EntryTask.RNStartup", "warmup.engine.failed code=RN_PRELOAD_FAILED exception=${error.javaClass.simpleName}")
                } else throw error
            },
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
        // 初始化既有普通偏好存储，账号改由Room持久化。
        StorageManager.init(this)
        getSharedPreferences("app_default_pref", MODE_PRIVATE).edit()
            .remove("key_token").apply()
        // 认证拦截器后台迁移旧SP，提交Room后再清旧键；启动时不读账号库。
        NetManager.initMock(MockInterceptor(this) { authAccounts })

        loadReactNative(this)
        registerActivityLifecycleCallbacks(AuthWarmupLifecycle(authFrameTiming) {
            if (RootContentHostFragment.usesReactNative()) {
                val lowRam = getSystemService(ActivityManager::class.java).isLowRamDevice
                rnWarmup.afterAuthFrame(lowRam || memoryPressure)
            }
        })
    }

    override fun onLowMemory() {
        super.onLowMemory()
        memoryPressure = true
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // UI_HIDDEN 只是后台，不等同低内存；已启动的共享引擎不在此销毁。
        if (level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) memoryPressure = true
    }
}

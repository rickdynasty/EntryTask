package com.entrytask.bridge

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

/** 将 Codegen 生成的五个 TurboModule 实现注册到唯一 ReactHost。 */
class EntryTaskPackage : BaseReactPackage() {
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? = when (name) {
        "NativeHost" -> HostModule(reactContext)
        "NativeNetwork" -> NetworkModule(reactContext)
        "NativeStorage" -> StorageModule(reactContext)
        "NativeSession" -> SessionModule(reactContext)
        "NativeTelemetry" -> TelemetryModule(reactContext)
        else -> null
    }
    override fun getReactModuleInfoProvider() = ReactModuleInfoProvider {
        mapOf("NativeHost" to HostModule::class.java, "NativeNetwork" to NetworkModule::class.java,
            "NativeStorage" to StorageModule::class.java, "NativeSession" to SessionModule::class.java,
            "NativeTelemetry" to TelemetryModule::class.java).mapValues { (name, clazz) ->
            ReactModuleInfo(name, clazz.name, false, false, false, true)
        }
    }
}

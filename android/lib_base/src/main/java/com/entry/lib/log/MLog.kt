package com.entry.lib.log

import android.util.Log

object MLog {

    /**
     * 日志等级枚举
     */
    enum class Level(val priority: Int) {
        VERBOSE(Log.VERBOSE),
        DEBUG(Log.DEBUG),
        INFO(Log.INFO),
        WARN(Log.WARN),
        ERROR(Log.ERROR),
        NONE(Int.MAX_VALUE) // 不打印任何日志
    }

    private const val DEFAULT_TAG = "MLog_Default"

    // 全局配置项
    var isEnabled: Boolean = true
    var minLogLevel: Level = Level.VERBOSE
    var globalTag: String = DEFAULT_TAG

    /**
     * 初始化日志配置（通常在 Application 中调用）
     */
    fun init(enable: Boolean = true, level: Level = Level.VERBOSE, tag: String = DEFAULT_TAG) {
        isEnabled = enable
        minLogLevel = level
        globalTag = tag
    }

    // ================= 1. 简便的单参数重载（自动使用全局 Tag） =================
    fun v(message: String) = log(Level.VERBOSE, globalTag, message)
    fun d(message: String) = log(Level.DEBUG, globalTag, message)
    fun i(message: String) = log(Level.INFO, globalTag, message)
    fun w(message: String, throwable: Throwable? = null) =
        log(Level.WARN, globalTag, message, throwable)

    fun e(message: String, throwable: Throwable? = null) =
        log(Level.ERROR, globalTag, message, throwable)

    // ================= 2. 标准的双参数重载（自定义 Tag） =================
    fun v(tag: String, message: String) = log(Level.VERBOSE, tag, message)
    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.ERROR, tag, message, throwable)

    /**
     * 核心日志输出实现
     */
    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        if (level.priority < minLogLevel.priority) return

        when (level) {
            Level.VERBOSE -> Log.v(tag, message)
            Level.DEBUG -> Log.d(tag, message)
            Level.INFO -> Log.i(tag, message)
            Level.WARN -> {
                if (throwable != null) Log.w(tag, message, throwable)
                else Log.w(tag, message)
            }

            Level.ERROR -> {
                if (throwable != null) Log.e(tag, message, throwable)
                else Log.e(tag, message)
            }

            Level.NONE -> {}
        }
    }
}
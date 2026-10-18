package com.entry.lib.ui

import android.content.Context
import android.widget.Toast
import com.entry.lib.log.MLog

/**
 * 双击退出应用辅助类
 * 负责记录时间差，提供统一的“双击退出”防抖逻辑
 */
object BackPressHelper {
    /** IME consumes the first back before authentication or RN navigation. */
    fun hideKeyboardIfVisible(activity: android.app.Activity): Boolean {
        val visible = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        if (visible) androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        return visible
    }


    private const val TAG = "BackPressHelper"
    private var lastPressTime: Long = 0L
    private const val EXIT_TIME_INTERVAL: Long = 500L // 0.5秒之内连续点击有效

    /**
     * 处理返回键事件
     * @param context 上下文
     * @param onExitDoublePress 达到双击条件时的回调（如 finishAffinity）
     */
    fun handleBackPress(context: Context, onExitDoublePress: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPressTime < EXIT_TIME_INTERVAL) {
            MLog.d(TAG, "检测到双击返回，执行退出应用")
            onExitDoublePress()
        } else {
            lastPressTime = currentTime
            MLog.d(TAG, "第一次点击返回，提示双击退出")
            Toast.makeText(context, "双击退出应用", Toast.LENGTH_SHORT).show()
        }
    }
}
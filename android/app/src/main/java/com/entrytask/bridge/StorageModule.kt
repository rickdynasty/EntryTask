package com.entrytask.bridge

import android.content.Context
import com.entry.biz.user.manager.UserAuthManager
import com.entrytask.specs.NativeStorageSpec
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import org.json.JSONObject
import java.util.concurrent.Executors

/** 提供受控的 RN 偏好读写；数据按 Native 会话 scope 隔离。 */
class StorageModule(context: ReactApplicationContext) : NativeStorageSpec(context) {
    private val io = Executors.newSingleThreadExecutor()
    override fun read(requestJson: String, promise: Promise) = perform(requestJson, promise, "read")
    override fun write(requestJson: String, promise: Promise) = perform(requestJson, promise, "write")
    override fun remove(requestJson: String, promise: Promise) = perform(requestJson, promise, "remove")
    private fun perform(raw: String, promise: Promise, operation: String) {
        // 磁盘访问放在专用串行线程，避免阻塞 React Native 或 UI 线程。
        io.execute {
            try {
                val input = JSONObject(raw); require(input.getString("key") == "assetVisibility")
                val epoch = UserAuthManager.sessionEpoch
                val scope = UserAuthManager.storageScope() ?: throw IllegalStateException("AUTH_REQUIRED")
                val prefs = reactApplicationContext.getSharedPreferences("rn_preferences_v1", Context.MODE_PRIVATE)
                val key = "$scope.assetVisibility"
                val result = when (operation) {
                    "read" -> prefs.getBoolean(key, false)
                    "write" -> { require(input.get("value") is Boolean); check(prefs.edit().putBoolean(key, input.getBoolean("value")).commit()); true }
                    else -> { check(prefs.edit().remove(key).commit()); true }
                }
                promise.resolve(if (epoch == UserAuthManager.sessionEpoch) BridgeReply.ok(result) else BridgeReply.error("STALE_SESSION"))
            } catch (e: Exception) { promise.resolve(BridgeReply.error("STORAGE_ERROR")) }
        }
    }
    override fun invalidate() { io.shutdownNow(); super.invalidate() }
}

package com.entrytask.bridge

import com.entry.lib.log.MLog
import com.entrytask.MainActivity
import com.entrytask.BuildConfig
import androidx.lifecycle.Lifecycle
import com.entrytask.specs.NativeHostSpec
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.util.UUID

/** Native 宿主桥接：仅转发受控导航/认证命令并提供启动握手与 Shell 事件。 */
class HostModule(context: ReactApplicationContext) : NativeHostSpec(context) {
    companion object {
        private val bootId = UUID.randomUUID().toString()
        private const val TAG = "EntryTask.Bridge"
        private const val CONTRACT_PATH = "contracts/bridge/runtime.json"
    }

    private val stopObserving = ShellCoordinator.observe { emitOnHostEvent(it) }
    override fun command(requestJson: String, promise: Promise) {
        try {
            val request = JSONObject(requestJson)
            val commandType = request.optString("type").replace(Regex("[^A-Za-z]"), "").take(40)
            MLog.i(TAG, "host.command.received type=$commandType")
            val activity = reactApplicationContext.currentActivity as? MainActivity
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                MLog.e(TAG, "host.command.failed code=HOST_UNAVAILABLE type=$commandType")
                promise.resolve(BridgeReply.error("HOST_UNAVAILABLE")); return
            }
            activity.runOnUiThread {
                try {
                    // 展示认证页前在主线程重查宿主生命周期，避免应用切后台后启动页面。
                    if (activity.isFinishing || activity.isDestroyed ||
                        (request.optString("type") == "authenticate" &&
                            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
                        MLog.e(TAG, "host.command.failed code=HOST_NOT_READY type=$commandType")
                        promise.resolve(BridgeReply.error("HOST_NOT_READY"))
                        return@runOnUiThread
                    }
                    activity.handleHostCommand(request)
                    // ACK 只表示命令已受理；登录结果以后续会话快照为准。
                    MLog.i(TAG, "host.command.completed type=$commandType")
                    promise.resolve(BridgeReply.ok(true))
                } catch (e: Exception) {
                    MLog.e(TAG, "host.command.failed type=$commandType exception=${e.javaClass.simpleName}")
                    promise.resolve(BridgeReply.error(if (e is IllegalArgumentException) "INVALID_HOST_COMMAND" else "HOST_COMMAND_FAILED"))
                }
            }
        } catch (e: Exception) {
            MLog.e(TAG, "host.command.failed code=INVALID_HOST_COMMAND exception=${e.javaClass.simpleName}")
            promise.resolve(BridgeReply.error("INVALID_HOST_COMMAND"))
        }
    }
    override fun invalidate() { stopObserving(); super.invalidate() }
    override fun bootstrap(requestJson: String, promise: Promise) {
        var flowId = "unknown"
        var stage = "request.decode"
        try {
            val request = JSONObject(requestJson)
            // Only an opaque diagnostic ID is logged. Never log bridge payloads.
            flowId = request.optString("flowId").take(80).replace(Regex("[^a-zA-Z0-9-]"), "")
            MLog.i(TAG, "bootstrap.started flowId=$flowId")
            stage = "contract.read"
            val contract = JSONObject(reactApplicationContext.assets.open(CONTRACT_PATH).bufferedReader().use { it.readText() })
            stage = "contract.validate"
            if (request.optString("protocolVersion") != contract.getString("protocolVersion")) {
                MLog.e(TAG, "bootstrap.failed code=PROTOCOL_VERSION_MISMATCH stage=$stage flowId=$flowId")
                promise.resolve(BridgeReply.error("PROTOCOL_VERSION_MISMATCH"))
                return
            }
            val snapshot = JSONObject().put("protocolVersion", contract.getString("protocolVersion"))
                .put("componentName", contract.getString("componentName")).put("bootId", bootId).put("shell", ShellCoordinator.snapshot())
                .put("capabilities", JSONArray(listOf("host.events", "host.command", "network.execute", "storage.assetVisibility", "session.snapshot", "telemetry.emitBatch")))
            MLog.i(TAG, "bootstrap.completed flowId=$flowId bootId=$bootId")
            promise.resolve(BridgeReply.ok(snapshot))
        } catch (e: Exception) {
            val code = if (e is FileNotFoundException) "HOST_RESOURCE_MISSING" else "HOST_RESOURCE_INVALID"
            MLog.e(TAG, "bootstrap.failed code=$code stage=$stage flowId=$flowId resource=$CONTRACT_PATH exception=${e.javaClass.simpleName}", if (BuildConfig.DEBUG) e else null)
            promise.resolve(BridgeReply.error(code))
        }
    }
}

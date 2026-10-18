package com.entrytask.bridge

import com.entry.lib.log.MLog
import com.entrytask.MainActivity
import com.entrytask.BuildConfig
import com.entrytask.specs.NativeHostSpec
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.util.UUID

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
            val activity = reactApplicationContext.currentActivity as? MainActivity
            if (activity == null) { promise.resolve(BridgeReply.error("HOST_UNAVAILABLE")); return }
            activity.runOnUiThread {
                try {
                    activity.handleHostCommand(request)
                    promise.resolve(BridgeReply.ok(true))
                } catch (e: Exception) {
                    MLog.e(TAG, "host.command.failed exception=${e.javaClass.simpleName}")
                    promise.resolve(BridgeReply.error("INVALID_HOST_COMMAND"))
                }
            }
        } catch (e: Exception) { promise.resolve(BridgeReply.error("INVALID_HOST_COMMAND")) }
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

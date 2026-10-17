package com.entrytask.bridge

import com.entrytask.specs.NativeHostSpec
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class HostModule(context: ReactApplicationContext) : NativeHostSpec(context) {
    companion object { private val bootId = UUID.randomUUID().toString() }
    override fun bootstrap(requestJson: String, promise: Promise) {
        try {
            val contract = JSONObject(reactApplicationContext.assets.open("contracts/bridge/runtime.json").bufferedReader().use { it.readText() })
            if (JSONObject(requestJson).optString("protocolVersion") != contract.getString("protocolVersion")) {
                promise.resolve(BridgeReply.error("PROTOCOL_VERSION_MISMATCH")); return
            }
            promise.resolve(BridgeReply.ok(JSONObject().put("protocolVersion", contract.getString("protocolVersion"))
                .put("componentName", contract.getString("componentName")).put("bootId", bootId)
                .put("capabilities", JSONArray(listOf("network.execute", "storage.assetVisibility", "session.snapshot", "telemetry.emitBatch")))))
        } catch (e: Exception) { promise.resolve(BridgeReply.error("HOST_RESOURCE_INVALID")) }
    }
}

package com.entrytask.bridge

import org.json.JSONObject

internal object BridgeReply {
    fun ok(data: Any): String = JSONObject().put("outcome", "ok").put("data", data).toString()
    fun error(code: String): String = JSONObject().put("outcome", "error")
        .put("error", JSONObject().put("code", code)).toString()
}

package com.entrytask.bridge

import org.json.JSONObject

internal object BridgeReply {
    /** 使用统一 JSON 信封返回桥接成功结果。 */
    fun ok(data: Any): String = JSONObject().put("outcome", "ok").put("data", data).toString()
    /** 使用稳定错误码返回桥接失败，不向 RN 暴露原生异常内容。 */
    fun error(code: String): String = JSONObject().put("outcome", "error")
        .put("error", JSONObject().put("code", code)).toString()
}

package com.entrytask.bridge

import com.entry.biz.user.manager.UserAuthManager
import com.entrytask.specs.NativeSessionSpec
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import org.json.JSONObject

/** Foundation only: no credentials or mutation APIs are exposed to JavaScript. */
class SessionModule(context: ReactApplicationContext) : NativeSessionSpec(context) {
    override fun getSnapshot(requestJson: String, promise: Promise) {
        promise.resolve(BridgeReply.ok(JSONObject().put("authenticated", UserAuthManager.isLogin()).put("sessionEpoch", UserAuthManager.sessionEpoch)))
    }
}

package com.entrytask.bridge

import com.entry.biz.user.manager.UserAuthManager
import com.entry.lib.log.MLog
import com.entrytask.specs.NativeSessionSpec
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import org.json.JSONObject

/** 会话只读桥接：向 RN 暴露登录状态和版本号，不暴露凭证或修改接口。 */
class SessionModule(context: ReactApplicationContext) : NativeSessionSpec(context) {
    companion object { private const val TAG = "EntryTask.Bridge" }

    override fun getSnapshot(requestJson: String, promise: Promise) {
        try {
            val (authenticated, epoch) = UserAuthManager.sessionSnapshot()
            val snapshot = JSONObject()
                .put("authenticated", authenticated)
                .put("sessionEpoch", epoch)
            MLog.i(TAG, "session.snapshot.completed authenticated=${snapshot.getBoolean("authenticated")} epoch=${snapshot.getLong("sessionEpoch")}")
            promise.resolve(BridgeReply.ok(snapshot))
        } catch (error: Exception) {
            MLog.e(TAG, "session.snapshot.failed exception=${error.javaClass.simpleName}")
            promise.resolve(BridgeReply.error("SESSION_UNAVAILABLE"))
        }
    }
}

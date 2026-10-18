package com.entrytask.bridge

import com.entry.biz.user.manager.UserAuthManager
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

/** Native owns root tab and chrome; RN owns the one child stack. No Activity retained here. */
object ShellCoordinator {
    private var revision = 0L
    var tabId: Int = 1
        private set
    var depth: Int = 0
        private set
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()
    fun snapshot(): JSONObject = JSONObject().put("revision", revision).put("tabId", tabId).put("depth", depth)
        .put("authenticated", UserAuthManager.isLogin()).put("sessionEpoch", UserAuthManager.sessionEpoch)
    fun observe(listener: (String) -> Unit): () -> Unit { listeners.add(listener); return { listeners.remove(listener) } }
    fun publish() { revision++; val json = snapshot().toString(); listeners.forEach { it(json) } }
    fun select(tab: Int) { require(tab in 1..4); tabId = tab; publish() }
    fun navigationDepth(value: Int) { require(value in 0..20); depth = value }
}

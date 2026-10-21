package com.entrytask.bridge

import com.entry.biz.user.manager.UserAuthManager
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

/** Native 持有根 Tab 和外壳；RN 使用单个子页面栈，本对象不保留 Activity。 */
object ShellCoordinator {
    private var revision = 0L
    var tabId: Int = 1
        private set
    var depth: Int = 0
        private set
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()
    /** 生成带版本的宿主快照，登录状态始终读取 Native 会话权威。 */
    fun snapshot(): JSONObject {
        val (authenticated, epoch) = UserAuthManager.sessionSnapshot()
        return JSONObject().put("revision", revision).put("tabId", tabId).put("depth", depth)
            .put("authenticated", authenticated).put("sessionEpoch", epoch)
    }
    fun observe(listener: (String) -> Unit): () -> Unit { listeners.add(listener); return { listeners.remove(listener) } }
    /** 通知 RN 同步最新 Tab、导航深度和认证状态。 */
    fun publish() { revision++; val json = snapshot().toString(); listeners.forEach { it(json) } }
    fun select(tab: Int) { require(tab in 1..4); tabId = tab; publish() }
    fun navigationDepth(value: Int) { require(value in 0..20); depth = value }
}

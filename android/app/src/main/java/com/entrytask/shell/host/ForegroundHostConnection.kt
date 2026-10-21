package com.entrytask.shell.host

/** 只在 Activity RESUMED 区间绑定；不让进程单例永久持有 Activity/VM。主线程调用。 */
class ForegroundHostConnection {
    // 仅保存流程编号，不持有后台 Activity；登录页显示时合并 RN 的重复认证请求。
    private var authenticationId: Long? = null
    fun authenticationStarted(id: Long) { authenticationId = id }
    fun authenticationFinished(id: Long? = authenticationId) {
        if (authenticationId == id) authenticationId = null
    }
    private var owner: Any? = null
    private var request: (() -> Unit)? = null
    fun bind(onAuthenticate: () -> Unit): () -> Unit {
        val token = Any()
        owner = token
        request = onAuthenticate
        return {
            // 旧 Activity 的迟到清理不能解绑新实例。
            if (owner === token) { owner = null; request = null }
        }
    }
    fun authenticate() {
        if (authenticationId != null) return
        val receiver = request ?: throw HostNotReadyException()
        receiver()
    }
}

class HostNotReadyException : IllegalStateException("HOST_NOT_READY")

package com.entrytask.shell.host

/** 只在 Activity RESUMED 区间绑定；不让进程单例永久持有 Activity/VM。主线程调用。 */
class ForegroundHostConnection {
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
        val receiver = request ?: throw HostNotReadyException()
        receiver()
    }
}

class HostNotReadyException : IllegalStateException("HOST_NOT_READY")

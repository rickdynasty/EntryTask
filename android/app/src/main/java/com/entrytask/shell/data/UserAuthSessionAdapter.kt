package com.entrytask.shell.data

import com.entry.biz.user.manager.UserAuthManager
import com.entrytask.shell.domain.NativeSessionPort
import com.entrytask.shell.domain.SessionSnapshot

/** 只读投影原有会话，不创建第二个登录标记或凭证存储。 */
class UserAuthSessionAdapter : NativeSessionPort {
    override fun current(): SessionSnapshot {
        val (authenticated, epoch) = UserAuthManager.sessionSnapshot()
        return SessionSnapshot(authenticated, epoch)
    }
    override fun logout() = UserAuthManager.logout()
}

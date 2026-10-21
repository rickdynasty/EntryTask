package com.entrytask.shell

import com.entrytask.shell.host.ForegroundHostConnection
import com.entrytask.shell.host.HostNotReadyException
import org.junit.Assert.*
import org.junit.Test

/** 登录覆盖 MainActivity 时重复 Bridge 请求应被合并，不依赖后台 Activity 引用。 */
class ForegroundHostConnectionTest {
    @Test fun duplicateRequestWhileAuthenticationIsShowingIsAcceptedWithoutForegroundHost() {
        val host = ForegroundHostConnection()
        var requests = 0
        val unbind = host.bind { requests++ }
        host.authenticate()
        host.authenticationStarted(1)
        unbind()
        host.authenticate()
        assertEquals(1, requests)
        host.authenticationFinished(0) // 旧结果不能解除新流程。
        host.authenticate()
        host.authenticationFinished(1)
        assertThrows(HostNotReadyException::class.java) { host.authenticate() }
    }
    @Test fun oldActivityCleanupCannotDisconnectReplacement() {
        val host = ForegroundHostConnection()
        val old = host.bind { fail("old activity") }
        var called = false
        host.bind { called = true }
        old()
        host.authenticate()
        assertTrue(called)
    }
}

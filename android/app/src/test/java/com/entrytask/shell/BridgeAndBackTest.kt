package com.entrytask.shell

import com.entrytask.bridge.ShellBridgeAdapter
import com.entrytask.shell.data.DefaultShellRepository
import com.entrytask.shell.domain.*
import com.entrytask.shell.host.ForegroundHostConnection
import com.entrytask.shell.host.HostNotReadyException
import org.junit.Assert.*
import org.junit.Test

class BridgeAndBackTest {
    @Test fun oldHostUnbindCannotRemoveNewForegroundReceiver() {
        val connection = ForegroundHostConnection()
        var old = 0
        var current = 0
        val stopOld = connection.bind { old++ }
        val stopCurrent = connection.bind { current++ }
        stopOld()
        connection.authenticate()
        assertEquals(0, old)
        assertEquals(1, current)
        stopCurrent()
        assertThrows(HostNotReadyException::class.java) { connection.authenticate() }
    }
    @Test fun bridgeValidatesNumbersAndRequiresForegroundOnlyForPresentation() {
        val session = FakeSession()
        val repository = DefaultShellRepository(session)
        val bridge = ShellBridgeAdapter(repository, ForegroundHostConnection())
        assertThrows(IllegalArgumentException::class.java) { bridge.decode("{\"type\":\"selectTab\",\"tabId\":1.5}") }
        assertThrows(IllegalArgumentException::class.java) { bridge.decode("{\"type\":\"navigation\",\"depth\":\"2\"}") }
        assertThrows(HostNotReadyException::class.java) { bridge.execute(ShellCommand.Authenticate) }
        bridge.execute(bridge.decode("{\"type\":\"selectTab\",\"tabId\":2}"))
        assertEquals(2, bridge.snapshot().getInt("tabId"))
        session.login()
        bridge.execute(ShellCommand.Authenticate)
        assertTrue(bridge.snapshot().getBoolean("authenticated"))
    }
    @Test fun rnChildConflictNeverExitsAndRootTimerUsesMonotonicClock() {
        var time = 0L
        val policy = RootBackPolicy({ time })
        assertEquals(RootBackAction.Resynchronize, policy.decide(ShellSnapshot(depth = 1)))
        assertEquals(RootBackAction.SwitchToHome, policy.decide(ShellSnapshot(tabId = 2)))
        assertEquals(RootBackAction.ShowExitHint, policy.decide(ShellSnapshot()))
        time = 499
        assertEquals(RootBackAction.Exit, policy.decide(ShellSnapshot()))
        time = 1000
        assertEquals(RootBackAction.ShowExitHint, policy.decide(ShellSnapshot()))
        time = 1500
        assertEquals(RootBackAction.ShowExitHint, policy.decide(ShellSnapshot()))
    }
}

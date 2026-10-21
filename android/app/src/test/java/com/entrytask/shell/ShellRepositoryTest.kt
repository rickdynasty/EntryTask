package com.entrytask.shell

import com.entrytask.shell.data.DefaultShellRepository
import com.entrytask.shell.domain.NativeSessionPort
import com.entrytask.shell.domain.SessionSnapshot
import org.junit.Assert.*
import org.junit.Test

/** 测试会话仅保存在测试内存，不写入用户现有演示账号。 */
internal class FakeSession(authenticated: Boolean = false, epoch: Long = 0) : NativeSessionPort {
    var snapshot = SessionSnapshot(authenticated, epoch)
    override fun current() = snapshot
    override fun logout() { snapshot = SessionSnapshot(false, snapshot.epoch + 1) }
    fun login() { snapshot = SessionSnapshot(true, snapshot.epoch + 1) }
}

class ShellRepositoryTest {
    @Test fun equalCommandsDoNotEchoButResumeCanRepublish() {
        val repository = DefaultShellRepository(FakeSession())
        repository.selectTab(2)
        repository.updateDepth(1)
        val revision = repository.snapshots.value.revision
        repository.selectTab(2)
        repository.updateDepth(1)
        repository.refreshSession()
        assertEquals(revision, repository.snapshots.value.revision)
        repository.refreshSession(forcePublish = true)
        assertEquals(revision + 1, repository.snapshots.value.revision)
    }
    @Test fun logoutResetsPrivateNavigationAndAdvancesEpoch() {
        val session = FakeSession(true, 3)
        val repository = DefaultShellRepository(session)
        repository.selectTab(4)
        repository.updateDepth(2)
        repository.logout()
        val state = repository.snapshots.value
        assertFalse(state.authenticated)
        assertEquals(4L, state.sessionEpoch)
        assertEquals(1, state.tabId)
        assertEquals(0, state.depth)
    }
    @Test fun sameProcessStateWinsAndGuestCannotRestorePrivateTab() {
        val repository = DefaultShellRepository(FakeSession(true, 1))
        repository.selectTab(3)
        repository.restoreSelection(4)
        assertEquals(3, repository.snapshots.value.tabId)
        val restored = DefaultShellRepository(FakeSession(true, 1))
        restored.restoreSelection(4)
        assertEquals(4, restored.snapshots.value.tabId)
        val coldGuest = DefaultShellRepository(FakeSession())
        coldGuest.restoreSelection(4)
        assertEquals(1, coldGuest.snapshots.value.tabId)
    }
    @Test fun sessionCannotMoveBackwardsAndExpiryClearsNavigation() {
        val session = FakeSession(true, 5)
        val repository = DefaultShellRepository(session)
        repository.selectTab(3)
        repository.updateDepth(2)
        session.snapshot = SessionSnapshot(false, 4)
        repository.refreshSession()
        assertTrue(repository.snapshots.value.authenticated)
        session.snapshot = SessionSnapshot(false, 6)
        repository.refreshSession()
        assertEquals(1, repository.snapshots.value.tabId)
        assertEquals(0, repository.snapshots.value.depth)
    }
    @Test fun invalidTabOrDepthDoesNotChangeState() {
        val repository = DefaultShellRepository(FakeSession())
        val before = repository.snapshots.value
        assertThrows(IllegalArgumentException::class.java) { repository.selectTab(5) }
        assertThrows(IllegalArgumentException::class.java) { repository.updateDepth(-1) }
        assertEquals(before, repository.snapshots.value)
    }
}

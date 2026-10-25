package com.entry.lib.net.mock

import org.junit.Assert.*
import org.junit.Test

/** 使用独立内存磁盘替身，不访问设备已注册账号。 */
class MockAccountStoreTest {
    private class Storage : MockAccountStorage {
        val disk = mutableMapOf<String, String>()
        var canWrite = true
        override fun read(key: String) = disk[key]
        override fun create(key: String, verifier: String): Boolean {
            check(canWrite) { "AUTH_STORAGE_WRITE_FAILED" }
            if (key in disk) return false
            disk[key] = verifier
            return true
        }
    }

    @Test fun registeredCredentialsSurviveNewStoreAndPasswordsAreExact() {
        val storage = Storage()
        assertEquals(MockAccountStore.Registration.CREATED, MockAccountStore(storage).register(" demo ", " pass "))
        val restarted = MockAccountStore(storage)
        assertTrue(restarted.verify("demo", " pass "))
        assertFalse(restarted.verify("demo", "pass"))
        assertFalse(restarted.verify("other", " pass "))
        assertEquals(1, storage.disk.size)
        assertFalse(storage.disk.toString().contains(" pass "))
    }

    @Test fun duplicateRegistrationDoesNotOverwriteAndNewAccountsDoNotReplaceOldOnes() {
        val storage = Storage()
        val accounts = MockAccountStore(storage)
        accounts.register("first", "first-password")
        assertEquals(MockAccountStore.Registration.EXISTS, accounts.register(" first ", "replacement"))
        accounts.register("second", "second-password")
        assertTrue(accounts.verify("first", "first-password"))
        assertFalse(accounts.verify("first", "replacement"))
        assertTrue(accounts.verify("second", "second-password"))
    }

    @Test fun failedRegistrationDoesNotReportSuccess() {
        val storage = Storage().apply { canWrite = false }
        try { MockAccountStore(storage).register("demo", "test-password"); fail("must reject failed commit") }
        catch (expected: IllegalStateException) { assertEquals("AUTH_STORAGE_WRITE_FAILED", expected.message) }
        assertTrue(storage.disk.isEmpty())
    }
}

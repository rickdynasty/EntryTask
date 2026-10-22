package com.entry.lib.net.mock

import org.junit.Assert.*
import org.junit.Test

/** 使用独立内存磁盘替身，不访问设备已注册账号。 */
class MockAccountStoreTest {
    private class Storage : MockAccountStorage {
        val disk = mutableMapOf<String, String>()
        var canWrite = true
        override fun read(key: String) = disk[key]
        override fun commit(values: Map<String, String>, removals: Set<String>): Boolean {
            if (!canWrite) return false
            removals.forEach(disk::remove)
            disk.putAll(values)
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

    @Test fun bothLegacyVersionsMigrateWithoutDeletingOtherPreferences() {
        val storage = Storage().apply { disk.putAll(mapOf(
            "mock_reg_username" to "legacy-one", "mock_reg_password" to "legacy-one-password",
            "mock_db_username" to "legacy-two", "mock_db_password" to "legacy-two-password",
            "unrelated" to "keep",
        )) }
        val accounts = MockAccountStore(storage)
        assertTrue(accounts.verify("legacy-one", "legacy-one-password"))
        assertTrue(MockAccountStore(storage).verify("legacy-two", "legacy-two-password"))
        assertEquals("keep", storage.disk["unrelated"])
        assertFalse(storage.disk.keys.any { it.startsWith("mock_reg") || it.startsWith("mock_db") })
        assertFalse(storage.disk.values.any { it == "legacy-one-password" || it == "legacy-two-password" })
    }

    @Test fun existingCurrentAccountWinsWhenLegacyNamesCollide() {
        val storage = Storage().apply { disk.putAll(mapOf(
            "mock_reg_username" to "same", "mock_reg_password" to "current-password",
            "mock_db_username" to "same", "mock_db_password" to "older-password",
        )) }
        assertTrue(MockAccountStore(storage).verify("same", "current-password"))
        assertFalse(MockAccountStore(storage).verify("same", "older-password"))
    }

    @Test fun failedMigrationPreservesLegacyCredentialsAndCanRetry() {
        val storage = Storage().apply {
            disk.putAll(mapOf("mock_reg_username" to "legacy", "mock_reg_password" to "old-password"))
            canWrite = false
        }
        try { MockAccountStore(storage).verify("legacy", "old-password"); fail("must reject failed commit") }
        catch (expected: IllegalStateException) { assertEquals("AUTH_STORAGE_WRITE_FAILED", expected.message) }
        assertEquals("old-password", storage.disk["mock_reg_password"])
        storage.canWrite = true
        assertTrue(MockAccountStore(storage).verify("legacy", "old-password"))
    }

    @Test fun failedRegistrationDoesNotReportSuccess() {
        val storage = Storage().apply { canWrite = false }
        try { MockAccountStore(storage).register("demo", "test-password"); fail("must reject failed commit") }
        catch (expected: IllegalStateException) { assertEquals("AUTH_STORAGE_WRITE_FAILED", expected.message) }
        assertTrue(storage.disk.isEmpty())
    }
}

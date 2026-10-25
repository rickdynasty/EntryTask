package com.entrytask.storage

import com.entry.lib.log.MLog
import com.entry.lib.net.mock.MockAccountStore
import com.entry.lib.stroage.room.CredentialStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** 只测跨介质提交顺序/恢复语义；真实Room事务另由设备测试覆盖。 */
class RoomMockAccountStorageTest {
    private class Disk : CredentialStore {
        val rows = linkedMapOf<String, String>()
        var fail = false

        override fun read(accountKey: String) = rows[accountKey]

        override fun insertIfAbsent(records: Map<String, String>): Set<String> {
            check(!fail) { "simulated disk failure" }
            val inserted = records.keys.filter { it !in rows }.toSet()
            inserted.forEach { rows[it] = records.getValue(it) }
            return inserted
        }
    }

    private class Legacy : LegacyAccountSource {
        val rows = linkedMapOf<String, String>()
        var canRemove = true

        override fun verifiers() = rows.filterKeys { it.startsWith("mock_auth_v1.") }

        override fun read(key: String) = rows[key]

        override fun remove(keys: Set<String>): Boolean {
            if (!canRemove) return false
            keys.forEach(rows::remove)
            return true
        }
    }

    private var logging = true

    @Before
    fun disableAndroidLogger() {
        logging = MLog.isEnabled
        MLog.isEnabled = false
    }

    @After
    fun restoreLogger() {
        MLog.isEnabled = logging
    }

    @Test
    fun importsAllHashedAccountsWithoutOverwritingRoomOrUnrelatedPreferences() {
        val disk = Disk().apply { rows["mock_auth_v1.existing"] = "room-verifier" }
        val legacy =
            Legacy().apply {
                rows.putAll(
                    mapOf(
                        "mock_auth_v1.existing" to "old-verifier",
                        "mock_auth_v1.second" to "second-verifier",
                        "theme" to "dark",
                    )
                )
            }
        val storage = RoomMockAccountStorage(disk, legacy)
        assertEquals("room-verifier", storage.read("mock_auth_v1.existing"))
        assertEquals("second-verifier", storage.read("mock_auth_v1.second"))
        assertEquals(mapOf("theme" to "dark"), legacy.rows)
    }

    @Test
    fun failedDatabaseCommitKeepsSourceAndSameAdapterCanRetry() {
        val disk = Disk().apply { fail = true }
        val legacy = Legacy().apply { rows["mock_auth_v1.first"] = "verifier" }
        val storage = RoomMockAccountStorage(disk, legacy)
        try {
            storage.read("mock_auth_v1.first")
            fail("must fail")
        } catch (_: IllegalStateException) {}
        assertEquals("verifier", legacy.rows["mock_auth_v1.first"])
        assertTrue(disk.rows.isEmpty())
        disk.fail = false
        assertEquals("verifier", storage.read("mock_auth_v1.first"))
        assertTrue(legacy.rows.isEmpty())
    }

    @Test
    fun crashWindowAfterDatabaseCommitIsIdempotentAndCannotReplaceNewerRoomRecord() {
        val disk = Disk()
        val legacy =
            Legacy().apply {
                rows["mock_auth_v1.first"] = "original"
                canRemove = false
            }
        try {
            RoomMockAccountStorage(disk, legacy).read("mock_auth_v1.first")
            fail("must fail cleanup")
        } catch (e: IllegalStateException) {
            assertEquals("AUTH_MIGRATION_CLEANUP_FAILED", e.message)
        }
        assertEquals("original", disk.rows["mock_auth_v1.first"])
        assertFalse(legacy.rows.isEmpty())
        legacy.rows["mock_auth_v1.first"] = "stale"
        legacy.canRemove = true
        assertEquals("original", RoomMockAccountStorage(disk, legacy).read("mock_auth_v1.first"))
        assertTrue(legacy.rows.isEmpty())
    }

    @Test
    fun plaintextLegacyIsDerivedBeforeDatabaseWriteAndCleanupCanRetry() {
        val disk = Disk()
        val legacy =
            Legacy().apply {
                rows.putAll(
                    mapOf(
                        "mock_reg_username" to "legacy",
                        "mock_reg_password" to "legacy-only-test",
                        "theme" to "dark",
                    )
                )
                canRemove = false
            }
        val accounts = MockAccountStore(RoomMockAccountStorage(disk, legacy))
        try {
            accounts.verify("legacy", "legacy-only-test")
            fail("must reject failed cleanup")
        } catch (e: IllegalStateException) {
            assertEquals("AUTH_STORAGE_WRITE_FAILED", e.message)
        }
        assertEquals(1, disk.rows.size)
        assertTrue(disk.rows.values.single().startsWith("v1:"))
        assertFalse(disk.rows.values.any { it.contains("legacy-only-test") })
        legacy.canRemove = true
        assertTrue(accounts.verify("legacy", "legacy-only-test"))
        assertEquals(mapOf("theme" to "dark"), legacy.rows)
    }

    @Test
    fun duplicateCreateIsRejectedWithoutChangingOriginalVerifier() {
        val disk = Disk()
        val storage = RoomMockAccountStorage(disk, Legacy())
        assertTrue(storage.create("mock_auth_v1.first", "original"))
        assertFalse(storage.create("mock_auth_v1.first", "replacement"))
        assertEquals("original", storage.read("mock_auth_v1.first"))
    }
}

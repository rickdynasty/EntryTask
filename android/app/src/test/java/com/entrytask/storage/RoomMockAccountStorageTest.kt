package com.entrytask.storage

import com.entry.lib.stroage.room.CredentialStore
import org.junit.Assert.*
import org.junit.Test

/** 隔离内存替身覆盖唯一写入、失败传播及key边界；真实Room由设备回归覆盖。 */
class RoomMockAccountStorageTest {
    private class Disk : CredentialStore {
        val rows = linkedMapOf<String, String>()
        var fail = false
        override fun read(accountKey: String) = rows[accountKey]
        override fun insertIfAbsent(records: Map<String, String>): Set<String> {
            check(!fail) { "AUTH_STORAGE_WRITE_FAILED" }
            val inserted = records.keys.filter { it !in rows }.toSet()
            inserted.forEach { rows[it] = records.getValue(it) }
            return inserted
        }
    }
    private val key = "mock_auth_v1." + "a".repeat(64)

    @Test fun duplicateCreateCannotReplaceExistingVerifier() {
        val disk = Disk()
        val storage = RoomMockAccountStorage(disk)
        assertTrue(storage.create(key, "original"))
        assertFalse(storage.create(key, "replacement"))
        assertEquals("original", storage.read(key))
    }

    @Test fun failedWriteIsNotReportedAsSuccessfulAndCanRetry() {
        val disk = Disk().apply { fail = true }
        val storage = RoomMockAccountStorage(disk)
        try { storage.create(key, "verifier"); fail("must propagate write failure") }
        catch (e: IllegalStateException) { assertEquals("AUTH_STORAGE_WRITE_FAILED", e.message) }
        assertTrue(disk.rows.isEmpty())
        disk.fail = false
        assertTrue(storage.create(key, "verifier"))
        assertEquals("verifier", storage.read(key))
    }

    @Test fun rejectsLegacyAndMalformedKeysBeforeReadingOrWriting() {
        val disk = Disk()
        val storage = RoomMockAccountStorage(disk)
        for (invalid in listOf("mock_reg_username", "theme", "mock_auth_v1.short", "mock_auth_v1." + "g".repeat(64))) {
            try { storage.read(invalid); fail("must reject key") } catch (_: IllegalArgumentException) {}
            try { storage.create(invalid, "verifier"); fail("must reject key") } catch (_: IllegalArgumentException) {}
        }
        assertTrue(disk.rows.isEmpty())
    }
}

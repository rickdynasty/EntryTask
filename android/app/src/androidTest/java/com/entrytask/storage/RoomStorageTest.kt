package com.entrytask.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.entry.lib.net.mock.MockAccountStorage
import com.entry.lib.net.mock.MockAccountStore
import com.entry.lib.stroage.room.RoomStorage
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** 每项测试使用独占文件，不触碰正式账号数据库或app_default_pref。 */
class RoomStorageTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun isolated(test: (String, RoomStorage) -> Unit) {
        val name = "room_test_${UUID.randomUUID().toString().replace("-", "")}.db"
        try {
            RoomStorage.open(context, name).use { test(name, it) }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun batchIsDurableAndUniqueKeyDoesNotOverwrite() = isolated { name, database ->
        assertEquals(
            setOf("one", "two"),
            database.credentials.insertIfAbsent(mapOf("one" to "first", "two" to "second")),
        )
        assertTrue(database.credentials.insertIfAbsent(mapOf("one" to "replacement")).isEmpty())
        // 独立连接重新打开，查询数据库中的已提交事实。
        RoomStorage.open(context, name).use { reopened ->
            assertEquals("first", reopened.credentials.read("one"))
            assertEquals("second", reopened.credentials.read("two"))
        }
    }

    @Test
    fun concurrentConnectionsOnlyCreateOneCredential() = isolated { name, first ->
        RoomStorage.open(context, name).use { second ->
            // 先完成建库，再验证实际并发写入的主键保护。
            first.credentials.insertIfAbsent(emptyMap())
            second.credentials.read("same")
            val pool = Executors.newFixedThreadPool(2)
            try {
                val latch = CountDownLatch(1)
                val jobs =
                    listOf(first, second).mapIndexed { index, store ->
                        pool.submit(
                            Callable {
                                latch.await(5, TimeUnit.SECONDS)
                                store.credentials.insertIfAbsent(mapOf("same" to "value-$index"))
                            }
                        )
                    }
                latch.countDown()
                assertEquals(1, jobs.sumOf { it.get(10, TimeUnit.SECONDS).size })
                assertTrue(first.credentials.read("same") in setOf("value-0", "value-1"))
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun failingBatchRollsBackEarlierRows() = isolated { name, database ->
        database.credentials.insertIfAbsent(mapOf("original" to "keep"))
        SQLiteDatabase.openDatabase(
                context.getDatabasePath(name).path,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
            .use { db ->
                db.execSQL(
                    "CREATE TRIGGER reject_test BEFORE INSERT ON auth_credentials WHEN NEW.account_key = 'reject' BEGIN SELECT RAISE(ABORT, 'test rollback'); END"
                )
            }
        try {
            database.credentials.insertIfAbsent(
                linkedMapOf("earlier" to "must rollback", "reject" to "fail")
            )
            fail("batch must fail")
        } catch (_: android.database.sqlite.SQLiteException) {}
        assertNull(database.credentials.read("earlier"))
        assertEquals("keep", database.credentials.read("original"))
    }

    @Test
    fun mainThreadDatabaseAccessIsRejected() = isolated { _, database ->
        database.credentials.insertIfAbsent(mapOf("one" to "first"))
        var rejected = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                database.credentials.read("one")
            } catch (_: IllegalStateException) {
                rejected = true
            }
        }
        assertTrue(rejected)
    }

    @Test
    fun actualPreferencesAndBothCredentialGenerationsMigrateToRoom() = isolated { _, database ->
        val prefName = "room_legacy_test_${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        try {
            // 复用既有KDF生成旧版SP校验记录，不在测试中复制算法实现。
            val old = linkedMapOf<String, String>()
            val source =
                object : MockAccountStorage {
                    override fun read(key: String) = old[key]

                    override fun commit(
                        values: Map<String, String>,
                        removals: Set<String>,
                    ): Boolean {
                        old.putAll(values)
                        removals.forEach(old::remove)
                        return true
                    }
                }
            MockAccountStore(source).register("existing-test", "existing-password")
            val edit =
                preferences
                    .edit()
                    .putString("theme", "dark")
                    .putString("mock_reg_username", "legacy-test")
                    .putString("mock_reg_password", "legacy-password")
            old.forEach { (key, value) -> edit.putString(key, value) }
            assertTrue(edit.commit())
            val accounts =
                MockAccountStore(
                    RoomMockAccountStorage(
                        database.credentials,
                        SharedPreferencesLegacyAccounts(preferences),
                    )
                )
            assertTrue(accounts.verify("existing-test", "existing-password"))
            assertTrue(accounts.verify("legacy-test", "legacy-password"))
            assertFalse(accounts.verify("legacy-test", "wrong"))
            assertEquals(mapOf("theme" to "dark"), preferences.all)
            assertEquals(
                MockAccountStore.Registration.EXISTS,
                accounts.register("legacy-test", "replacement"),
            )
            assertTrue(accounts.verify("legacy-test", "legacy-password"))
        } finally {
            context.deleteSharedPreferences(prefName)
        }
    }
}

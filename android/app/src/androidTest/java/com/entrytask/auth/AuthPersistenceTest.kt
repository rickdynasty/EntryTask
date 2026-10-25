package com.entrytask.auth

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.entry.biz.user.manager.UserAuthManager
import com.entry.biz.user.network.UserService
import com.entry.biz.user.repository.UserRepository
import com.entry.lib.net.MockInterceptor
import com.entry.lib.net.mock.MockAccountStore
import com.entry.lib.stroage.room.RoomStorage
import com.entrytask.storage.RoomMockAccountStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** 隔离数据库/用于断言不写SP的测试偏好；seed和verify由两个instrumentation进程运行，验证真实冷重启。 */
class AuthPersistenceTest {
    @Test
    fun registrationAndLoginUseDurableInterceptedCredentials() =
        runBlocking(Dispatchers.IO) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val phase = InstrumentationRegistry.getArguments().getString("authPhase") ?: "both"
            require(phase in setOf("seed", "verify", "both"))
            val databaseName = "entrytask_auth_persistence_test.db"
            val preferences =
                context.getSharedPreferences(
                    "entrytask_auth_persistence_test",
                    Context.MODE_PRIVATE,
                )
            val username = "restart-\"mock+&"
            val password = " pass+&%\"only-test "
            if (phase != "verify") {
                context.deleteDatabase(databaseName) // 仅删除测试独占文件。
                assertTrue(preferences.edit().clear().commit())
            }
            fun service(database: RoomStorage): UserService {
                val accounts =
                    MockAccountStore(
                        RoomMockAccountStorage(database.credentials)
                    )
                val client =
                    OkHttpClient.Builder()
                        .retryOnConnectionFailure(false)
                        .addInterceptor(MockInterceptor(context) { accounts })
                        .build()
                return Retrofit.Builder()
                    .baseUrl("https://entrytask.invalid/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(UserService::class.java)
            }
            try {
                if (phase != "verify") {
                    RoomStorage.open(context, databaseName).use { database ->
                        val registered =
                            UserRepository(service(database)).register(" $username ", password)
                        assertEquals(username, registered.username)
                        assertEquals(1L, registered.id)
                        assertTrue(UserAuthManager.isLogin())
                        assertTrue("注册不再写SP", preferences.all.isEmpty())
                    }
                    UserAuthManager.logout()
                }
                if (phase != "seed") {
                    assertFalse(UserAuthManager.isLogin())
                    assertTrue("seed必须跨进程保留Room文件", context.getDatabasePath(databaseName).exists())
                    SQLiteDatabase.openDatabase(
                            context.getDatabasePath(databaseName).path,
                            null,
                            SQLiteDatabase.OPEN_READONLY,
                        )
                        .use { db ->
                            db.rawQuery(
                                    "SELECT account_key, password_verifier FROM auth_credentials",
                                    null,
                                )
                                .use { cursor ->
                                    assertEquals(1, cursor.count)
                                    assertTrue(cursor.moveToFirst())
                                    assertNotEquals(username, cursor.getString(0))
                                    assertTrue(cursor.getString(1).startsWith("v1:"))
                                    assertFalse(cursor.getString(1).contains(password))
                                }
                        }
                    RoomStorage.open(context, databaseName).use { database ->
                        val api = service(database)
                        assertEquals(401, api.login(username, "wrong-only-test").code)
                        assertFalse(UserAuthManager.isLogin())
                        assertEquals(
                            username,
                            UserRepository(api).login(username, password).username,
                        )
                        assertTrue(UserAuthManager.isLogin())
                        assertEquals(409, api.register(username, "replacement-only-test").code)
                        assertEquals(
                            username,
                            UserRepository(service(database)).login(username, password).username,
                        )
                        assertTrue(preferences.all.isEmpty())
                    }
                }
            } finally {
                UserAuthManager.logout()
                if (phase != "seed") {
                    context.deleteDatabase(databaseName)
                    assertTrue(preferences.edit().clear().commit())
                }
            }
        }
}

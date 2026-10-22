package com.entrytask.auth

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.entry.biz.user.manager.UserAuthManager
import com.entry.biz.user.network.UserService
import com.entry.biz.user.repository.UserRepository
import com.entry.lib.net.MockInterceptor
import com.entry.lib.net.mock.MockAccountStore
import com.entry.lib.net.mock.SharedPreferencesAccountStorage
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** 专用SP隔离生产演示账号；支持seed/verify两次独立instrumentation进程验证冷重启。 */
class AuthPersistenceTest {
    @Test fun registrationAndLoginUseDurableInterceptedCredentials() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val phase = InstrumentationRegistry.getArguments().getString("authPhase") ?: "both"
        require(phase in setOf("seed", "verify", "both"))
        val preferences = context.getSharedPreferences("entrytask_auth_persistence_test", Context.MODE_PRIVATE)
        val username = "restart-\"mock+&"
        val password = " pass+&%\"only-test "
        fun service(): UserService {
            val accounts = MockAccountStore(SharedPreferencesAccountStorage(preferences))
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
                .addInterceptor(MockInterceptor(context, accounts)).build()
            return Retrofit.Builder().baseUrl("https://entrytask.invalid/")
                .client(client).addConverterFactory(GsonConverterFactory.create()).build().create(UserService::class.java)
        }
        try {
            if (phase != "verify") {
                // 仅清本测试独占文件，绝不清app_default_pref或现有演示账户。
                assertTrue(preferences.edit().clear().commit())
                val registered = UserRepository(service()).register(" $username ", password)
                assertEquals(username, registered.username)
                assertEquals(1L, registered.id)
                assertTrue(UserAuthManager.isLogin())
                assertEquals(1, preferences.all.size)
                assertFalse(preferences.all.toString().contains(password))
                UserAuthManager.logout()
            }
            if (phase != "seed") {
                assertFalse(UserAuthManager.isLogin())
                assertFalse("seed must persist across processes", preferences.all.isEmpty())
                val api = service()
                assertEquals(401, api.login(username, "wrong-only-test").code)
                assertFalse(UserAuthManager.isLogin())
                val fresh = UserRepository(api)
                val loggedIn = fresh.login(username, password)
                assertEquals(username, loggedIn.username)
                assertTrue(UserAuthManager.isLogin())
                assertEquals(409, api.register(username, "replacement-only-test").code)
                assertEquals(username, UserRepository(service()).login(username, password).username)
            }
        } finally {
            UserAuthManager.logout()
            if (phase != "seed") assertTrue(preferences.edit().clear().commit())
        }
    }
}

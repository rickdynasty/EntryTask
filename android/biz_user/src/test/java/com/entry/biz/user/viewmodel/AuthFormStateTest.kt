package com.entry.biz.user.viewmodel

import androidx.lifecycle.ViewModelStore
import com.entry.biz.user.manager.UserAuthManager
import com.entry.biz.user.model.ApiResponse
import com.entry.biz.user.model.User
import com.entry.biz.user.network.UserService
import com.entry.biz.user.repository.UserRepository
import com.entry.biz.user.ui.contract.AuthUiIntent
import com.entry.lib.log.MLog
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** 使用成功/失败/挂起的Fake服务，只操作测试内存会话，不读写注册账号。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthFormStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var logging = true
    @Before fun setup() { Dispatchers.setMain(dispatcher); logging = MLog.isEnabled; MLog.isEnabled = false }
    @After fun teardown() { store.clear(); UserAuthManager.logout(); MLog.isEnabled = logging; Dispatchers.resetMain() }
    private fun model(key: String, service: UserService) = UserViewModel(UserRepository(service)).also { store.put(key, it) }
    private fun fill(vm: UserViewModel, name: String, password: String) {
        vm.handleIntent(AuthUiIntent.UpdateUsername(name))
        vm.handleIntent(AuthUiIntent.UpdatePassword(password))
    }
    private class Service(private val request: suspend () -> ApiResponse<User>) : UserService {
        var calls = 0
        override suspend fun login(username: String, password: String): ApiResponse<User> { calls++; return request() }
        override suspend fun register(username: String, password: String): ApiResponse<User> { calls++; return request() }
    }
    @Test fun failedLoginAndRegisterHaveSeparateInputsAndLeaveClearsOnlyOwnPassword() = runTest(dispatcher) {
        val service = Service { ApiResponse(401, "演示认证失败", null) }
        val login = model("login", service)
        val register = model("register", service)
        fill(login, "login-demo", "login-test-only")
        login.handleIntent(AuthUiIntent.SubmitLogin)
        runCurrent()
        assertFalse(login.uiState.value.isLoading)
        assertEquals("", register.uiState.value.username)
        assertEquals("", register.uiState.value.password)
        login.onFormLeft()
        fill(register, "register-demo", "register-test-only")
        register.handleIntent(AuthUiIntent.SubmitRegister)
        runCurrent()
        register.onFormLeft()
        assertEquals("login-demo", login.uiState.value.username)
        assertEquals("", login.uiState.value.password)
        assertEquals("register-demo", register.uiState.value.username)
        assertEquals("", register.uiState.value.password)
    }
    @Test fun leavingCancelsRequestWithoutToastOrNavigation() = runTest(dispatcher) {
        var cancelled = false
        val vm = model("login", Service {
            try { awaitCancellation() } finally { cancelled = true }
        })
        fill(vm, "demo", "test-only")
        vm.handleIntent(AuthUiIntent.SubmitLogin)
        runCurrent()
        vm.onFormLeft()
        runCurrent()
        assertTrue(cancelled)
        assertNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.authenticated)
        assertEquals("", vm.uiState.value.password)
        assertFalse(vm.uiState.value.isLoading)
    }
    @Test fun duplicateSubmitAndOldCancellationCannotResetNewRequest() = runTest(dispatcher) {
        val service = Service { awaitCancellation() }
        val vm = model("login", service)
        fill(vm, "demo", "first-test-only")
        vm.handleIntent(AuthUiIntent.SubmitLogin)
        vm.handleIntent(AuthUiIntent.SubmitLogin)
        runCurrent()
        assertEquals(1, service.calls)
        vm.onFormLeft()
        fill(vm, "demo", "second-test-only")
        vm.handleIntent(AuthUiIntent.SubmitLogin)
        runCurrent()
        assertEquals(2, service.calls)
        assertTrue(vm.uiState.value.isLoading)
        vm.onFormLeft()
        runCurrent()
    }
    @Test fun viewModelStoreClearRemovesRetainedPassword() = runTest(dispatcher) {
        val vm = model("login", Service { awaitCancellation() })
        fill(vm, "demo", "test-only")
        store.clear()
        assertEquals("", vm.uiState.value.password)
    }
    @Test fun businessFailuresShowToastAndRestoreBothForms() = runTest(dispatcher) {
        for (registration in listOf(false, true)) {
            val vm = model("failure-$registration", Service { ApiResponse(401, "untrusted-server-detail", null) })
            fill(vm, "demo", "test-only")
            vm.handleIntent(if (registration) AuthUiIntent.SubmitRegister else AuthUiIntent.SubmitLogin)
            runCurrent()
            assertFalse(vm.uiState.value.isLoading)
            assertEquals(if (registration) "注册失败，请稍后重试"
                else "账号或密码错误，请检查后重试", vm.uiState.value.message)
        }
    }

    @Test fun networkAndUnexpectedFailuresUseSafeToastMessages() = runTest(dispatcher) {
        val cases = listOf(
            java.io.IOException("private-network-detail") to "网络连接失败，请检查网络后重试",
            java.net.SocketTimeoutException("private-timeout-detail") to "网络请求超时，请稍后重试",
            IllegalStateException("private-internal-detail") to "登录失败，请稍后重试",
        )
        for ((index, case) in cases.withIndex()) {
            val vm = model("network-$index", Service { throw case.first })
            fill(vm, "demo", "test-only")
            vm.handleIntent(AuthUiIntent.SubmitLogin)
            runCurrent()
            assertFalse(vm.uiState.value.isLoading)
            assertEquals(case.second, vm.uiState.value.message)
        }
    }

    @Test fun duplicateRegistrationUsesAccountExistsToast() = runTest(dispatcher) {
        val vm = model("duplicate", Service { ApiResponse(409, "untrusted-detail", null) })
        fill(vm, "demo", "test-only")
        vm.handleIntent(AuthUiIntent.SubmitRegister)
        runCurrent()
        assertEquals("该用户名已注册，请更换用户名或直接登录", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun exitingWhileLoginIsPendingRejectsLateSessionAndSuccessEffect() = runTest(dispatcher) {
        val response = CompletableDeferred<ApiResponse<User>>()
        val vm = model("pending-exit", Service { response.await() })
        fill(vm, "test-epoch-only", "test-only")
        vm.handleIntent(AuthUiIntent.SubmitLogin)
        runCurrent()
        UserAuthManager.logout() // 模拟双击退出，网络结果仍可能晚于该动作返回。
        response.complete(ApiResponse(200, "success", User(1, "test-epoch-only", null, null, "memory-test-only")))
        runCurrent()
        assertFalse(UserAuthManager.isLogin())
        assertNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.authenticated)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun loginAndRegistrationRetainSuccessWithoutAnActiveUiCollector() = runTest(dispatcher) {
        for (registration in listOf(false, true)) {
            val user = User(1, "auth-success-only", null, null, "memory-test-only")
            val vm = model("success-$registration", Service { ApiResponse(200, "success", user) })
            try {
                UserAuthManager.logout()
                fill(vm, user.username, "test-only")
                vm.handleIntent(if (registration) AuthUiIntent.SubmitRegister else AuthUiIntent.SubmitLogin)
                runCurrent()
                assertTrue(vm.uiState.value.authenticated)
                assertEquals(if (registration) "注册成功" else null, vm.uiState.value.message)
                // 提示消费不清掉成功事实；宿主重建或恢复后仍能观察成功。
                vm.handleIntent(AuthUiIntent.MessageShown)
                assertNull(vm.uiState.value.message)
                assertTrue(vm.uiState.value.authenticated)
                assertTrue(UserAuthManager.isLogin())
                assertEquals("", vm.uiState.value.password)
                assertFalse(vm.uiState.value.isLoading)
            } finally {
                UserAuthManager.logout()
            }
        }
    }

    @Test fun viewModelAcceptsDomainContractAndCannotSubmitAgainAfterSuccess() = runTest(dispatcher) {
        var calls = 0
        val repository = object : com.entry.biz.user.domain.AuthRepository {
            override suspend fun login(username: String, password: String): com.entry.biz.user.domain.AuthenticatedUser {
                calls++
                return com.entry.biz.user.domain.AuthenticatedUser(1, username)
            }
            override suspend fun register(username: String, password: String) = login(username, password)
        }
        val vm = UserViewModel(repository).also { store.put("domain", it) }
        fill(vm, "demo", "test-only")
        vm.handleIntent(AuthUiIntent.SubmitLogin)
        runCurrent()
        vm.handleIntent(AuthUiIntent.SubmitLogin)
        vm.handleIntent(AuthUiIntent.UpdatePassword("should-not-be-retained"))
        runCurrent()
        assertEquals(1, calls)
        assertTrue(vm.uiState.value.authenticated)
        assertEquals("", vm.uiState.value.password)
    }

    @Test fun malformedSuccessDoesNotEstablishSession() = runTest(dispatcher) {
        UserAuthManager.logout()
        val vm = model("invalid", Service { ApiResponse(200, "success", User(1, "demo", null, null, "")) })
        fill(vm, "demo", "test-only")
        vm.handleIntent(AuthUiIntent.SubmitLogin)
        runCurrent()
        assertFalse(UserAuthManager.isLogin())
        assertFalse(vm.uiState.value.authenticated)
        assertEquals("登录失败，请稍后重试", vm.uiState.value.message)
    }
}

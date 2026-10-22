package com.entry.biz.user.viewmodel

import androidx.lifecycle.ViewModelStore
import com.entry.biz.user.model.ApiResponse
import com.entry.biz.user.model.User
import com.entry.biz.user.network.UserService
import com.entry.biz.user.repository.UserRepository
import com.entry.biz.user.ui.contract.AuthUiEffect
import com.entry.biz.user.ui.contract.AuthUiIntent
import com.entry.lib.log.MLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** 使用失败/挂起的 Fake 服务，不建立会话、不读写注册账号。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthFormStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var logging = true
    @Before fun setup() { Dispatchers.setMain(dispatcher); logging = MLog.isEnabled; MLog.isEnabled = false }
    @After fun teardown() { store.clear(); MLog.isEnabled = logging; Dispatchers.resetMain() }
    private fun model(key: String, service: UserService) = UserViewModel(UserRepository(service)).also { store.put(key, it) }
    private fun fill(vm: UserViewModel, name: String, password: String) {
        vm.handleIntent(AuthUiIntent.UpdateUsername(name))
        vm.handleIntent(AuthUiIntent.UpdatePassword(password))
    }
    private class Service(private val request: suspend () -> ApiResponse<User>) : UserService {
        var calls = 0
        override suspend fun login(username: String, pass: String): ApiResponse<User> { calls++; return request() }
        override suspend fun register(username: String, pass: String): ApiResponse<User> { calls++; return request() }
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
        val effects = mutableListOf<AuthUiEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiEffect.toList(effects) }
        fill(vm, "demo", "test-only")
        vm.handleIntent(AuthUiIntent.SubmitLogin)
        runCurrent()
        vm.onFormLeft()
        runCurrent()
        assertTrue(cancelled)
        assertTrue(effects.isEmpty())
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
            val effects = mutableListOf<AuthUiEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiEffect.toList(effects) }
            fill(vm, "demo", "test-only")
            vm.handleIntent(if (registration) AuthUiIntent.SubmitRegister else AuthUiIntent.SubmitLogin)
            runCurrent()
            assertFalse(vm.uiState.value.isLoading)
            assertEquals(listOf(AuthUiEffect.ShowToast(if (registration) "注册失败，请稍后重试"
                else "账号或密码错误，请检查后重试")), effects)
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
            val effects = mutableListOf<AuthUiEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiEffect.toList(effects) }
            fill(vm, "demo", "test-only")
            vm.handleIntent(AuthUiIntent.SubmitLogin)
            runCurrent()
            assertFalse(vm.uiState.value.isLoading)
            assertEquals(listOf(AuthUiEffect.ShowToast(case.second)), effects)
        }
    }

}

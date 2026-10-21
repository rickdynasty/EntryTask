package com.entrytask.shell

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.entrytask.shell.data.DefaultShellRepository
import com.entrytask.shell.presentation.AuthLaunchState
import com.entrytask.shell.presentation.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }
    private fun model(repository: DefaultShellRepository) = MainViewModel(repository, SavedStateHandle()) { 100L }
        .also { models.put("main", it) }

    @Test fun authenticationIsSingleFlightAndFailedLaunchCanRetry() = runTest(dispatcher) {
        val vm = model(DefaultShellRepository(FakeSession()))
        vm.requestAuthentication()
        val first = vm.authLaunch.value as AuthLaunchState.Requested
        vm.requestAuthentication()
        assertEquals(first, vm.authLaunch.value)
        assertTrue(vm.authenticationStarted(first.id))
        assertFalse(vm.authenticationStarted(first.id))
        vm.requestAuthentication()
        assertEquals(AuthLaunchState.InFlight(first.id), vm.authLaunch.value)
        vm.authenticationFailed(first.id)
        vm.requestAuthentication()
        val retry = vm.authLaunch.value as AuthLaunchState.Requested
        assertNotEquals(first.id, retry.id)
        vm.authenticationFailed(first.id)
        assertEquals(retry, vm.authLaunch.value)
    }
    @Test fun activityResultDoesNotInventLoginAndSuccessReadsNativeSession() = runTest(dispatcher) {
        val session = FakeSession()
        val repository = DefaultShellRepository(session)
        val vm = model(repository)
        vm.requestAuthentication()
        vm.authenticationReturned()
        runCurrent()
        assertFalse(vm.uiState.value.shell.authenticated)
        assertFalse(vm.uiState.value.bottomBarVisible)
        session.login()
        vm.authenticationReturned()
        runCurrent()
        assertTrue(vm.uiState.value.bottomBarVisible)
        repository.updateDepth(1)
        runCurrent()
        assertFalse(vm.uiState.value.bottomBarVisible)
        repository.logout()
        runCurrent()
        assertFalse(vm.uiState.value.bottomBarVisible)
        assertEquals(AuthLaunchState.Idle, vm.authLaunch.value)
    }
}

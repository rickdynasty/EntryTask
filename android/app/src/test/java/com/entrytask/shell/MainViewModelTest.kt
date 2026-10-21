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
    @Test fun startupRequestsNativeAuthenticationBeforeContentAndCancelDoesNotLoop() = runTest(dispatcher) {
        val vm = model(DefaultShellRepository(FakeSession()))
        vm.onHostCreated()
        assertTrue(vm.authLaunch.value is AuthLaunchState.Requested)
        assertFalse(vm.canAttachContent())
        val id = (vm.authLaunch.value as AuthLaunchState.Requested).id
        vm.authenticationStarted(id)
        vm.onHostResumed()
        assertEquals(AuthLaunchState.InFlight(id), vm.authLaunch.value)
        vm.authenticationReturned(id)
        repeat(3) { vm.onHostResumed(); vm.onHostCreated() }
        assertEquals(AuthLaunchState.Canceled(id), vm.authLaunch.value)
        assertTrue(vm.canAttachContent())
        vm.requestAuthentication()
        assertTrue(vm.authLaunch.value is AuthLaunchState.Requested)
    }

    @Test fun recreatedPendingAuthenticationIsNotLaunchedAgainAndCanceledStateSurvives() = runTest(dispatcher) {
        val repository = DefaultShellRepository(FakeSession())
        val state = SavedStateHandle()
        val first = MainViewModel(repository, state) { 100L }.also { models.put("first", it) }
        first.onHostCreated()
        val request = first.authLaunch.value as AuthLaunchState.Requested
        first.authenticationStarted(request.id)
        val restored = MainViewModel(repository, state) { 100L }.also { models.put("restored", it) }
        restored.onHostCreated()
        assertEquals(AuthLaunchState.InFlight(request.id), restored.authLaunch.value)
        assertFalse(restored.authenticationStarted(request.id))
        restored.authenticationReturned(request.id)
        val canceled = MainViewModel(repository, state) { 100L }.also { models.put("canceled", it) }
        canceled.onHostCreated()
        canceled.onHostResumed()
        assertEquals(AuthLaunchState.Canceled(request.id), canceled.authLaunch.value)
    }

    @Test fun singleTaskReentryStartsOneNewAttemptAndIgnoresOldResult() = runTest(dispatcher) {
        val vm = model(DefaultShellRepository(FakeSession()))
        vm.onHostCreated()
        val old = vm.authLaunch.value as AuthLaunchState.Requested
        vm.authenticationStarted(old.id)
        vm.onNewEntry()
        val next = vm.authLaunch.value as AuthLaunchState.Requested
        assertTrue(next.id > old.id)
        vm.authenticationReturned(old.id)
        assertEquals(next, vm.authLaunch.value)
        vm.authenticationStarted(next.id)
        vm.authenticationReturned(next.id)
        assertEquals(AuthLaunchState.Canceled(next.id), vm.authLaunch.value)
    }

    @Test fun authenticatedStartupAttachesContentWithoutLaunchingLogin() = runTest(dispatcher) {
        val session = FakeSession().apply { login() }
        val vm = model(DefaultShellRepository(session))
        vm.onHostCreated()
        vm.onHostResumed()
        assertEquals(AuthLaunchState.Idle, vm.authLaunch.value)
        assertTrue(vm.canAttachContent())
    }

    @Test fun systemCanceledResultWithoutDataCannotCancelSingleTaskReentryAttempt() = runTest(dispatcher) {
        val vm = model(DefaultShellRepository(FakeSession()))
        vm.onHostCreated()
        val first = vm.authLaunch.value as AuthLaunchState.Requested
        vm.authenticationStarted(first.id)
        vm.onNewEntry()
        val next = vm.authLaunch.value as AuthLaunchState.Requested
        assertFalse(vm.authenticationReturned(null))
        assertEquals(next, vm.authLaunch.value)
        vm.authenticationStarted(next.id)
        assertTrue(vm.authenticationReturned(next.id))
        assertEquals(AuthLaunchState.Canceled(next.id), vm.authLaunch.value)
    }

}

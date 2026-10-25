package com.entry.biz.dashboard.presentation

import com.entry.biz.dashboard.domain.*
import com.entry.lib.log.MLog
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** 聚焦门禁、取消、错误恢复；不使用真实账号或磁盘。 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher); MLog.isEnabled = false }
    @After fun cleanup() { Dispatchers.resetMain(); MLog.isEnabled = true }
    @Test fun unauthenticatedPageDoesNotRequestAndDropsPreviousContent() = runTest(dispatcher) {
        var calls = 0
        val vm = DashboardViewModel(object : DashboardRepository {
            override suspend fun load(tab: Int, category: String?): DashboardPage { calls++; return DashboardPage(tab) }
        })
        vm.show(1, false, 1); runCurrent()
        assertEquals(0, calls)
        vm.show(1, true, 2); runCurrent()
        assertNotNull(vm.uiState.value.page)
        vm.show(1, false, 3); runCurrent()
        assertNull(vm.uiState.value.page)
        assertFalse(vm.uiState.value.loading)
        assertEquals(1, calls)
    }
    @Test fun oldTabResponseCannotOverwriteNewTabEvenIfRepositoryIgnoresCancellation() = runTest(dispatcher) {
        val vm = DashboardViewModel(object : DashboardRepository {
            override suspend fun load(tab: Int, category: String?): DashboardPage {
                withContext(NonCancellable) { delay(if (tab == 1) 500 else 10) }
                return DashboardPage(tab)
            }
        })
        vm.show(1, true, 1); runCurrent()
        vm.show(2, true, 1); advanceUntilIdle()
        assertEquals(2, vm.uiState.value.page?.tab)
        assertNull(vm.uiState.value.error)
    }
    @Test fun failedRefreshKeepsContentAndRetryRecovers() = runTest(dispatcher) {
        var fail = false
        val vm = DashboardViewModel(object : DashboardRepository {
            override suspend fun load(tab: Int, category: String?): DashboardPage {
                if (fail) error("test-failure")
                return DashboardPage(tab)
            }
        })
        vm.show(4, true, 1); runCurrent()
        fail = true; vm.reload(); runCurrent()
        assertNotNull(vm.uiState.value.page)
        assertNotNull(vm.uiState.value.error)
        fail = false; vm.reload(); runCurrent()
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.loading)
        vm.toggleAssets(); assertTrue(vm.uiState.value.revealAssets)
        vm.show(4, true, 2); runCurrent()
        assertFalse(vm.uiState.value.revealAssets)
    }
}

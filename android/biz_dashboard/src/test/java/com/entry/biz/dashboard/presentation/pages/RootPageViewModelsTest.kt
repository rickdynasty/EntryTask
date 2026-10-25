package com.entry.biz.dashboard.presentation.pages

import com.entry.biz.dashboard.domain.pages.*
import com.entry.lib.log.MLog
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootPageViewModelsTest {
    private var logging = true

    @Before
    fun disableAndroidSinkInJvmTest() {
        logging = MLog.isEnabled
        MLog.isEnabled = false
    }

    @After
    fun restoreAndroidSink() {
        MLog.isEnabled = logging
    }

    @Test
    fun regionsFailIndependentlyAndNewSessionReloadsActiveTab() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm =
                HomePageViewModel(
                    object : HomeRepository {
                        override suspend fun home(): HomeContent {
                            calls++
                            return HomeContent("搜索", emptyList(), emptyList())
                        }

                        override suspend fun unread(): Int = throw IllegalStateException("offline")

                        override suspend fun products(category: String) =
                            ProductPage(emptyList(), 0)
                    }
                )
            vm.show(true, 1)
            advanceUntilIdle()
            assertNotNull(vm.content.state.value.data)
            assertNotNull(vm.unread.state.value.error)
            vm.show(true, 2)
            advanceUntilIdle()
            assertEquals(2, calls)
            vm.show(false, 2)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun categoryRaceCannotOverwriteNewestSelectionAndSurvivesTabSwitch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm =
                WealthPageViewModel(
                    object : WealthRepository {
                        override suspend fun wealth() =
                            WealthContent(emptyList(), emptyList(), emptyList())

                        override suspend fun assets() =
                            AssetOverview(MoneyValue("0.00"), MoneyValue("0.00"))

                        override suspend fun products(category: String): ProductPage {
                            delay(if (category == "fund") 300 else 10)
                            return ProductPage(listOf(ProductBrief(category, category, "", "")), 1)
                        }
                    }
                )
            vm.show(true, 1)
            runCurrent()
            vm.select("hot")
            advanceUntilIdle()
            assertEquals("hot", vm.products.state.value.data?.items?.single()?.id)
            vm.show(false, 1)
            vm.show(true, 1)
            advanceUntilIdle()
            assertEquals("hot", vm.category.value)
            vm.show(false, 1)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun homeCategoryCancelsStaleReplyAndResetsOnAccountChange() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm =
                HomePageViewModel(
                    object : HomeRepository {
                        override suspend fun home() = HomeContent("搜索", emptyList(), emptyList())

                        override suspend fun unread() = 0

                        override suspend fun products(category: String): ProductPage {
                            withContext(NonCancellable) {
                                delay(if (category == "all") 300 else 10)
                            }
                            return ProductPage(listOf(ProductBrief(category, category, "", "")), 1)
                        }
                    }
                )
            vm.show(true, 1)
            runCurrent()
            vm.select("finance")
            advanceUntilIdle()
            assertEquals("finance", vm.products.state.value.data?.items?.single()?.id)
            vm.show(false, 1)
            vm.show(true, 1)
            advanceUntilIdle()
            assertEquals("finance", vm.category.value)
            vm.show(true, 2)
            advanceUntilIdle()
            assertEquals("all", vm.category.value)
            assertEquals("all", vm.products.state.value.data?.items?.single()?.id)
            vm.show(false, 2)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cancellationAndEpochResetDiscardOldAccountResult() = runTest {
        val resource = PageResource<String>(backgroundScope)
        resource.load {
            withContext(NonCancellable) {
                delay(100)
                "old-user"
            }
        }
        runCurrent()
        resource.clear()
        advanceTimeBy(200)
        runCurrent()
        assertNull(resource.state.value.data)
        assertNull(resource.state.value.error)
    }

    @Test
    fun ambiguousCheckinIsConfirmedWithoutAutomaticReplay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var reads = 0
            var writes = 0
            val status = CheckinStatus("2026-10-25", false, 0, 0, 0, false, emptyList(), "demo")
            val vm =
                MemberPageViewModel(
                    object : MemberRepository {
                        override suspend fun member() = MemberContent(emptyList())

                        override suspend fun identity(member: Boolean): Identity =
                            throw IllegalStateException("offline")

                        override suspend fun checkin(): CheckinStatus {
                            reads++
                            if (reads == 2) throw IllegalStateException("offline")
                            return status.copy(checked = reads >= 3)
                        }

                        override suspend fun submit(date: String, key: String): CheckinStatus {
                            writes++
                            delay(1)
                            throw IllegalStateException("timeout")
                        }
                    }
                )
            vm.show(true, 1)
            advanceUntilIdle()
            vm.submit()
            vm.submit()
            advanceUntilIdle()
            assertEquals(1, writes)
            assertTrue(vm.submission.value.message.contains("尚未确认"))
            vm.submit()
            advanceUntilIdle()
            assertEquals(1, writes)
            assertTrue(vm.checkin.state.value.data!!.checked)
            vm.show(false, 1)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

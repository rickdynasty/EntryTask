package com.entrytask.shell

import com.entrytask.shell.runtime.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** 预热不能抢在首帧、重复启动引擎或在失败/低内存时破坏 Native 流程。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RnWarmupControllerTest {
    @Test fun engineStartsOnceOnlyAfterFrameAndCompletionIsObservedWithoutBlocking() = runTest {
        var calls = 0
        var outcome: WarmupPhase? = null
        val controller = RnWarmupController(true, this, {
            calls++; WarmupHandle { outcome }
        }, { testScheduler.currentTime }, {})
        assertEquals(0, calls)
        controller.afterAuthFrame(false)
        runCurrent()
        assertEquals(1, calls)
        assertEquals(WarmupPhase.RUNNING, controller.snapshot.phase)
        controller.afterAuthFrame(false)
        outcome = WarmupPhase.READY
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(WarmupPhase.READY, controller.snapshot.phase)
    }
    @Test fun demandSurfaceAndDisabledModeNeverStartSpeculativeEngine() = runTest {
        val demand = RnWarmupController(true, this, { error("not allowed") }, { testScheduler.currentTime }, {})
        demand.surfaceWillAttach()
        demand.afterAuthFrame(false)
        val disabled = RnWarmupController(false, this, { error("not allowed") }, { testScheduler.currentTime }, {})
        disabled.afterAuthFrame(false)
        advanceUntilIdle()
        assertFalse(demand.handlesPreloadException())
        assertFalse(disabled.handlesPreloadException())
    }
    @Test fun memoryPressureSkipsWarmup() = runTest {
        val controller = RnWarmupController(true, this, { error("not allowed") }, { testScheduler.currentTime }, {})
        controller.afterAuthFrame(true)
        assertEquals(WarmupPhase.SKIPPED_MEMORY, controller.snapshot.phase)
    }
    @Test fun failureIsIsolatedOnlyBeforeSurfaceAndDoesNotAutoRetry() = runTest {
        var calls = 0
        val controller = RnWarmupController(true, this, {
            calls++; throw IllegalStateException("fake")
        }, { testScheduler.currentTime }, {})
        controller.afterAuthFrame(false)
        advanceUntilIdle()
        assertEquals(WarmupPhase.FAILED, controller.snapshot.phase)
        assertTrue(controller.handlesPreloadException())
        controller.afterAuthFrame(false)
        assertEquals(1, calls)
        controller.surfaceWillAttach()
        assertFalse(controller.handlesPreloadException())
    }
    @Test fun observationTimeoutDoesNotDestroyOrRetrySharedRuntime() = runTest {
        var calls = 0
        val controller = RnWarmupController(true, this, { calls++; WarmupHandle { null } }, { testScheduler.currentTime }, {})
        controller.afterAuthFrame(false)
        controller.surfaceWillAttach()
        advanceUntilIdle()
        assertEquals(WarmupPhase.TIMED_OUT, controller.snapshot.phase)
        assertEquals(30_000L, controller.snapshot.endedMs)
        controller.afterAuthFrame(false)
        assertEquals(1, calls)
    }
}

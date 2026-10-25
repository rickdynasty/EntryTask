package com.entrytask.shell

import androidx.fragment.app.Fragment
import com.entrytask.shell.host.RootContentFragmentFactory
import com.entrytask.shell.host.RootContentHostFragment
import com.swmansion.rnscreens.fragment.restoration.RNScreensFragmentFactory
import org.junit.Assert.*
import org.junit.Test

/** 重命名不能使旧恢复状态找不到类，也不能绕过RN Screens的恢复策略。 */
class RootContentFragmentFactoryTest {
    private val loader
        get() = RootContentHostFragment::class.java.classLoader!!

    @Test
    fun oldAndCurrentClassNamesBothInstantiateTheNewRootHost() {
        val factory = RootContentFragmentFactory()
        assertEquals(
            RootContentHostFragment::class.java,
            factory.instantiate(loader, "com.entrytask.home.HomeFragment").javaClass,
        )
        assertEquals(
            RootContentHostFragment::class.java,
            factory.instantiate(loader, RootContentHostFragment::class.java.name).javaClass,
        )
    }

    @Test
    fun screensAndOrdinaryFragmentsStillUseTheirOriginalFactoryBehavior() {
        val actual = RootContentFragmentFactory()
        val original = RNScreensFragmentFactory()
        for (name in listOf("com.swmansion.rnscreens.ScreenFragment", Fragment::class.java.name)) {
            assertEquals(
                original.instantiate(loader, name).javaClass,
                actual.instantiate(loader, name).javaClass,
            )
        }
    }
}

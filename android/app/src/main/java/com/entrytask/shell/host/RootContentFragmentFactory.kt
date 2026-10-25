package com.entrytask.shell.host

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import com.swmansion.rnscreens.fragment.restoration.RNScreensFragmentFactory

/** 在super.onCreate前安装：兼容旧宿主类名，同时保留RN Screens自己的恢复策略。 */
class RootContentFragmentFactory : FragmentFactory() {
    private val screens = RNScreensFragmentFactory()

    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        // FragmentManager保存的是类全名；只映射类名，不丢弃状态、tag或子Fragment。
        val restoredName =
            if (className == LEGACY_ROOT_CLASS) {
                RootContentHostFragment::class.java.name
            } else className
        return screens.instantiate(classLoader, restoredName)
    }

    companion object {
        // 仅作为已安装版本的恢复标识保留，不再保留旧Fragment实现或别名类。
        private const val LEGACY_ROOT_CLASS = "com.entrytask.home.HomeFragment"
    }
}

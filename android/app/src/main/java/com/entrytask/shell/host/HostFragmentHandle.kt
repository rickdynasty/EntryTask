package com.entrytask.shell.host

import com.entrytask.home.HomeFragment
import com.facebook.react.ReactFragment

/** Activity/View 作用域的当前宿主句柄，不进入 VM 或进程单例。 */
class HostFragmentHandle {
    private var fragment: HomeFragment? = null
    fun bind(value: HomeFragment) { fragment = value }
    fun clear() { fragment = null }
    fun dispatchBack(): Boolean {
        val host = fragment?.takeIf { it.isAdded && it.isResumed } ?: return false
        val child = host.childFragmentManager.findFragmentByTag("entrytask-rn-root") as? ReactFragment
        return child?.takeIf { it.isResumed }?.onBackPressed() == true
    }
}


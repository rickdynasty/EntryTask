package com.entrytask.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.entrytask.R
import com.entrytask.base.BaseFragment

/** Set USE_REACT_NATIVE=false for the existing Native-only Home content. */
class HomeFragment : BaseFragment(R.layout.fragment_main) {
    companion object {
        private const val USE_REACT_NATIVE = true
        fun usesReactNative(): Boolean = USE_REACT_NATIVE
    }
    override fun getComponentName(): String? = if (USE_REACT_NATIVE) "EntryTask" else null
    override fun initView(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tvTitle).text = "Home页面（Native）"
    }
}

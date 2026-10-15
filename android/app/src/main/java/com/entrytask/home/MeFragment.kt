package com.entrytask.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.entrytask.R
import com.entrytask.base.BaseFragment

class MeFragment : BaseFragment(R.layout.fragment_me) {
    override fun initView(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tvTitle).text = "Me页面"
    }
}
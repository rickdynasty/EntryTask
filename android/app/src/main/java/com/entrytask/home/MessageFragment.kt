package com.entrytask.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.entrytask.R
import com.entrytask.base.BaseFragment

class MessageFragment : BaseFragment(R.layout.fragment_message) {
    override fun initView(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tvTitle).text = "信息页面"
    }
}
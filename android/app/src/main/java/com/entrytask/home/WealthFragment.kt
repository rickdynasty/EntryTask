package com.entrytask.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.entrytask.R
import com.entrytask.base.BaseFragment

class WealthFragment : BaseFragment(R.layout.fragment_trade) {
    override fun initView(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tvTitle).text = "欢迎来到 财富页面"
    }
}
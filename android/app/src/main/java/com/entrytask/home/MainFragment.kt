package com.entrytask.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.entrytask.R
import com.entrytask.base.BaseFragment

class MainFragment : BaseFragment(R.layout.fragment_main) {
    override fun initView(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tvTitle).text = "Home页面"
    }
//class MainFragment : BaseFragment() {
//    /**
//     * 指定当前 Fragment 要加载的 React Native 组件名称
//     * 该名称必须与你在 index.js / AppRegistry.registerComponent 中注册的一致
//     */
//    override fun getComponentName(): String {
//        return "EntryTask"
//    }
//
//    /**
//     * （可选）如果需要向 React Native 页面传递初始化参数，可以在这里配置 Bundle
//     */
//    override fun getBundle(): Bundle? {
//        return Bundle().apply {
//            putString("from_native", "Hello from MainFragment!")
//        }
//    }
}
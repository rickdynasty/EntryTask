package com.entrytask.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.facebook.react.ReactFragment

abstract class BaseFragment(@LayoutRes private val contentLayoutId: Int = 0) : Fragment() {

    protected var isViewCreated = false
    protected var isFirstVisible = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 如果子类指定了 RN 的组件名，则动态创建一个 FrameLayout 作为容器来承载 ReactFragment
        val componentName = getComponentName()
        if (!componentName.isNullOrEmpty()) {
            val childViewId = View.generateViewId()
            return FrameLayout(requireContext()).apply {
                id = childViewId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }

        // 如果没有指定 RN 组件名，但传了原生的 contentLayoutId，则按常规方式加载 XML 布局
        if (contentLayoutId != 0) {
            return inflater.inflate(contentLayoutId, container, false)
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewCreated = true

        val componentName = getComponentName()
        if (!componentName.isNullOrEmpty()) {
            // 挂载 RN 页面
            mountReactFragment(componentName, getBundle())
        } else {
            // 初始化原生控件
            initView(view, savedInstanceState)
        }

        initData()
    }

    /**
     * 子类实现此方法指定 RN 的 Component 名称。
     * 返回 null 或空字符串则代表是纯原生 Fragment。
     */
    protected open fun getComponentName(): String? = null

    /**
     * 子类可重写此方法提供传递给 RN 页面的 Bundle 参数。
     */
    protected open fun getBundle(): Bundle? = null

    /**
     * 内部私有方法：负责统一挂载 ReactFragment
     */
    private fun mountReactFragment(componentName: String, bundle: Bundle?) {
        val reactFragment = ReactFragment.Builder()
            .setComponentName(componentName)
            .setLaunchOptions(bundle ?: Bundle()) // 如果 bundle 为空，则传入一个新的空 Bundle
            .build()

        childFragmentManager.beginTransaction()
            .replace(view?.id ?: android.R.id.content, reactFragment)
            .commit()
    }

    /**
     * 纯原生 Fragment 初始化控件的方法
     */
    protected open fun initView(view: View, savedInstanceState: Bundle?) {}

    /**
     * 初始化数据
     */
    open fun initData() {}

    override fun onDestroyView() {
        super.onDestroyView()
        isViewCreated = false
        isFirstVisible = true
    }
}
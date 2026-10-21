package com.entrytask

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import com.entry.biz.user.manager.UserAuthManager
import com.entry.lib.base.data.TabItem
import com.entry.lib.base.mvvm.BaseActivity
import com.entry.lib.log.MLog
import com.entry.lib.ui.BackPressHelper
import com.entry.lib.ui.BottomTabBar
import com.entrytask.bridge.ShellCoordinator
import com.entrytask.home.HomeFragment
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import com.swmansion.rnscreens.fragment.restoration.RNScreensFragmentFactory
import org.json.JSONObject

/** One stable HomeFragment/RN Surface for all root tabs. Native-only mode remains available. */
class MainActivity : BaseActivity(), DefaultHardwareBackBtnHandler {
    private lateinit var bottomTabBar: BottomTabBar
    private var authRequested = false
    companion object {
        private const val TAG = "EntryTask.MainActivity"
        const val EXTRA_TAB_ITEMS = "extra_tab_items"
        const val TAB_ID_HOME = 1
        const val TAB_ID_WEALTH = 2
        const val TAB_ID_MEMBER = 3
        const val TAB_ID_ME = 4
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        MLog.i(TAG, "MainActivity 创建，恢复单实例首页宿主")
        supportFragmentManager.fragmentFactory = RNScreensFragmentFactory()
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContentView(R.layout.activity_home)
        bottomTabBar = findViewById(R.id.bottomTabBar)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomTabBar) { view, insets ->
            val bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom
            view.layoutParams = view.layoutParams.apply { height = (56 * resources.displayMetrics.density).toInt() + bottom }
            view.setPadding(0, 0, 0, bottom)
            insets
        }
        bottomTabBar.setTabItems(listOf(
            TabItem(1, "首页", R.drawable.ic_tab_home),
            TabItem(2, "财富", R.drawable.ic_tab_trade),
            TabItem(3, "会员", R.drawable.ic_tab_message),
            TabItem(4, "我的", R.drawable.ic_tab_profile)
        )) { _, id -> selectRoot(id) }
        if (supportFragmentManager.findFragmentByTag("entrytask-root-hub") == null) {
            supportFragmentManager.beginTransaction().add(R.id.fragmentContainer, HomeFragment(), "entrytask-root-hub").commit()
        }
        selectRoot(savedInstanceState?.getInt("root_tab", 1) ?: 1)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (BackPressHelper.hideKeyboardIfVisible(this@MainActivity)) return
                // React Navigation registers the JS child stack with ReactFragment's dispatcher.
                if (ShellCoordinator.depth > 0) {
                    if (reactHostActivityFragment()?.onBackPressed() != true) invokeDefaultOnBackPressed()
                } else invokeDefaultOnBackPressed()
            }
        })
    }
    private fun reactHostActivityFragment(): com.facebook.react.ReactFragment? =
        supportFragmentManager.findFragmentByTag("entrytask-root-hub")?.childFragmentManager
            ?.findFragmentByTag("entrytask-rn-root") as? com.facebook.react.ReactFragment

    private fun selectRoot(id: Int) {
        ShellCoordinator.select(id)
        bottomTabBar.selectTab(id)
        updateChrome()
    }
    /** 登录门禁和子页面都不展示 Native 根底栏，避免外壳与 RN 状态错位。 */
    private fun updateChrome() {
        val (authenticated, _) = UserAuthManager.sessionSnapshot()
        bottomTabBar.visibility = if (authenticated && ShellCoordinator.depth == 0) View.VISIBLE else View.GONE
    }
    fun handleHostCommand(request: JSONObject) {
        when (request.getString("type")) {
            "selectTab" -> selectRoot(request.getInt("tabId"))
            "navigation" -> {
                ShellCoordinator.navigationDepth(request.getInt("depth"))
                updateChrome()
            }
            "authenticate" -> requestAuthentication()
            "logout" -> {
                UserAuthManager.logout()
                ShellCoordinator.navigationDepth(0)
                selectRoot(TAB_ID_HOME)
            }
            else -> throw IllegalArgumentException("Unknown command")
        }
    }
    override fun onResume() {
        super.onResume()
        authRequested = false
        updateChrome()
        ShellCoordinator.publish()
        MLog.i(TAG, "MainActivity 恢复前台并发布会话快照")
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("root_tab", ShellCoordinator.tabId)
        super.onSaveInstanceState(outState)
    }
    override fun invokeDefaultOnBackPressed() {
        if (ShellCoordinator.tabId != 1) { selectRoot(1) }
        else BackPressHelper.handleBackPress(this) { finishAffinity() }
    }

    /** 只由 RN Host Bridge 调用；登录页作为当前首页之上的 Activity 展示。 */
    private fun requestAuthentication() {
        if (UserAuthManager.isLogin()) {
            MLog.i(TAG, "登录请求已满足，跳过打开认证页")
            ShellCoordinator.publish()
            return
        }
        if (authRequested || isFinishing || isDestroyed) {
            MLog.i(TAG, "忽略重复或无效的登录页请求")
            return
        }
        authRequested = true
        MLog.i(TAG, "通过 Native Bridge 拉起登录注册界面")
        try {
            UserAuthManager.startAuthActivity(this)
        } catch (error: Exception) {
            // 启动失败也要解除防重锁，允许 RN 门禁的显式重试。
            authRequested = false
            throw error
        }
    }
}

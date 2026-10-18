package com.entrytask

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import com.entry.biz.user.manager.UserAuthManager
import com.entry.lib.base.data.TabItem
import com.entry.lib.base.mvvm.BaseActivity
import com.entry.lib.ui.BackPressHelper
import com.entry.lib.ui.BottomTabBar
import com.entrytask.bridge.ShellCoordinator
import com.entrytask.home.HomeFragment
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import org.json.JSONObject

/** One stable HomeFragment/RN Surface for all root tabs. Native-only mode remains available. */
class MainActivity : BaseActivity(), DefaultHardwareBackBtnHandler {
    private lateinit var bottomTabBar: BottomTabBar
    private var authRequested = false
    companion object {
        const val EXTRA_TAB_ITEMS = "extra_tab_items"
        const val TAB_ID_HOME = 1
        const val TAB_ID_WEALTH = 2
        const val TAB_ID_MEMBER = 3
        const val TAB_ID_ME = 4
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = com.swmansion.rnscreens.fragment.restoration.RNScreensFragmentFactory()
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
    }
    fun handleHostCommand(request: JSONObject) {
        when (request.getString("type")) {
            "selectTab" -> selectRoot(request.getInt("tabId"))
            "navigation" -> {
                ShellCoordinator.navigationDepth(request.getInt("depth"))
                bottomTabBar.visibility = if (ShellCoordinator.depth > 0) View.GONE else View.VISIBLE
            }
            "authenticate" -> if (!UserAuthManager.isLogin() && !authRequested) {
                authRequested = true
                UserAuthManager.startAuthActivity(this)
            }
            "logout" -> { UserAuthManager.logout(); ShellCoordinator.publish() }
            else -> throw IllegalArgumentException("Unknown command")
        }
    }
    override fun onResume() {
        super.onResume()
        authRequested = false
        ShellCoordinator.publish()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("root_tab", ShellCoordinator.tabId)
        super.onSaveInstanceState(outState)
    }
    override fun invokeDefaultOnBackPressed() {
        if (ShellCoordinator.tabId != 1) { selectRoot(1) }
        else BackPressHelper.handleBackPress(this) { finishAffinity() }
    }
}

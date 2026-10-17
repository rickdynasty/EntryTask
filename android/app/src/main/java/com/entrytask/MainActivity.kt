package com.entrytask

//import com.entrytask.databinding.ActivityHomeBinding // 如果启用 ViewBinding
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.entry.biz.user.manager.UserAuthManager
import com.entry.lib.base.data.TabItem
import com.entry.lib.base.mvvm.BaseActivity
import com.entry.lib.log.MLog
import com.entry.lib.ui.BackPressHelper
import com.entry.lib.ui.BottomTabBar
import com.entrytask.home.HomeFragment
import com.entrytask.home.MeFragment
import com.entrytask.home.MemberFragment
import com.entrytask.home.WealthFragment
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler

// 若未启用 ViewBinding，可直接使用 findViewById

class MainActivity : BaseActivity(), DefaultHardwareBackBtnHandler {
    private lateinit var bottomTabBar: BottomTabBar
    private lateinit var tabItems: List<TabItem>

    // 缓存 Fragment 映射：ID -> Fragment 实例
    private val fragments = mutableMapOf<Int, Fragment>()
    private var currentTabId: Int = -1

    companion object {
        private const val TAG = "Rick_tan:HomeActivity"
        const val EXTRA_TAB_ITEMS = "extra_tab_items"

        // 定义 4 个固定的 Tab ID
        const val TAB_ID_HOME = 1
        const val TAB_ID_WEALTH = 2
        const val TAB_ID_MEMBER = 3
        const val TAB_ID_ME = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                BackPressHelper.handleBackPress(this@MainActivity) {
                    finishAffinity() // 连续双击两次，退出应用
                }
            }
        })

        setContentView(R.layout.activity_home)

        bottomTabBar = findViewById(R.id.bottomTabBar)

        // 1. 初始化 4 个 Tab 数据（优先从 Intent 解析，若无则使用指定的默认 4 个 Tab）
        initTabItems()

        // 2. 初始化底部导航栏并绑定点击回调
        setupBottomTabBar()

        // 3. 恢复或默认选中第一个 Tab (首页)
        val defaultId =
            savedInstanceState?.getInt("current_tab_id", tabItems.first().id) ?: tabItems.first().id
        switchFragment(defaultId)
        bottomTabBar.selectTab(defaultId)

        // 启动时检查内存登录态
        checkLoginStatus()
    }

    /**
     * 校验登录状态，若未登录则拦截跳转到 AuthActivity
     */
    private fun checkLoginStatus(needLog: Boolean = true) {
        if (!UserAuthManager.isLogin()) {
            MLog.w(TAG, "检测到用户未登录，正在拦截并跳转至 AuthActivity")
            UserAuthManager.startAuthActivity(this)
        } else if (needLog) {
            MLog.d(TAG, "用户已登录，放行进入首页")
        }
    }

    private fun initTabItems() {
        val intentItems =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(EXTRA_TAB_ITEMS, TabItem::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<TabItem>(EXTRA_TAB_ITEMS)
            }

        tabItems = if (!intentItems.isNullOrEmpty()) {
            intentItems
        } else {
            // 严格设定默认的 4 个 Tab：首页、交易、信息、我的
            listOf(
                TabItem(TAB_ID_HOME, "首页", R.drawable.ic_tab_home),
                TabItem(TAB_ID_WEALTH, "财富", R.drawable.ic_tab_trade),
                TabItem(TAB_ID_MEMBER, "会员", R.drawable.ic_tab_message),
                TabItem(TAB_ID_ME, "我的", R.drawable.ic_tab_profile)
            )
        }
    }

    private fun setupBottomTabBar() {
        bottomTabBar.setTabItems(tabItems) { _, id ->
            switchFragment(id)
        }
    }

    private fun switchFragment(targetId: Int) {
        if (currentTabId == targetId) return

        val transaction = supportFragmentManager.beginTransaction()

        // 隐藏当前显示的 Fragment
        if (currentTabId != -1) {
            fragments[currentTabId]?.let { transaction.hide(it) }
        }

        // 查找或创建目标 Fragment
        var targetFragment = fragments[targetId]
        if (targetFragment == null) {
            targetFragment = createFragmentForId(targetId)
            fragments[targetId] = targetFragment
            transaction.add(R.id.fragmentContainer, targetFragment, targetId.toString())
        } else {
            transaction.show(targetFragment)
        }

        transaction.commitAllowingStateLoss()
        currentTabId = targetId
    }

    private fun createFragmentForId(id: Int): Fragment {
        // 根据 4 个 Tab ID 返回对应的子 Fragment 实例
        return when (id) {
            TAB_ID_HOME-> HomeFragment()
            TAB_ID_WEALTH -> WealthFragment()
            TAB_ID_MEMBER -> MemberFragment()
            TAB_ID_ME -> MeFragment()
            else -> HomeFragment() // 默认兜底
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 登录成功后重载检测
        if (!UserAuthManager.isLogin()) {
            UserAuthManager.startAuthActivity(this)
        }
    }

    override fun onResume() {
        super.onResume()
        MLog.d(TAG, "HomeActivity onResume 恢复，进行二次登录态校验")

        checkLoginStatus(false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_tab_id", currentTabId)
    }

    override fun invokeDefaultOnBackPressed() {
        // 如果你的 MainFragment 中有 RN 的页面，可以先让 RN 处理返回键（例如关闭 RN 的 Modal 或页面路由）
        // 如果不需要特殊拦截，可以直接调用 super.onBackPressed() 或者交由 React Native 处理
        super.onBackPressed()
    }
}
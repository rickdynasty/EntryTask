package com.entry.biz.dashboard.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.entry.biz.dashboard.R
import com.entry.biz.dashboard.domain.*
import com.entry.biz.dashboard.presentation.DashboardUiState

sealed interface DashboardAction {
    data object Retry : DashboardAction
    data object ToggleAssets : DashboardAction
    data class Category(val id: String) : DashboardAction
    data class Open(val title: String) : DashboardAction
}

/** XML页面渲染器：无IO、无VM持有；共同组件组装四个原型页面。 */
class DashboardView(private val root: View, private val onAction: (DashboardAction) -> Unit) {
    private val container = root.findViewById<FrameLayout>(R.id.dashboardPage)
    private val scroll = root.findViewById<ScrollView>(R.id.dashboardScroll)
    private var previous: DashboardUiState? = null
    private var renderedTab: Int? = null
    private val positions = mutableMapOf<Int, Int>()
    private val open: (String) -> Unit = { onAction(DashboardAction.Open(it)) }
    init { root.findViewById<View>(R.id.dashboardRetry).setOnClickListener { onAction(DashboardAction.Retry) } }

    fun render(state: DashboardUiState) {
        root.findViewById<View>(R.id.dashboardStateRow).visibility = if (state.loading || state.error != null) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.dashboardLoading).visibility = if (state.loading) View.VISIBLE else View.GONE
        root.findViewById<TextView>(R.id.dashboardStatus).apply {
            text = state.error ?: if (state.loading) context.getString(R.string.native_loading) else ""
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        root.findViewById<Button>(R.id.dashboardRetry).apply {
            setText(if (state.error == null) R.string.native_refresh else R.string.native_retry)
            isEnabled = !state.loading
        }
        container.visibility = if (state.authenticated && state.page != null) View.VISIBLE else View.GONE
        if (!state.authenticated) {
            container.removeAllViews(); positions.clear(); renderedTab = null
            previous = state
            return
        }
        val page = state.page
        if (page != null && (page !== previous?.page || state.tab != previous?.tab)) {
            renderedTab?.let { positions[it] = scroll.scrollY }
            container.removeAllViews()
            val layout = listOf(R.layout.dashboard_home, R.layout.dashboard_wealth, R.layout.dashboard_member, R.layout.dashboard_profile)[state.tab - 1]
            LayoutInflater.from(root.context).inflate(layout, container, true)
            bindPage(page, state.category)
            val position = positions[state.tab] ?: 0
            renderedTab = state.tab
            scroll.post { scroll.scrollTo(0, position) }
        }
        bindAssets(state)
        previous = state
    }
    private fun text(id: Int, value: String) { container.findViewById<TextView>(id)?.text = value }
    private fun click(id: Int, title: Int) { container.findViewById<View>(id)?.setOnClickListener { open(root.context.getString(title)) } }
    private fun promo(id: Int, data: Promotion?) {
        container.findViewById<PromotionCardView>(id)?.apply {
            visibility = if (data == null) View.GONE else View.VISIBLE
            data?.let { bind(it, open) }
        }
    }
    private fun chips(id: Int, values: List<ServiceEntry>, category: String? = null, selectable: Boolean = false) {
        val row = container.findViewById<LinearLayout>(id) ?: return
        row.removeAllViews()
        values.forEachIndexed { index, item ->
            val button = TextView(root.context).apply {
                text = item.title; textSize = 14f; gravity = android.view.Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8)); minHeight = dp(48)
                setTextColor(if (selectable && (category == item.category || category == null && index == 0)) 0xFFB2763D.toInt() else 0xFF514B46.toInt())
                setBackgroundResource(R.drawable.dashboard_soft)
                isSelected = selectable && (category == item.category || category == null && index == 0)
                isFocusable = true
                setOnClickListener { if (selectable) onAction(DashboardAction.Category(item.category)) else open(item.title) }
            }
            row.addView(button, LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 0, button.dp(8), button.dp(8)) })
        }
    }
    private fun bindPage(page: DashboardPage, category: String?) {
        container.findViewById<BankHeaderView>(R.id.pageHeader).bind(page.tab, open)
        container.findViewById<ServiceGridView>(R.id.services)?.bind(page.services, onAction = open)
        container.findViewById<ServiceGridView>(R.id.quickServices)?.bind(page.quick, columns = 4, quick = true, onAction = open)
        chips(R.id.shortcuts, page.shortcuts)
        chips(R.id.categories, page.categories, category, selectable = true)
        when (page.tab) {
            1 -> { promo(R.id.heroPromo, page.promotions.getOrNull(0)); promo(R.id.giftPromo, page.promotions.getOrNull(1)) }
            2 -> {
                text(R.id.market, "${root.context.getString(R.string.native_market)}  ｜  ${page.market}  ›")
                click(R.id.market, R.string.native_market)
                promo(R.id.giftPromo, page.promotions.firstOrNull())
                container.findViewById<FundChartView>(R.id.fundChart).values = page.products.take(3).map { it.ratio }
                click(R.id.viewFunds, R.string.native_funds)
            }
            3 -> {
                text(R.id.memberWelcome, page.welcome)
                promo(R.id.customCard, Promotion(root.context.getString(R.string.native_card_title), root.context.getString(R.string.native_card_subtitle), "custom-cards"))
                promo(R.id.levelPromo, Promotion(root.context.getString(R.string.native_level), root.context.getString(R.string.native_level_subtitle), "crown"))
                promo(R.id.bookPromo, Promotion(root.context.getString(R.string.native_book), root.context.getString(R.string.native_book_subtitle), "book"))
                click(R.id.memberBenefits, R.string.native_demo_member); click(R.id.checkin, R.string.native_checkin)
                click(R.id.applyCard, R.string.native_card_action)
            }
            4 -> {
                container.findViewById<SummaryStatsView>(R.id.summaryStats).bind(page.counts)
                text(R.id.slogan, page.slogan); text(R.id.notice, page.notice)
                text(R.id.loanAmount, "¥${page.loanLimit}    ${page.loanRate}")
                promo(R.id.creditPromo, Promotion(root.context.getString(R.string.native_credit), root.context.getString(R.string.native_credit_subtitle), "credit-gift"))
                click(R.id.borrow, R.string.native_borrow)
            }
        }
        val products = container.findViewById<LinearLayout>(R.id.products) ?: return
        if (page.products.isEmpty()) products.addView(TextView(root.context).apply {
            setText(R.string.native_empty_products); setPadding(dp(12), dp(20), dp(12), dp(20))
        })
        page.products.forEach { product ->
            // 产品卡复用同一XML文案/图片结构，点击仅展示当前模拟产品说明。
            val view = PromotionCardView(root.context)
            view.bind(Promotion(product.name, "${product.ratio}   ${product.period}\n${product.risk}", "")) {
                open("${product.name}\n${product.ratio} · ${product.risk}")
            }
            products.addView(view, LinearLayout.LayoutParams(-1, view.dp(112)).apply { bottomMargin = view.dp(8) })
        }
    }
    private fun bindAssets(state: DashboardUiState) {
        val page = state.page ?: return
        container.findViewById<View>(R.id.assetTitle)?.visibility = if (state.tab == 4) View.VISIBLE else View.INVISIBLE
        text(R.id.totalBalance, if (state.revealAssets) "¥${page.total}" else "******")
        text(R.id.incomeBalance, if (state.revealAssets) page.income else "******")
        container.findViewById<View>(R.id.toggleAssets)?.setOnClickListener { onAction(DashboardAction.ToggleAssets) }
    }
}

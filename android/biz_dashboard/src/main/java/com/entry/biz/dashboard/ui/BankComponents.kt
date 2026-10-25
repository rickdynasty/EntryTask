package com.entry.biz.dashboard.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.entry.biz.dashboard.R
import com.entry.biz.dashboard.domain.Promotion
import com.entry.biz.dashboard.domain.ServiceEntry

internal fun View.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

/** 显式资源白名单，避免任意assetKey变成资源路径。装饰图来自共享PRD素材。 */
internal fun art(key: String): Int = when (key) {
    "hero-ginkgo" -> R.drawable.dashboard_hero_ginkgo
    "certification-gift" -> R.drawable.dashboard_certification_gift
    "seasonal-gifts" -> R.drawable.dashboard_seasonal_gifts
    "custom-cards" -> R.drawable.dashboard_custom_cards
    "crown" -> R.drawable.dashboard_crown
    "book" -> R.drawable.dashboard_book
    "credit-gift" -> R.drawable.dashboard_credit_gift
    else -> 0
}

/** XML工具栏；只上报搜索/消息/客服动作，不持有认证或页面状态。 */
class BankHeaderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(12), 0)
        setBackgroundResource(R.drawable.dashboard_warm)
        LayoutInflater.from(context).inflate(R.layout.dashboard_header, this, true)
    }
    fun bind(tab: Int, onAction: (String) -> Unit) {
        val title = findViewById<TextView>(R.id.headerTitle)
        title.setText(listOf(R.string.native_search, R.string.native_wealth, R.string.native_member, R.string.native_profile)[tab - 1])
        val home = tab == 1
        title.textSize = if (home) 16f else 22f
        title.layoutParams = LayoutParams(if (home) 0 else LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, if (home) 1f else 0f)
        title.setBackgroundResource(if (home) R.drawable.dashboard_card else android.R.color.transparent)
        title.setPadding(if (home) dp(8) else 0, 0, if (home) dp(8) else 0, 0)
        findViewById<View>(R.id.headerSpacer).layoutParams = LayoutParams(0, 1, if (home) 0f else 1f)
        title.setOnClickListener { onAction(context.getString(R.string.native_search)) }
        findViewById<TextView>(R.id.headerSecondary).apply {
            setText(when(tab) { 2 -> R.string.native_news; 3 -> R.string.native_activities; 4 -> R.string.native_settings; else -> R.string.native_scan })
            setOnClickListener { onAction(text.toString()) }
            if (home) {
                removeView(this)
                addView(this, 0)
            }
        }
        findViewById<View>(R.id.headerMessages).setOnClickListener { onAction(context.getString(R.string.native_messages)) }
        findViewById<View>(R.id.headerAdvisor).setOnClickListener { onAction(context.getString(R.string.native_advisor)) }
    }
}

/** 多页共用服务宫格：固定列数、自适应等宽；每个入口保留可访问文本。 */
class ServiceGridView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    init { orientation = VERTICAL }
    fun bind(items: List<ServiceEntry>, columns: Int = 5, quick: Boolean = false, onAction: (String) -> Unit) {
        removeAllViews()
        items.chunked(columns).forEach { group ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            repeat(columns) { column ->
                val entry = group.getOrNull(column)
                val cell = LayoutInflater.from(context).inflate(R.layout.dashboard_service, row, false)
                row.addView(cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                if (entry == null) cell.visibility = INVISIBLE else {
                    cell.findViewById<TextView>(R.id.serviceTitle).text = entry.title
                    val iconView = cell.findViewById<ServiceIconView>(R.id.serviceIcon)
                    iconView.icon = entry.icon
                    if (quick) {
                        iconView.visibility = GONE
                        cell.findViewById<ImageView>(R.id.serviceImage).apply {
                            visibility = VISIBLE
                            setImageResource(listOf(R.drawable.dashboard_quick_0, R.drawable.dashboard_quick_1,
                                R.drawable.dashboard_quick_2, R.drawable.dashboard_quick_3)[column % 4])
                        }
                    }
                    cell.findViewById<TextView>(R.id.serviceBadge).apply {
                        text = entry.badge; visibility = if (entry.badge.isEmpty()) GONE else VISIBLE
                    }
                    cell.contentDescription = entry.title
                    cell.isFocusable = true
                    cell.setOnClickListener { onAction(entry.title) }
                }
            }
        }
    }
}

/** 标题/说明/局部装饰图的共用营销卡；无业务请求或导航逻辑。 */
class PromotionCardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private var hasArt = false
    private var tallArt = false
    private var layoutMode: Int? = null
    init {
        setBackgroundResource(R.drawable.dashboard_warm)
        LayoutInflater.from(context).inflate(R.layout.dashboard_promo, this, true)
    }
    fun bind(promotion: Promotion, onAction: (String) -> Unit) {
        findViewById<TextView>(R.id.promoTitle).text = promotion.title
        findViewById<TextView>(R.id.promoSubtitle).text = promotion.subtitle
        val resource = art(promotion.art)
        hasArt = resource != 0
        tallArt = promotion.art == "custom-cards"
        findViewById<ImageView>(R.id.promoArt).apply {
            setImageResource(resource); visibility = if (resource == 0) GONE else VISIBLE
        }
        adaptLayout(width)
        contentDescription = "${promotion.title}，${promotion.subtitle}"
        isFocusable = true
        setOnClickListener { onAction(promotion.title) }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 测量子View之前切换窄卡布局，避免在layout期间改参数造成首帧仍挤在图旁。
        adaptLayout(MeasureSpec.getSize(widthMeasureSpec))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    private fun adaptLayout(w: Int) {
        val narrow = hasArt && w in 1 until dp(260)
        val mode = if (!hasArt) 0 else if (narrow && tallArt) 3 else if (narrow) 1 else 2
        if (layoutMode == mode) return
        layoutMode = mode
        val artHeight = if (tallArt) 120 else 65
        val title = findViewById<TextView>(R.id.promoTitle)
        title.textSize = if (narrow) 17f else 22f
        val copy = title.parent as LinearLayout
        copy.gravity = if (narrow) Gravity.TOP else Gravity.CENTER_VERTICAL
        copy.setPaddingRelative(dp(14), dp(12), if (hasArt && !narrow) dp(130) else dp(14), if (narrow) dp(artHeight) else dp(12))
        findViewById<ImageView>(R.id.promoArt).layoutParams = LayoutParams(
            if (narrow && tallArt) LayoutParams.MATCH_PARENT else dp(if (narrow) 86 else 140),
            if (narrow) dp(artHeight) else LayoutParams.MATCH_PARENT,
            Gravity.END or if (narrow) Gravity.BOTTOM else Gravity.CENTER_VERTICAL)
    }
}

/** Canvas线性图标复用金黑色视觉语言，不用整页截图替代控件。 */
class ServiceIconView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var icon: String = "more"
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shieldPath = Path().apply {
        moveTo(20f, 3f); lineTo(35f, 12f); lineTo(32f, 29f)
        lineTo(20f, 38f); lineTo(7f, 29f); lineTo(5f, 12f); close()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        canvas.scale(width / 40f, height / 40f)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f; paint.color = Color.rgb(45, 43, 39)
        when (icon) {
            "fund", "wallet", "card", "ledger", "city" -> canvas.drawRect(5f, 5f, 35f, 35f, paint)
            "diamond", "shield", "family", "hexagon" -> {
                canvas.drawPath(shieldPath, paint)
            }
            "gold" -> {
                canvas.drawRect(14f, 3f, 26f, 16f, paint); canvas.drawRect(3f, 21f, 17f, 35f, paint); canvas.drawRect(23f, 21f, 37f, 35f, paint)
            }
            else -> canvas.drawCircle(20f, 20f, 17f, paint)
        }
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(189, 137, 64); paint.textSize = 22f; paint.textAlign = Paint.Align.CENTER
        val glyph = when(icon) { "more" -> "···"; "transfer", "exchange" -> "⇆"; "fund" -> "↗"; "shield", "pig" -> "+"; "diamond" -> "◇"; "gold" -> ""; else -> "¥" }
        canvas.drawText(glyph, 20f, 28f, paint)
        canvas.restoreToCount(save)
    }
}

/** 基金卡中的示意图；数值取当前Mock产品，禁止绘制未经数据支持的收益承诺。 */
class FundChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var proportions: List<Float> = emptyList()
    var values: List<String> = emptyList()
        set(value) {
            field = value.take(3)
            contentDescription = field.joinToString("，")
            val numbers = field.map { it.removeSuffix("%").toFloatOrNull() ?: 0f }
            val max = numbers.maxOrNull()?.coerceAtLeast(0.01f) ?: 1f
            proportions = numbers.map { (it / max).coerceIn(0f, 1f) }
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        values.forEachIndexed { index, value ->
            val x = w * (index + 0.5f) / 3
            val barHeight = h * 0.55f * proportions[index]
            paint.color = Color.rgb(242, 102, 82)
            canvas.drawRoundRect(x - w / 12, h - barHeight - dp(24), x + w / 12, h - dp(24).toFloat(), dp(6).toFloat(), dp(6).toFloat(), paint)
            paint.color = Color.rgb(223, 66, 57); paint.textSize = dp(18).toFloat(); paint.textAlign = Paint.Align.CENTER
            canvas.drawText(value, x, h - barHeight - dp(30), paint)
        }
    }
}


/** 等权统计条，数值与名称逐列对齐，避免用空格拼接在不同字体下错位。 */
class SummaryStatsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    init { orientation = HORIZONTAL }
    fun bind(counts: List<String>) {
        removeAllViews()
        val labels = listOf(R.string.native_stat_cards, R.string.native_stat_todos, R.string.native_stat_coupons,
            R.string.native_stat_points, R.string.native_stat_energy)
        labels.forEachIndexed { index, label ->
            val cell = LayoutInflater.from(context).inflate(R.layout.dashboard_stat, this, false)
            cell.findViewById<TextView>(R.id.statValue).text = counts.getOrElse(index) { "0" }
            cell.findViewById<TextView>(R.id.statLabel).setText(label)
            addView(cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
    }
}

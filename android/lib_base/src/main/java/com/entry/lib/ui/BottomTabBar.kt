package com.entry.lib.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.entry.lib.base.R
import com.entry.lib.base.data.TabItem

class BottomTabBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var onTabSelectedListener: ((Int, Int) -> Unit)? = null
    private var currentSelectedId: Int = -1
    private val tabViews = mutableMapOf<Int, LinearLayout>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        // 可根据需要设置背景色或阴影
        setBackgroundColor(Color.WHITE)
    }

    fun setTabItems(items: List<TabItem>, onSelected: (position: Int, id: Int) -> Unit) {
        this.onTabSelectedListener = onSelected
        removeAllViews()
        tabViews.clear()
        currentSelectedId = -1

        items.forEachIndexed { index, item ->
            val tabView = createTabView(item)
            val params = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            tabView.layoutParams = params

            tabView.setOnClickListener {
                // 点击只上报意图，选中外观等待外部 UiState 渲染。
                onTabSelectedListener?.invoke(index, item.id)
            }

            addView(tabView)
            tabViews[item.id] = tabView
            tabView.contentDescription = item.title
            setTabState(tabView, false)
        }
    }

    private fun createTabView(item: TabItem): LinearLayout {
        val layout = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
        }

        val imageView = ImageView(context).apply {
            id = android.R.id.icon
            setImageResource(item.iconRes)
            layoutParams = LayoutParams(dpToPx(24), dpToPx(24))
        }

        val textView = TextView(context).apply {
            id = android.R.id.text1
            text = item.title
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(2)
            }
        }

        layout.addView(imageView)
        layout.addView(textView)
        return layout
    }

    /** 只更新控件外观；不发布业务选择事件，不独立修改 Shell 状态。 */
    fun selectTab(id: Int) {
        if (currentSelectedId == id) return

        // 恢复上一个选中状态
        tabViews[currentSelectedId]?.let { oldView ->
            setTabState(oldView, false)
        }

        // 设置新的选中状态
        tabViews[id]?.let { newView ->
            setTabState(newView, true)
            currentSelectedId = id
        }
    }

    private fun setTabState(tabView: LinearLayout, isSelected: Boolean) {
        tabView.isSelected = isSelected
        val icon = tabView.findViewById<ImageView>(android.R.id.icon)
        val text = tabView.findViewById<TextView>(android.R.id.text1)

        val color = if (isSelected) {
            ContextCompat.getColor(context, R.color.tab_select) // 选中颜色（可替换为您的主题色）
        } else {
            ContextCompat.getColor(context, R.color.tab_unselect) // 未选中颜色
        }

        icon.setColorFilter(color)
        text.setTextColor(color)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
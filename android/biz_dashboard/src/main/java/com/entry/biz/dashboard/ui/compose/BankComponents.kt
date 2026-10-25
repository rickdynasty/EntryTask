package com.entry.biz.dashboard.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.entry.biz.dashboard.domain.pages.*
import com.entry.biz.dashboard.presentation.pages.RegionState
import kotlinx.coroutines.delay

internal val Ink = Color(0xFF292623)
internal val Muted = Color(0xFF8E857B)
internal val Gold = Color(0xFFB78C5D)
internal val Brand = Color(0xFFF44759)
internal val PageBackground = Color(0xFFF5F5F5)

/** 主题原语没有IO和业务状态；卡片不自行创建VM。 */
@Composable
fun BankCard(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(color).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
internal fun SectionTitle(title: String) {
    Text(
        title,
        color = Ink,
        fontSize = 18.sp,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun Caption(text: String, color: Color = Muted) {
    Text(text, color = color, fontSize = 12.sp)
}

@Composable
internal fun BankIcon(name: String, modifier: Modifier = Modifier.size(25.dp), color: Color = Ink) {
    val context = LocalContext.current
    val id =
        remember(name) {
            context.resources
                .getIdentifier(
                    "dashboard_icon_${name.replace('-', '_')}",
                    "drawable",
                    context.packageName,
                )
                .takeIf { it != 0 }
                ?: context.resources.getIdentifier(
                    "dashboard_icon_more",
                    "drawable",
                    context.packageName,
                )
        }
    if (id != 0) Icon(painterResource(id), null, modifier, tint = color)
}

@Composable
internal fun Art(name: String, modifier: Modifier) {
    val context = LocalContext.current
    val id =
        remember(name) {
            context.resources.getIdentifier(
                "dashboard_${name.replace('-', '_')}",
                "drawable",
                context.packageName,
            )
        }
    if (id != 0) Image(painterResource(id), null, modifier, contentScale = ContentScale.Fit)
}

@Composable
internal fun IconAction(name: String, label: String, onClick: () -> Unit, color: Color = Ink) {
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        BankIcon(name, color = color)
    }
}

@Composable
fun MoneyText(value: MoneyValue?, visible: Boolean, large: Boolean = false, color: Color = Ink) {
    Text(
        if (!visible) "******" else value?.text ?: "—",
        Modifier.semantics { if (!visible) contentDescription = "金额已隐藏" },
        color = color,
        fontSize = if (large) 25.sp else 19.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun EyeToggle(visible: Boolean, onToggle: () -> Unit) {
    val scale by animateFloatAsState(if (visible) 1f else .88f, label = "privacy-eye")
    Box(Modifier.scale(scale)) {
        IconAction(
            if (visible) "eye-open" else "eye",
            if (visible) "隐藏资产" else "显示资产",
            onToggle,
            Gold,
        )
    }
}

@Composable
fun AssetPrivacyCard(data: AssetOverview?, visible: Boolean, onToggle: () -> Unit) {
    BankCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("资产总览")
            EyeToggle(visible, onToggle)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                MoneyText(data?.total, visible, true)
                Caption("总资产（元）")
            }
            Column {
                MoneyText(data?.income, visible)
                Caption("最新收益（元）")
            }
        }
    }
}

/** 同一宫格内核控制列宽/角标/触摸范围，业务菜单由调用方传入。 */
@Composable
fun StandardGridMenu(
    items: List<MenuEntry>,
    columns: Int,
    gold: Boolean = false,
    blue: Boolean = false,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        items.chunked(columns).forEachIndexed { row, entries ->
            Row(Modifier.fillMaxWidth()) {
                entries.forEachIndexed { col, item ->
                    Column(
                        Modifier.weight(1f)
                            .heightIn(min = 85.dp)
                            .clickable { onSelect(item.title) }
                            .padding(vertical = 10.dp)
                            .semantics { role = Role.Button },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Box {
                            if (gold) Art("quick-${row * columns + col}", Modifier.size(45.dp))
                            else
                                BankIcon(
                                    item.icon,
                                    Modifier.size(31.dp),
                                    if (blue) Color(0xFF23A9D8) else Ink,
                                )
                            if (item.badge.isNotEmpty())
                                Text(
                                    item.badge,
                                    Modifier.align(Alignment.TopEnd)
                                        .offset(16.dp, (-8).dp)
                                        .background(Brand, RoundedCornerShape(5.dp))
                                        .padding(horizontal = 3.dp),
                                    color = Color.White,
                                    fontSize = 8.sp,
                                )
                        }
                        Text(
                            item.title,
                            color = Ink,
                            fontSize = if (gold) 15.sp else 12.sp,
                            maxLines = 2,
                        )
                    }
                }
                repeat(columns - entries.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun AppFooterLogo() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 38.dp).clearAndSetSemantics {
            contentDescription = "中信银行，CHINA CITIC BANK，让财富有温度"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BankIcon("bank-brand", Modifier.size(36.dp), Color(0xFFC92336))
            Column {
                Text(
                    "中信银行",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = Ink,
                )
                Text("CHINA CITIC BANK", color = Ink, fontSize = 8.sp, letterSpacing = .8.sp)
            }
        }
        Text("让　财　富　有　温　度", color = Muted, fontSize = 9.sp)
    }
}

@Composable
internal fun <T> RegionStateView(state: RegionState<T>, retry: () -> Unit) {
    when {
        state.error != null ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caption(state.error)
                TextButton(onClick = retry) { Text("重试", color = Gold) }
            }
        state.loading && state.data == null ->
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Gold)
            }
    }
}

@Composable
internal fun DemoActionSheet(title: String?, onClose: () -> Unit) {
    if (title != null)
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(title) },
            text = { Text("本入口仅作界面演示，不发起真实银行业务。") },
            confirmButton = { TextButton(onClick = onClose) { Text("我知道了") } },
        )
}

/** 只有可见区域与前台RESUMED期间才运行周期，数据0/1不启动。 */
@Composable
private fun CarouselEffect(enabled: Boolean, count: Int, tick: suspend () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(tick)
    LaunchedEffect(enabled, count, lifecycle) {
        if (enabled && count > 1)
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(5000)
                    latest()
                }
            }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerCarousel(items: List<Banner>, visible: Boolean, onSelect: (String) -> Unit) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(pageCount = { items.size })
    val dragging by pager.interactionSource.collectIsDraggedAsState()
    var touching by remember { mutableStateOf(false) }
    CarouselEffect(visible && !dragging && !touching, items.size) {
        pager.animateScrollToPage((pager.currentPage + 1) % items.size)
    }
    Column {
        HorizontalPager(
            state = pager,
            modifier =
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            touching =
                                awaitPointerEvent(PointerEventPass.Initial).changes.any {
                                    it.pressed
                                }
                        }
                    }
                },
            key = { items[it].id },
        ) { index ->
            val banner = items[index]
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(min = 158.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFBE9D8))
                    .clickable { onSelect(banner.title) }
                    .semantics {
                        contentDescription = banner.title
                        role = Role.Button
                    }
            ) {
                Art(banner.art, Modifier.align(Alignment.CenterEnd).size(160.dp))
                Column(Modifier.padding(18.dp).fillMaxWidth(.7f)) {
                    Text(
                        banner.title,
                        fontSize = 27.sp,
                        fontFamily = FontFamily.Serif,
                        color = Color(0xFF915319),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(banner.subtitle, color = Color(0xFF985C2B), fontSize = 13.sp)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            items.indices.forEach { index ->
                Box(
                    Modifier.padding(horizontal = 3.dp)
                        .size(if (index == pager.currentPage) 15.dp else 5.dp, 5.dp)
                        .background(
                            if (index == pager.currentPage) Gold else Color(0xFFD6CBC0),
                            RoundedCornerShape(3.dp),
                        )
                )
            }
        }
    }
}

@Composable
fun MarketTickerCarousel(
    items: List<Pair<String, String>>,
    visible: Boolean,
    onSelect: (String) -> Unit,
) {
    if (items.isEmpty()) return
    var selectedId by remember { mutableStateOf<String?>(null) }
    val index = items.indexOfFirst { it.first == selectedId }.coerceAtLeast(0)
    val interaction = remember { MutableInteractionSource() }
    val touched by interaction.collectIsPressedAsState()
    val offset = remember { Animatable(0f) }
    val scale = androidx.compose.ui.platform.LocalDensity.current.density
    CarouselEffect(visible && !touched, items.size) {
        try {
            offset.animateTo(-30f * scale, animationSpec = tween(220))
            selectedId = items[(index + 1) % items.size].first
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { offset.snapTo(0f) }
        }
    }
    // 离开可见区时中断动画，避免后台遗留偏移。
    LaunchedEffect(visible) {
        if (!visible) {
            offset.stop()
            offset.snapTo(0f)
        }
    }
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(interactionSource = interaction, indication = null) {
                onSelect(items[index].second)
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text("市场解读", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(0.dp))) {
            Column(Modifier.offset { IntOffset(0, offset.value.toInt()) }) {
                Text(
                    items[index].second,
                    Modifier.height(30.dp),
                    fontSize = 12.sp,
                    lineHeight = 30.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Ink,
                )
                Text(
                    items[(index + 1) % items.size].second,
                    Modifier.height(30.dp),
                    fontSize = 12.sp,
                    lineHeight = 30.sp,
                    maxLines = 1,
                    color = Ink,
                )
            }
        }
        BankIcon("chevron", Modifier.size(16.dp))
    }
}

/** Preview只使用合成展示模型，不能触发网络和持久化。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390)
@Composable
private fun HiddenAssetPreview() {
    MaterialTheme {
        AssetPrivacyCard(AssetOverview(MoneyValue("0.00"), MoneyValue("0.00")), false) {}
    }
}

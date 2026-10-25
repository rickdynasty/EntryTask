package com.entry.biz.dashboard.ui.compose

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.entry.biz.dashboard.domain.pages.*
import com.entry.biz.dashboard.presentation.pages.*

private data class RootRegion(val id: String, val content: @Composable (Boolean) -> Unit)

/** 每Tab的列表恢复由SaveableStateHolder包裹；Logo始终是最后一个内容项。 */
@Composable
private fun BankPage(regions: List<RootRegion>, enabled: Boolean) {
    val list = rememberLazyListState()
    val visible by remember {
        derivedStateOf { list.layoutInfo.visibleItemsInfo.map { it.key }.toSet() }
    }
    LazyColumn(
        Modifier.fillMaxSize().background(PageBackground).testTag("dashboard-list"),
        state = list,
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(regions, key = { it.id }, contentType = { it.id }) { region ->
            region.content(enabled && region.id in visible)
        }
        item(key = "footer", contentType = "footer") { AppFooterLogo() }
    }
}

/** Route负责订阅，叶子组件不感知VM/Repository；根Tab由Native Shell唯一决定。 */
@Composable
fun NativeDashboardRoute(
    tab: Int,
    authenticated: Boolean,
    epoch: Long,
    coordinator: DashboardCoordinatorViewModel,
    home: HomePageViewModel,
    wealth: WealthPageViewModel,
    member: MemberPageViewModel,
    profile: ProfilePageViewModel,
    onAuthenticate: () -> Unit,
) {
    val visible by coordinator.assetsVisible.collectAsStateWithLifecycle()
    var dialog by remember(epoch) { mutableStateOf<String?>(null) }
    if (!authenticated) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onAuthenticate) { Text("请先登录") }
        }
        return
    }
    // epoch作为组合身份，旧账户保存的列表状态随会话整体销毁。
    key(epoch) {
        val saved = rememberSaveableStateHolder()
        saved.SaveableStateProvider("$epoch:$tab") {
            val open: (String) -> Unit = {
                dialog = it
                com.entry.lib.log.MLog.i("EntryTask.NativeDashboard", "entry.opened tabId=$tab")
            }
            when (tab) {
                1 -> {
                    val content by home.content.state.collectAsStateWithLifecycle()
                    val unread by home.unread.state.collectAsStateWithLifecycle()
                    val products by home.products.state.collectAsStateWithLifecycle()
                    val category by home.category.collectAsStateWithLifecycle()
                    HomeScreen(
                        content,
                        unread,
                        products,
                        category,
                        dialog == null,
                        home::refresh,
                        home::select,
                        open,
                    )
                }
                2 -> {
                    val content by wealth.content.state.collectAsStateWithLifecycle()
                    val assets by wealth.assets.state.collectAsStateWithLifecycle()
                    val products by wealth.products.state.collectAsStateWithLifecycle()
                    val category by wealth.category.collectAsStateWithLifecycle()
                    WealthScreen(
                        content,
                        assets,
                        products,
                        category,
                        visible,
                        dialog == null,
                        wealth::refresh,
                        wealth::select,
                        coordinator::toggle,
                        open,
                    )
                }
                3 -> {
                    val content by member.content.state.collectAsStateWithLifecycle()
                    val identity by member.identity.state.collectAsStateWithLifecycle()
                    val checkin by member.checkin.state.collectAsStateWithLifecycle()
                    val operation by member.submission.collectAsStateWithLifecycle()
                    MemberScreen(
                        content,
                        identity,
                        checkin,
                        operation,
                        visible,
                        dialog == null,
                        member::refresh,
                        member::submit,
                        open,
                    )
                }
                4 -> {
                    val identity by profile.identity.state.collectAsStateWithLifecycle()
                    val assets by profile.assets.state.collectAsStateWithLifecycle()
                    val financial by profile.financial.state.collectAsStateWithLifecycle()
                    ProfileScreen(
                        identity,
                        assets,
                        financial,
                        visible,
                        profile::refresh,
                        coordinator::toggle,
                        open,
                    )
                }
            }
        }
    }
    DemoActionSheet(dialog) { dialog = null }
}

@Composable
private fun HomeTopBar(placeholder: String, unread: Int?, open: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFFCF0E5)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction("scan", "扫一扫", { open("扫一扫") })
        Row(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFFFFF9F4))
                .clickable { open("搜索") }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BankIcon("search", Modifier.size(20.dp))
            Text(placeholder, fontSize = 12.sp, maxLines = 1, color = Muted)
        }
        Box {
            IconAction(
                "message",
                if (unread == null) "消息未读数暂不可用" else "消息，${unread}条未读",
                { open("消息中心") },
            )
            if (unread != null && unread > 0)
                Text(
                    if (unread > 99) "99+" else "$unread",
                    Modifier.align(Alignment.TopEnd)
                        .background(Brand, RoundedCornerShape(8.dp))
                        .padding(horizontal = 3.dp),
                    color = Color.White,
                    fontSize = 9.sp,
                )
        }
        Box(
            Modifier.size(48.dp)
                .clickable { open("在线客服") }
                .semantics {
                    contentDescription = "在线客服"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Art("advisor-avatar", Modifier.size(32.dp))
        }
    }
}

/** 首页按原型的完整内容顺序组装，Logo自然位于最后，产品失败不影响菜单。 */
@Composable
private fun HomeScreen(
    content: RegionState<HomeContent>,
    unread: RegionState<Int>,
    products: RegionState<ProductPage>,
    category: String,
    enabled: Boolean,
    retry: () -> Unit,
    select: (String) -> Unit,
    open: (String) -> Unit,
) {
    val data = content.data
    val sections = data?.sections.orEmpty()
    BankPage(
        listOf(
            RootRegion("home-top") {
                HomeTopBar(data?.placeholder ?: "搜索产品、服务", unread.data, open)
                RegionStateView(unread, retry)
            },
            RootRegion("home-quick") {
                RegionStateView(content, retry)
                StandardGridMenu(data?.quick.orEmpty(), 4, gold = true, onSelect = open)
            },
            RootRegion("home-banner") { visible ->
                BannerCarousel(data?.banners.orEmpty(), visible, open)
            },
            RootRegion("home-services") {
                BankCard { StandardGridMenu(data?.services.orEmpty(), 5, onSelect = open) }
            },
            RootRegion("home-shortcuts") { HomeShortcutRow(data?.shortcuts.orEmpty(), open) },
        ) +
            sections.take(1).map { section ->
                RootRegion("home-section-${section.id}") { HomeEditorialSection(section, open) }
            } +
            listOf(
                RootRegion("home-products") {
                    BankCard(color = Color(0xFFFFF9F3)) {
                        SectionTitle("财富精选")
                        Caption("把握市场动向 优中选优")
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            data?.categories.orEmpty().forEach { item ->
                                TextButton(
                                    onClick = { select(item.id) },
                                    modifier = Modifier.semantics { selected = category == item.id },
                                ) {
                                    Text(item.title, color = if (category == item.id) Gold else Ink)
                                }
                            }
                        }
                        RegionStateView(products, retry)
                        if (products.data?.items?.isEmpty() == true) Caption("该分类暂无产品")
                        products.data?.items.orEmpty().forEach { FinancialProductCard(it, open) }
                        TextButton(onClick = { open("查看更多产品") }) { Text("查看更多产品 ›", color = Muted) }
                    }
                }
            ) +
            sections.drop(1).map { section ->
                RootRegion("home-section-${section.id}") { HomeEditorialSection(section, open) }
            },
        enabled,
    )
}

@Composable
private fun WealthScreen(
    content: RegionState<WealthContent>,
    assets: RegionState<AssetOverview>,
    products: RegionState<ProductPage>,
    category: String,
    visible: Boolean,
    enabled: Boolean,
    retry: () -> Unit,
    select: (String) -> Unit,
    toggle: () -> Unit,
    open: (String) -> Unit,
) {
    BankPage(
        listOf(
            RootRegion("wealth-assets") {
                SectionTitle("财富")
                AssetPrivacyCard(assets.data, visible, toggle)
                RegionStateView(assets, retry)
            },
            RootRegion("wealth-market") { shown ->
                MarketTickerCarousel(content.data?.markets.orEmpty(), shown, open)
            },
            RootRegion("wealth-services") {
                RegionStateView(content, retry)
                StandardGridMenu(content.data?.services.orEmpty(), 5, onSelect = open)
            },
            RootRegion("wealth-category") {
                BankCard {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        content.data?.categories.orEmpty().forEach { item ->
                            TextButton(
                                onClick = { select(item.id) },
                                modifier = Modifier.semantics { selected = category == item.id },
                            ) {
                                Text(
                                    item.title,
                                    color = if (category == item.id) Gold else Ink,
                                    fontWeight =
                                        if (category == item.id) FontWeight.Bold
                                        else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    RegionStateView(products, retry)
                    products.data?.let { data ->
                        Caption("当前分类共${data.total}项 · 展示最多3项")
                        if (data.items.isEmpty()) Caption("该分类暂无产品")
                        data.items.forEach { item ->
                            Text(item.title, color = Ink, fontSize = 14.sp)
                            Caption("${item.term} · ${item.risk}")
                        }
                    }
                }
            },
        ),
        enabled,
    )
}

@Composable
private fun VipProfileCard(data: Identity?, visible: Boolean, open: (String) -> Unit) {
    BankCard(color = Color(0xFF159BC5)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BankIcon("crown", Modifier.size(42.dp), Color(0xFFF6DCA0))
            Text(
                "R${data?.level ?: "—"} ${data?.tierName ?: "中信红会员"}",
                Modifier.weight(1f),
                color = Color(0xFFFFF3C3),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = { open("全部权益") }) {
                Text("全部权益 ›", color = Color(0xFFFFF0CF), fontSize = 12.sp)
            }
        }
        Box(
            Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF9BD5E9)).semantics {
                contentDescription = if (visible) "会员升级进度" else "会员升级进度已隐藏"
            }
        ) {
            if (visible)
                Box(
                    Modifier.fillMaxWidth(data?.progress?.fraction ?: 0f)
                        .height(2.dp)
                        .background(Color(0xFFFFF3C3))
                )
        }
        Caption("当前月日均金融资产 · 演示口径", Color(0xFFE1F6FF))
        MoneyText(data?.progress?.current, visible, color = Color(0xFFFFF3C3))
        if (data?.progress?.top == true) Caption("已达最高演示等级", Color.White)
    }
}

@Composable
private fun InteractiveTaskCard(
    data: CheckinStatus?,
    operation: MemberPageViewModel.Operation,
    submit: () -> Unit,
) {
    BankCard(color = Color(0xFFFFF8F4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "连续签到赢好礼，已连签${data?.consecutive ?: "—"}天",
                Modifier.weight(1f),
                color = Ink,
                fontSize = 14.sp,
            )
            Button(
                onClick = submit,
                enabled = data != null && !data.checked && !operation.submitting,
                colors = ButtonDefaults.buttonColors(containerColor = Brand),
                shape = RoundedCornerShape(3.dp),
            ) {
                Text(
                    if (operation.submitting) "提交中"
                    else if (data?.checked == true) "今日已签到" else "立即签到",
                    fontSize = 12.sp,
                )
            }
        }
        Caption(data?.rule ?: "正在加载签到规则")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            data?.calendar.orEmpty().forEach { day ->
                Column(
                    Modifier.weight(1f)
                        .background(Color.White)
                        .padding(vertical = 14.dp)
                        .semantics {
                            contentDescription = "${day.date}，${if (day.checked) "已签到" else "未签到"}"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (day.checked) "已签" else "空", color = Gold, fontSize = 12.sp)
                    Text(day.date.takeLast(5), fontSize = 9.sp, color = Muted)
                }
            }
        }
        if (operation.message.isNotEmpty())
            Text(
                operation.message,
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = Muted,
                fontSize = 12.sp,
            )
    }
}

@Composable
private fun MemberScreen(
    content: RegionState<MemberContent>,
    identity: RegionState<Identity>,
    checkin: RegionState<CheckinStatus>,
    operation: MemberPageViewModel.Operation,
    visible: Boolean,
    enabled: Boolean,
    retry: () -> Unit,
    submit: () -> Unit,
    open: (String) -> Unit,
) {
    BankPage(
        listOf(
            RootRegion("member-vip") {
                SectionTitle("会员")
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF159BC5))
                        .padding(bottom = 12.dp)
                ) {
                    VipProfileCard(identity.data, visible, open)
                    RegionStateView(identity, retry)
                    BankCard(Modifier.padding(horizontal = 12.dp)) {
                        RegionStateView(content, retry)
                        StandardGridMenu(
                            content.data?.benefits.orEmpty(),
                            4,
                            blue = true,
                            onSelect = open,
                        )
                    }
                }
            },
            RootRegion("member-task") {
                SectionTitle("会员专属活动")
                InteractiveTaskCard(checkin.data, operation, submit)
                RegionStateView(checkin, retry)
            },
        ),
        enabled,
    )
}

@Composable
private fun UserCardHeader(data: Identity?, open: (String) -> Unit) {
    BankCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Art("profile-avatar", Modifier.size(57.dp))
            Column {
                Text(
                    data?.nickname ?: "正在加载资料",
                    color = Ink,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Caption(data?.carbon ?: "低碳", Color(0xFF57A787))
                Caption("R${data?.level ?: "—"} ${data?.tierName.orEmpty()}")
            }
        }
        Row {
            listOf("银行卡", "待办", "卡券", "积分", "能量值").forEachIndexed { index, title ->
                Column(
                    Modifier.weight(1f).heightIn(min = 62.dp).clickable { open(title) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        data?.counts?.getOrNull(index)?.toString() ?: "—",
                        color = Ink,
                        fontSize = 21.sp,
                    )
                    Text(title, color = Ink, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MonthlyCashflowCard(data: FinancialOverview?, visible: Boolean) {
    BankCard {
        SectionTitle("月度收支")
        Caption("${data?.month ?: "—"} · 本地演示")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                MoneyText(data?.income, visible)
                Caption("本月收入")
            }
            Column {
                MoneyText(data?.expense, visible)
                Caption("本月支出")
            }
        }
        Box(
            Modifier.fillMaxWidth().height(5.dp).background(Color(0xFFE8E4E0)).semantics {
                contentDescription = if (visible) "本地账户暂无收支" else "收支比例已隐藏"
            }
        ) {
            if (visible && data?.incomeShare != null)
                Box(Modifier.fillMaxWidth(data.incomeShare).height(5.dp).background(Gold))
        }
    }
}

@Composable
private fun CreditBillCard(data: CreditBill?, visible: Boolean, open: (String) -> Unit) {
    BankCard {
        SectionTitle("信用卡账单")
        if (data?.hasCard == true) {
            MoneyText(data.unpaid, visible)
            Caption("到期日 ${if (visible) data.dueDate ?: "—" else "已隐藏"}")
        } else Caption(if (data == null) "账单信息暂不可用" else "尚未绑定信用卡，暂无账单")
        TextButton(onClick = { open("信用卡服务") }) { Text("信用卡服务 ›", color = Gold) }
    }
}

@Composable
private fun LoanSummaryCard(data: LoanOffer?, visible: Boolean, open: (String) -> Unit) {
    BankCard {
        SectionTitle("我的借钱")
        MoneyText(data?.limit, visible)
        Caption("营销演示上限 · 尚未授信")
        Text(data?.rate ?: "—", color = Ink, fontSize = 21.sp)
        Caption("示例年利率")
        TextButton(onClick = { open("借款服务") }) { Text("了解借款服务 ›", color = Gold) }
    }
}

@Composable
private fun ProfileScreen(
    identity: RegionState<Identity>,
    assets: RegionState<AssetOverview>,
    financial: RegionState<FinancialOverview>,
    visible: Boolean,
    retry: () -> Unit,
    toggle: () -> Unit,
    open: (String) -> Unit,
) {
    BankPage(
        listOf(
            RootRegion("profile-identity") {
                UserCardHeader(identity.data, open)
                RegionStateView(identity, retry)
            },
            RootRegion("profile-assets") {
                AssetPrivacyCard(assets.data, visible, toggle)
                RegionStateView(assets, retry)
            },
            RootRegion("profile-cashflow") {
                MonthlyCashflowCard(financial.data, visible)
                RegionStateView(financial, retry)
            },
            RootRegion("profile-credit") { CreditBillCard(financial.data?.bill, visible, open) },
            RootRegion("profile-loan") { LoanSummaryCard(financial.data?.loan, visible, open) },
        ),
        true,
    )
}

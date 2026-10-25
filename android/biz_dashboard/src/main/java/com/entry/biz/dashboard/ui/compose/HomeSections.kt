package com.entry.biz.dashboard.ui.compose

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.entry.biz.dashboard.domain.pages.*

/** 首页与其他根页共用菜单内核；横向快捷条不与外层纵向列表争抢滚动。 */
@Composable
internal fun HomeShortcutRow(items: List<MenuEntry>, open: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            OutlinedButton(
                onClick = { open(item.title) },
                shape = RoundedCornerShape(5.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text(item.title, fontSize = 12.sp, color = Ink)
            }
        }
    }
}

/** 金融卡片集中处理指标、期限和风险文字，数值来自Repository的十进制转换。 */
@Composable
internal fun FinancialProductCard(item: ProductBrief, open: (String) -> Unit) {
    BankCard(Modifier.clickable { open(item.title) }) {
        Row {
            Text(item.title, Modifier.weight(1f), color = Ink, fontSize = 15.sp, maxLines = 1)
            Caption(item.distribution, Gold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.ratio,
                    color = if (item.ratio.startsWith("-")) Color(0xFF268262) else Brand,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Caption(item.indicator)
            }
            Column(Modifier.weight(1f)) {
                Text(item.term, color = Ink, fontSize = 17.sp)
                Caption("${item.minimum} · ${item.risk}")
            }
        }
        Text(
            item.riskText,
            Modifier.fillMaxWidth().background(Color(0xFFF5F8FA)).padding(8.dp),
            color = Muted,
            fontSize = 10.sp,
        )
    }
}

/** 有限运营布局：海报、双列卡、菜单、资讯；不接受任意远端组件或脚本。 */
@Composable
internal fun HomeEditorialSection(section: HomeSection, open: (String) -> Unit) {
    BankCard(color = Color(0xFFFFF9F3)) {
        if (section.title.isNotEmpty()) {
            SectionTitle(section.title)
            if (section.subtitle.isNotEmpty()) Caption(section.subtitle)
        }
        when (section.style) {
            "menu" ->
                StandardGridMenu(
                    section.entries.map { MenuEntry(it.id, it.title, it.icon) },
                    if (section.entries.size == 4) 4 else 3,
                    onSelect = open,
                )
            "banner" ->
                section.entries.forEach { tile ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { open(tile.title) }
                            .padding(vertical = 8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                tile.title,
                                color = Gold,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Caption(tile.subtitle)
                        }
                        Art(tile.art, Modifier.size(80.dp))
                    }
                }
            "cards" ->
                section.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { tile ->
                            BankCard(
                                Modifier.weight(1f).clickable { open(tile.title) },
                                if (tile.tone == "blue") Color(0xFFF0F3FD) else Color(0xFFFFF3EE),
                            ) {
                                BankIcon(tile.icon, color = Gold)
                                Text(tile.title, color = Gold, fontSize = 16.sp)
                                Caption(tile.subtitle)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            "news" ->
                section.entries.forEach { tile ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { open(tile.title) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tile.title, color = Ink, fontSize = 15.sp)
                            Caption(tile.subtitle)
                        }
                        BankIcon(tile.icon, Modifier.size(42.dp), Gold)
                    }
                    HorizontalDivider(color = Color(0xFFF0ECE7))
                }
        }
    }
}

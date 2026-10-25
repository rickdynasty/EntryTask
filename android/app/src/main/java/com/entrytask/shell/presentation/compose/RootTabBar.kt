package com.entrytask.shell.presentation.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.entrytask.R

/** 唯一Native根底栏；点击只发意图，不在组件内保存导航选择。 */
@Composable
fun RootTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("首页", "财富", "会员", "我的")
    val icons =
        listOf(
            R.drawable.ic_tab_home,
            R.drawable.ic_tab_trade,
            R.drawable.ic_tab_message,
            R.drawable.ic_tab_profile,
        )
    Column {
        HorizontalDivider(thickness = .5.dp, color = Color(0xFFDDDDDD))
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            labels.forEachIndexed { index, title ->
                val color = if (selected == index + 1) Color(0xFFB78C5D) else Color(0xFF292623)
                Column(
                    Modifier.weight(1f)
                        .clickable { onSelect(index + 1) }
                        .semantics {
                            this.selected = selected == index + 1
                            role = Role.Tab
                        }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(painterResource(icons[index]), null, Modifier.size(25.dp), tint = color)
                    Text(title, color = color, fontSize = 12.sp)
                }
            }
        }
    }
}

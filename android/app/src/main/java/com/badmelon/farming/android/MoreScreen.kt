package com.badmelon.farming.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MoreScreen(onNavigate: (Dest) -> Unit) {
    val entries = listOf(
        Triple(Dest.Knowledge, "知识库", "上传/查看知识文档"),
        Triple(Dest.Memory, "记忆库", "长期记忆的查看与维护"),
        Triple(Dest.Mcp, "MCP 服务", "查看、测试、启停 MCP"),
        Triple(Dest.Skills, "技能中心", "查看、启停技能"),
        Triple(Dest.Settings, "设置", "服务器地址、API Key、主题"),
        Triple(Dest.About, "关于", "版本与使用说明")
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("更多", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "生活助理 Harness",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        "对话、监控、任务、知识与记忆，都在这里。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
        entries.forEach { entry ->
            item {
                AppCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(entry.first) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.second, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                entry.third,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

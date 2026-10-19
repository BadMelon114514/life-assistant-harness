package com.badmelon.farming.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun MonitorScreen() {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        try {
            data = Api.getObject("/api/monitor/overview")
            error = null
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching { load() }
            delay(5000)
        }
    }
    LaunchedEffect(Unit) {
        Events.monitorTick.collect { runCatching { load() } }
    }

    val summary = data?.optJSONObject("summary") ?: JSONObject()
    val teammates = data?.optJSONArray("teammates") ?: JSONArray()
    val logs = data?.optJSONArray("logs") ?: JSONArray()
    val teammateList = (0 until teammates.length()).mapNotNull { teammates.optJSONObject(it) }
    val logList = (0 until logs.length()).mapNotNull { logs.optJSONObject(it) }.take(40)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("进程监控", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }
        }
        error?.let { message ->
            item {
                Text("⚠️ " + message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCell("成员总数", summary.num("total").toString(), Modifier.weight(1f))
                    StatCell("工作中", summary.num("working").toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCell("空闲", summary.num("idle").toString(), Modifier.weight(1f))
                    StatCell("平均进度", summary.num("avgProgress").toString() + "%", Modifier.weight(1f))
                }
            }
        }
        item { SectionTitle("团队成员（" + teammateList.size + "）") }
        if (teammateList.isEmpty()) {
            item { EmptyState("暂无团队成员") }
        }
        items(teammateList) { mate ->
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            mate.str("name"),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        val status = mate.str("status")
                        StatusPill(statusLabel(status), statusColor(status))
                    }
                    Text(
                        mate.str("role"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    val progress = mate.num("progress").coerceIn(0, 100)
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(6.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Row(Modifier.padding(top = 6.dp)) {
                        Text("进度 " + progress + "%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(formatTime(mate.str("heartbeatAt")).takeLast(8), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val current = mate.str("currentTask")
                    if (current.isNotBlank()) {
                        Text(
                            "当前：" + current,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp),
                            maxLines = 2
                        )
                    }
                }
            }
        }
        item { SectionTitle("最近日志") }
        if (logList.isEmpty()) {
            item { EmptyState("暂无日志") }
        }
        items(logList) { log ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatTime(log.str("createdAt")).takeLast(8),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(52.dp)
                )
                Text(
                    "[" + log.str("source") + "]",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 5.dp)
                )
                Text(
                    log.str("message"),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column {
            Text(
                value,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

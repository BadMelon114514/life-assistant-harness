package com.badmelon.farming.android

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun TasksScreen() {
    var tasks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("all") }
    var refreshKey by remember { mutableStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val arr = Api.getArray("/api/tasks")
            tasks = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            error = null
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(refreshKey) { load() }

    fun reload() {
        refreshKey += 1
    }

    fun runAction(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                block()
                error = null
            } catch (e: Exception) {
                error = e.message ?: "操作失败"
            } finally {
                busy = false
                reload()
            }
        }
    }

    val filtered = tasks.filter { task ->
        filter == "all" || task.str("status") == filter
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("任务看板", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (loading || busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }
            error?.let { message ->
                item { Text("⚠️ " + message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chips = listOf(
                        "all" to "全部",
                        "pending" to "待处理",
                        "in_progress" to "进行中",
                        "completed" to "已完成"
                    )
                    chips.forEach { pair ->
                        FilterChip(
                            selected = filter == pair.first,
                            onClick = { filter = pair.first },
                            label = { Text(pair.second, fontSize = 12.sp) }
                        )
                    }
                }
            }
            if (filtered.isEmpty() && !loading) {
                item { EmptyState("没有符合条件的任务") }
            }
            items(filtered) { task ->
                TaskCard(
                    task = task,
                    busy = busy,
                    onClaim = {
                        val key = task.str("taskKey")
                        runAction {
                            Api.post(
                                "/api/tasks/" + key + "/claim",
                                JSONObject().put("owner", "安卓助手")
                            )
                        }
                    },
                    onProgress = {
                        val key = task.str("taskKey")
                        val current = task.num("progress")
                        runAction {
                            Api.post(
                                "/api/tasks/" + key + "/progress",
                                JSONObject().put("progress", (current + 10).coerceAtMost(100))
                                    .put("status", "in_progress")
                            )
                        }
                    },
                    onComplete = {
                        val key = task.str("taskKey")
                        runAction {
                            Api.post(
                                "/api/tasks/" + key + "/complete",
                                JSONObject().put("result", "由安卓端标记完成")
                            )
                        }
                    },
                    onDelete = { deleting = task }
                )
            }
        }
        FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "新建任务")
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建任务") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("标题") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("描述（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val taskTitle = title.trim()
                        if (taskTitle.isNotEmpty()) {
                            scope.launch {
                                busy = true
                                try {
                                    Api.post(
                                        "/api/tasks",
                                        JSONObject().put("title", taskTitle)
                                            .put("description", description.trim())
                                            .put("blockedBy", JSONArray())
                                    )
                                    title = ""
                                    description = ""
                                    showCreate = false
                                    reload()
                                } catch (e: Exception) {
                                    error = e.message ?: "创建失败"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消") }
            }
        )
    }

    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除任务") },
            text = { Text("确定删除「" + task.str("title") + "」？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val key = task.str("taskKey")
                        deleting = null
                        runAction { Api.delete("/api/tasks/" + key) }
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TaskCard(
    task: JSONObject,
    busy: Boolean,
    onClaim: () -> Unit,
    onProgress: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val status = task.str("status")
    AppCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.str("title"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(statusLabel(status), statusColor(status))
            }
            val desc = task.str("description")
            if (desc.isNotBlank()) {
                Text(
                    desc,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 3
                )
            }
            val progress = task.num("progress").coerceIn(0, 100)
            if (status == "in_progress") {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(6.dp)
                )
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "负责人：" + (task.str("owner").ifBlank { "未认领" }),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(formatTime(task.str("updatedAt")), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val result = task.str("result")
            if (result.isNotBlank()) {
                Text(
                    "结果：" + result,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 2
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (status) {
                    "pending" -> TextButton(onClick = onClaim, enabled = !busy) { Text("认领", fontSize = 12.sp) }
                    "in_progress" -> {
                        TextButton(onClick = onProgress, enabled = !busy) { Text("+10%", fontSize = 12.sp) }
                        TextButton(onClick = onComplete, enabled = !busy) { Text("完成", fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, enabled = !busy) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

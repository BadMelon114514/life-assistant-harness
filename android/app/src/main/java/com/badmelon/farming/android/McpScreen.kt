package com.badmelon.farming.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import org.json.JSONObject

@Composable
fun McpScreen() {
    var servers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val arr = Api.getArray("/api/mcp")
            servers = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            error = null
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(refreshKey) { load() }

    fun runAction(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                block()
            } catch (e: Exception) {
                error = e.message ?: "操作失败"
            } finally {
                busy = false
                refreshKey += 1
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MCP 服务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (loading || busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }
            message?.let { text ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(10.dp)
                            )
                            TextButton(onClick = { message = null }) { Text("关闭", fontSize = 11.sp) }
                        }
                    }
                }
            }
            error?.let { text ->
                item { Text("⚠️ " + text, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
            if (servers.isEmpty() && !loading) {
                item { EmptyState("暂无 MCP 服务") }
            }
            items(servers) { server ->
                McpCard(
                    server = server,
                    busy = busy,
                    onToggle = {
                        runAction {
                            Api.post("/api/mcp/" + server.num("id") + "/toggle", JSONObject())
                        }
                    },
                    onTest = {
                        runAction {
                            val response = Api.post("/api/mcp/" + server.num("id") + "/test", JSONObject())
                            message = (if (response.optBoolean("success")) "✅ " else "❌ ") +
                                response.optString("message", "测试完成")
                        }
                    },
                    onDelete = { deleting = server }
                )
            }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "新增 MCP")
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("新增 MCP 服务") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("端点 URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("描述（可选）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "新增后默认类型为 http，可在服务卡片中测试连通性。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank() && endpoint.isNotBlank()) {
                            scope.launch {
                                busy = true
                                try {
                                    Api.post(
                                        "/api/mcp",
                                        JSONObject().put("name", name.trim())
                                            .put("serverType", "http")
                                            .put("endpoint", endpoint.trim())
                                            .put("description", description.trim())
                                            .put("config", "{}")
                                    )
                                    name = ""
                                    endpoint = ""
                                    description = ""
                                    showAdd = false
                                    refreshKey += 1
                                } catch (e: Exception) {
                                    error = e.message ?: "保存失败"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
        )
    }

    deleting?.let { server ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除 MCP 服务") },
            text = { Text("确定删除「" + server.str("name") + "」？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = server.num("id")
                        deleting = null
                        runAction { Api.delete("/api/mcp/" + id) }
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun McpCard(
    server: JSONObject,
    busy: Boolean,
    onToggle: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    val status = server.str("status")
    AppCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    server.str("name"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                StatusPill(statusLabel(status), statusColor(status))
            }
            Text(
                server.str("serverType") + " · " + server.str("endpoint"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 2
            )
            val desc = server.str("description")
            if (desc.isNotBlank()) {
                Text(
                    desc,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggle, enabled = !busy) {
                    Text(if (status == "active") "停用" else "启用", fontSize = 12.sp)
                }
                TextButton(onClick = onTest, enabled = !busy) { Text("测试", fontSize = 12.sp) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, enabled = !busy) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

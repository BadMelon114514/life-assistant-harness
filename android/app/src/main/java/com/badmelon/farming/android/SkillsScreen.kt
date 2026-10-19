package com.badmelon.farming.android

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import org.json.JSONObject

@Composable
fun SkillsScreen() {
    var skills by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("通用") }
    var description by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val arr = Api.getArray("/api/skills")
            skills = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
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
                    Text("技能中心", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (loading || busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }
            error?.let { text ->
                item { Text("⚠️ " + text, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
            if (skills.isEmpty() && !loading) {
                item { EmptyState("暂无技能") }
            }
            items(skills) { skill ->
                AppCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selected = skill }
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                skill.str("name"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            StatusPill(
                                if (skill.optBoolean("enabled", true)) "已启用" else "已停用",
                                if (skill.optBoolean("enabled", true)) statusColor("active") else statusColor("inactive")
                            )
                        }
                        Text(
                            skill.str("category").ifBlank { "通用" },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        val desc = skill.str("description")
                        if (desc.isNotBlank()) {
                            Text(
                                desc,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 2
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    runAction {
                                        Api.post("/api/skills/" + skill.num("id") + "/toggle", JSONObject())
                                    }
                                },
                                enabled = !busy
                            ) {
                                Text(if (skill.optBoolean("enabled", true)) "停用" else "启用", fontSize = 12.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { deleting = skill }, enabled = !busy) {
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
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "新增技能")
        }
    }

    selected?.let { skill ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(skill.str("name")) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(skill.str("category") + " · " + skill.str("description"), fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    Text(skill.str("content"), fontSize = 13.sp, lineHeight = 20.sp)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("关闭") } },
            dismissButton = {
                TextButton(
                    onClick = {
                        val id = skill.num("id")
                        selected = null
                        runAction { Api.delete("/api/skills/" + id) }
                    }
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("新增技能") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 430.dp)
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
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("分类") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("简介") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("技能指令内容") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank() && content.isNotBlank()) {
                            scope.launch {
                                busy = true
                                try {
                                    Api.post(
                                        "/api/skills",
                                        JSONObject().put("name", name.trim())
                                            .put("category", category.trim().ifBlank { "通用" })
                                            .put("description", description.trim())
                                            .put("content", content.trim())
                                            .put("enabled", true)
                                    )
                                    name = ""
                                    description = ""
                                    content = ""
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

    deleting?.let { skill ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除技能") },
            text = { Text("确定删除「" + skill.str("name") + "」？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = skill.num("id")
                        deleting = null
                        runAction { Api.delete("/api/skills/" + id) }
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
}

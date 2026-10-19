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
import androidx.compose.material.icons.filled.Search
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
import java.net.URLEncoder

@Composable
fun KnowledgeScreen() {
    var docs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var keyword by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val path = if (submitted.isBlank()) {
                "/api/knowledge"
            } else {
                "/api/knowledge?keyword=" + URLEncoder.encode(submitted, "UTF-8")
            }
            val arr = Api.getArray(path)
            docs = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            error = null
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(refreshKey) { load() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("知识库", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (loading || busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("搜索文档标题或摘要", fontSize = 13.sp) },
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            submitted = keyword.trim()
                            loading = true
                            refreshKey += 1
                        }
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                }
            }
            error?.let { message ->
                item { Text("⚠️ " + message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
            if (docs.isEmpty() && !loading) {
                item { EmptyState(if (submitted.isBlank()) "知识库还没有文档" else "没有匹配的文档") }
            }
            items(docs) { doc ->
                AppCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selected = doc }
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                doc.str("fileName").ifBlank { doc.str("docKey") },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                doc.str("parseStatus").ifBlank { doc.str("fileType") },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val docTags = doc.str("tags")
                        if (docTags.isNotBlank()) {
                            Text(
                                docTags,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp),
                                maxLines = 1
                            )
                        }
                        val preview = doc.str("summary").ifBlank { doc.str("content") }
                        if (preview.isNotBlank()) {
                            Text(
                                preview.take(120),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 2
                            )
                        }
                        Text(
                            formatTime(doc.str("createdAt")),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
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
            Icon(Icons.Filled.Add, contentDescription = "新增文档")
        }
    }

    selected?.let { doc ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(doc.str("fileName").ifBlank { "文档详情" }) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("类型：" + doc.str("fileType") + " · " + doc.str("parseStatus"), fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    Text(doc.str("content"), fontSize = 13.sp, lineHeight = 20.sp)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("关闭") } },
            dismissButton = {
                TextButton(
                    onClick = {
                        val key = doc.str("docKey")
                        selected = null
                        scope.launch {
                            try {
                                Api.delete("/api/knowledge/" + URLEncoder.encode(key, "UTF-8"))
                                refreshKey += 1
                            } catch (e: Exception) {
                                error = e.message ?: "删除失败"
                            }
                        }
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
            title = { Text("新增文本文档") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("文件名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("标签（可选，逗号分隔）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("内容") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (fileName.isNotBlank() && content.isNotBlank()) {
                            scope.launch {
                                busy = true
                                try {
                                    Api.post(
                                        "/api/knowledge/save-text",
                                        JSONObject().put("fileName", fileName.trim())
                                            .put("content", content)
                                            .put("tags", tags.trim())
                                    )
                                    fileName = ""
                                    content = ""
                                    tags = ""
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
}

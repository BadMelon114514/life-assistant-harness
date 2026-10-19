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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun SettingsScreen(onThemeChange: (String) -> Unit) {
    var baseUrl by remember { mutableStateOf(Prefs.baseUrl) }
    var connectionMsg by remember { mutableStateOf<String?>(null) }
    var apiKey by remember { mutableStateOf("") }
    var keyMsg by remember { mutableStateOf<String?>(null) }
    var themeMode by remember { mutableStateOf(Prefs.themeMode) }
    var showShutdownConfirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("后端连接", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("服务器地址") },
                        placeholder = { Text("http://10.0.2.2:8080") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(Modifier.padding(top = 8.dp)) {
                        Button(
                            onClick = {
                                Prefs.baseUrl = baseUrl
                                baseUrl = Prefs.baseUrl
                                scope.launch {
                                    busy = true
                                    connectionMsg = try {
                                        Api.testConnection()
                                    } catch (e: Exception) {
                                        "❌ " + (e.message ?: "连接失败")
                                    }
                                    busy = false
                                }
                            },
                            enabled = !busy
                        ) { Text("保存并测试") }
                    }
                    connectionMsg?.let { message ->
                        Text(
                            message,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp),
                            color = if (message.startsWith("✅") || message.startsWith("连接成功")) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    Text(
                        "真机访问请填写电脑的局域网 IP，例如 http://192.168.1.10:8080",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("模型 API Key", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("DeepSeek API Key（仅保存到后端）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(Modifier.padding(top = 8.dp)) {
                        Button(
                            onClick = {
                                if (apiKey.isNotBlank()) {
                                    scope.launch {
                                        busy = true
                                        keyMsg = try {
                                            Api.post("/api/settings/api-key", JSONObject().put("value", apiKey.trim()))
                                            apiKey = ""
                                            "✅ API Key 已保存"
                                        } catch (e: Exception) {
                                            "❌ " + (e.message ?: "保存失败")
                                        }
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy && apiKey.isNotBlank()
                        ) { Text("保存 API Key") }
                    }
                    keyMsg?.let { message ->
                        Text(message, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Text(
                        "密钥保存在后端，不会回显明文。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("外观主题", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { pair ->
                            FilterChip(
                                selected = themeMode == pair.first,
                                onClick = {
                                    themeMode = pair.first
                                    Prefs.themeMode = pair.first
                                    onThemeChange(pair.first)
                                },
                                label = { Text(pair.second, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "危险操作",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "关闭后端服务会同时停止上下文存储服务，网页与手机端将无法继续使用，需要重新启动桌面程序。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    TextButton(
                        onClick = { showShutdownConfirm = true },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("关闭后端服务", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("关于", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("生活助理 Harness · 安卓客户端", fontSize = 13.sp)
                    Text(
                        "原生 Kotlin + Jetpack Compose 客户端，连接桌面版后端。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        "版本 2.0.0",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }

    if (showShutdownConfirm) {
        AlertDialog(
            onDismissRequest = { showShutdownConfirm = false },
            title = { Text("确认关闭后端？") },
            text = { Text("关闭后 8080 与 8765 服务都会停止，手机端将立即断开连接。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShutdownConfirm = false
                        scope.launch {
                            busy = true
                            try {
                                Api.postWithHeaders(
                                    "/api/system/shutdown",
                                    JSONObject(),
                                    mapOf("X-Shutdown-Confirm" to "yes")
                                )
                                Events.backendDown.value = true
                            } catch (e: Exception) {
                                // 服务关闭时连接中断属于正常现象
                                Events.backendDown.value = true
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("确认关闭", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showShutdownConfirm = false }) { Text("取消") }
            }
        )
    }
}

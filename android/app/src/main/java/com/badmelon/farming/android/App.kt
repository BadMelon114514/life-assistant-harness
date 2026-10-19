package com.badmelon.farming.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Dest(val label: String) {
    Chat("对话"),
    Monitor("监控"),
    Tasks("任务"),
    Knowledge("知识"),
    More("更多"),
    Memory("记忆库"),
    Mcp("MCP 服务"),
    Skills("技能"),
    Settings("设置"),
    About("关于")
}

private val mainTabs = listOf(Dest.Chat, Dest.Monitor, Dest.Tasks, Dest.Knowledge, Dest.More)

private fun iconFor(dest: Dest): ImageVector = when (dest) {
    Dest.Chat -> Icons.Filled.Email
    Dest.Monitor -> Icons.Filled.Refresh
    Dest.Tasks -> Icons.Filled.List
    Dest.Knowledge -> Icons.Filled.Search
    else -> Icons.Filled.Menu
}

@Composable
fun LifeAssistantApp(themeMode: String, onThemeChange: (String) -> Unit) {
    var destName by rememberSaveable { mutableStateOf(Dest.Chat.name) }
    val dest = Dest.valueOf(destName)
    var backendDown by remember { mutableStateOf(false) }
    val chatViewModel = remember { ChatViewModel() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        Events.backendDown.collect { down -> backendDown = down }
    }
    LaunchedEffect(Unit) {
        Events.notifications.collect { pair ->
            snackbarHostState.showSnackbar(pair.first + "：" + pair.second)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            if (Events.backendDown.value) break
            val closed = CompletableDeferred<Unit>()
            val socket = Api.connectMonitor(
                onEvent = { event ->
                    when (event.optString("type")) {
                        "snapshot" -> Events.monitorTick.value = Events.monitorTick.value + 1
                        "assistant_notify" -> Events.notifications.tryEmit(
                            event.optString("title", "提醒") to event.optString("content")
                        )
                        "server_shutdown" -> Events.backendDown.value = true
                    }
                },
                onClosed = { if (!closed.isCompleted) closed.complete(Unit) }
            )
            if (socket == null) {
                delay(5000)
            } else {
                closed.await()
            }
            if (Events.backendDown.value) break
            delay(3000)
        }
    }

    BackHandler(enabled = dest != Dest.Chat) {
        destName = if (mainTabs.contains(dest) && dest != Dest.Chat) {
            Dest.Chat.name
        } else {
            Dest.More.name
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                mainTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = dest == tab,
                        onClick = { destName = tab.name },
                        icon = { Icon(iconFor(tab), contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (backendDown) {
                OfflineScreen()
            } else {
                if (!mainTabs.contains(dest)) {
                    SubHeader(dest.label) { destName = Dest.More.name }
                }
                when (dest) {
                    Dest.Chat -> ChatScreen(chatViewModel)
                    Dest.Monitor -> MonitorScreen()
                    Dest.Tasks -> TasksScreen()
                    Dest.Knowledge -> KnowledgeScreen()
                    Dest.Memory -> MemoryScreen()
                    Dest.Mcp -> McpScreen()
                    Dest.Skills -> SkillsScreen()
                    Dest.Settings -> SettingsScreen(onThemeChange)
                    Dest.More -> MoreScreen { target -> destName = target.name }
                    Dest.About -> AboutScreen()
                }
            }
        }
    }
}

@Composable
private fun SubHeader(label: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("生活助理 Harness", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("安卓客户端 · 版本 2.0.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppCard(Modifier.fillMaxWidth()) {
            Column {
                Text("支持的功能", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "· 流式对话与高危操作确认\n" +
                        "· 任务看板认领 / 推进 / 完成\n" +
                        "· 进程监控与日志实时查看\n" +
                        "· 知识库、记忆库浏览与维护\n" +
                        "· MCP 服务与技能的启停、测试\n" +
                        "· 主题切换、服务器地址配置\n" +
                        "· 定时提醒消息推送（前台）",
                    fontSize = 12.sp,
                    lineHeight = 19.sp
                )
            }
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column {
                Text("连接方式", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "手机与运行桌面版的电脑需在同一局域网。在「设置」中填写电脑的局域网地址（如 http://192.168.1.10:8080）。" +
                        "模拟器可直接使用默认的 10.0.2.2。",
                    fontSize = 12.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun OfflineScreen() {
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("服务已关闭", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "后端服务已停止，手机端暂时无法使用。\n请在电脑上重新启动「生活助理 Harness」桌面程序，然后点击下方按钮重试。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                scope.launch {
                    checking = true
                    message = try {
                        Api.testConnection()
                        Events.backendDown.value = false
                        null
                    } catch (e: Exception) {
                        "❌ " + (e.message ?: "仍无法连接")
                    } finally {
                        checking = false
                    }
                }
            },
            enabled = !checking
        ) {
            Text(if (checking) "连接中…" else "重试连接")
        }
        message?.let { text ->
            Spacer(Modifier.height(10.dp))
            Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

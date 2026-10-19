package com.badmelon.farming.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lastContent = viewModel.messages.lastOrNull()?.content?.length ?: 0

    LaunchedEffect(viewModel.messages.size, lastContent) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "会话 " + viewModel.session,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            TextButton(onClick = { viewModel.refreshHistory() }) { Text("刷新", fontSize = 12.sp) }
            TextButton(onClick = { viewModel.newSession() }) { Text("新对话", fontSize = 12.sp) }
            TextButton(onClick = { viewModel.clearHistory() }) { Text("清空", fontSize = 12.sp) }
        }

        viewModel.error?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "⚠️ " + message,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        viewModel.notice?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(10.dp)
                    )
                    TextButton(onClick = { viewModel.dismissNotice() }) { Text("知道了", fontSize = 11.sp) }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (viewModel.messages.isEmpty()) {
                item { WelcomeCard(onPick = { input = it }) }
            }
            itemsIndexed(viewModel.messages) { index, message ->
                val isStreaming = viewModel.streaming && index == viewModel.messages.size - 1
                MessageBubble(message, isStreaming)
            }
            item { Spacer(Modifier.height(6.dp)) }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息…", fontSize = 14.sp) },
                maxLines = 4,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            if (viewModel.streaming) {
                IconButton(
                    onClick = { viewModel.stop() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "停止", tint = Color.White)
                }
            } else {
                IconButton(
                    onClick = {
                        val text = input
                        if (text.isNotBlank()) {
                            input = ""
                            viewModel.send(text)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "发送", tint = Color.White)
                }
            }
        }
    }

    viewModel.pendingConfirm?.let { pending ->
        val fileOps = listOf("write_file", "delete_file", "run_script", "mkdir")
        val isFileOp = fileOps.contains(pending.op)
        val labels = mapOf(
            "write_file" to "写入/覆盖文件",
            "delete_file" to "删除文件/目录",
            "run_script" to "执行项目内脚本",
            "mkdir" to "创建目录"
        )
        AlertDialog(
            onDismissRequest = { viewModel.resolveConfirm(false) },
            title = { Text("⚠️ 高危操作确认") },
            text = {
                Column {
                    Text(
                        if (isFileOp) "助手请求执行文件操作：" else "助手请求执行高危 MCP 操作：",
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    if (isFileOp) {
                        Text("操作：" + (labels[pending.op] ?: pending.op), fontSize = 13.sp)
                        Text("路径：" + pending.arg1, fontSize = 13.sp)
                        if (pending.arg2.isNotBlank()) {
                            Text(
                                "内容预览：" + pending.arg2.take(300),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text("目标：" + pending.arg1, fontSize = 13.sp)
                        Text("参数：" + pending.arg2.take(300), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "该操作可能修改或删除数据，确认后才会执行。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveConfirm(true) }) {
                    Text("确认执行", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveConfirm(false) }) { Text("取消") }
            }
        )
    }

    viewModel.draft?.let { current ->
        val isSkill = current.type == "skill_draft"
        AlertDialog(
            onDismissRequest = { viewModel.dismissDraft() },
            title = { Text(if (isSkill) "检测到技能草稿" else "检测到 MCP 草稿") },
            text = {
                Column {
                    Text("名称：" + current.payload.optString("name", "(未命名)"), fontWeight = FontWeight.Medium)
                    val description = current.payload.optString("description", "")
                    if (description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(description.take(400), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "导入前会进行安全扫描，风险内容会被拒绝。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.importDraft() }) { Text("一键导入") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDraft() }) { Text("忽略") }
            }
        )
    }
}

@Composable
private fun WelcomeCard(onPick: (String) -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column {
            Text("你好，我是生活助理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "可以聊聊天、查天气、记事情、整理文件、设定提醒。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            val suggestions = listOf(
                "现在几点了？我在哪个城市？",
                "帮我记住：我喜欢喝拿铁",
                "今天有什么热点？",
                "整理一下本周的任务"
            )
            suggestions.forEach { suggestion ->
                TextButton(onClick = { onPick(suggestion) }) {
                    Text(suggestion, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMsg, streaming: Boolean) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("助", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                when {
                    message.content.isBlank() && streaming -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text("  正在思考…", fontSize = 13.sp)
                    }
                    message.content.isBlank() -> Text("…", fontSize = 13.sp)
                    isUser -> Text(message.content, fontSize = 14.sp, lineHeight = 20.sp)
                    else -> MarkdownText(
                        text = if (streaming) message.content + " ▍" else message.content,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

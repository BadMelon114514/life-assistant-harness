package com.badmelon.farming.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Box(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
fun LoadingRow(text: String = "加载中…") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text("  " + text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
fun StatusPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val parts = remember(text) { text.split("```") }
    Column(modifier = modifier) {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                val code = part.trim('\n')
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp)
                    )
                }
            } else {
                val blocks = part.trim().split("\n")
                blocks.forEach { line ->
                    when {
                        line.startsWith("### ") -> Text(
                            inlineMarkdown(line.removePrefix("### ")),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                        line.startsWith("## ") -> Text(
                            inlineMarkdown(line.removePrefix("## ")),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                        line.startsWith("# ") -> Text(
                            inlineMarkdown(line.removePrefix("# ")),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        line.startsWith("- ") || line.startsWith("* ") -> Text(
                            inlineMarkdown("· " + line.substring(2)),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                        line.isBlank() -> Spacer(Modifier.height(6.dp))
                        else -> SelectionContainer {
                            Text(
                                inlineMarkdown(line),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 21.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun inlineMarkdown(text: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val builder = buildAnnotatedString {
        val regex = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
        var cursor = 0
        for (match in regex.findAll(text)) {
            append(text.substring(cursor, match.range.first))
            val bold = match.groupValues[1]
            val code = match.groupValues[2]
            if (bold.isNotEmpty()) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            } else {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackground,
                        fontSize = 12.sp
                    )
                ) { append(code) }
            }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
    return builder
}

fun formatTime(iso: String): String {
    if (iso.isBlank()) return ""
    val cleaned = iso.replace("T", " ").replace("Z", "")
    return if (cleaned.length >= 16) cleaned.substring(0, 16) else cleaned
}

fun statusLabel(status: String): String = when (status.lowercase()) {
    "pending" -> "待处理"
    "in_progress", "working" -> "进行中"
    "completed", "done" -> "已完成"
    "blocked" -> "阻塞"
    "idle" -> "空闲"
    "shutdown" -> "已停止"
    "active" -> "已启用"
    "inactive" -> "已停用"
    "error" -> "异常"
    else -> status
}

fun statusColor(status: String): Color = when (status.lowercase()) {
    "completed", "done" -> Color(0xFF16A34A)
    "in_progress", "working" -> Color(0xFFF59E0B)
    "blocked" -> Color(0xFFDC2626)
    "idle" -> Color(0xFF4176E6)
    "shutdown", "inactive" -> Color(0xFF8A8F98)
    "active" -> Color(0xFF16A34A)
    "error" -> Color(0xFFDC2626)
    else -> Color(0xFF6B7280)
}

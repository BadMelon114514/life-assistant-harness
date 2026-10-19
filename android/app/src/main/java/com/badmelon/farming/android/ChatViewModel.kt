package com.badmelon.farming.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

data class ChatMsg(val role: String, val content: String)
data class DraftInfo(val type: String, val payload: JSONObject)

class ChatViewModel : ViewModel() {
    val messages = mutableStateListOf<ChatMsg>()
    var streaming by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var pendingConfirm by mutableStateOf<ConfirmReq?>(null)
        private set
    var draft by mutableStateOf<DraftInfo?>(null)
        private set
    var notice by mutableStateOf<String?>(null)

    private var job: Job? = null
    @Volatile
    private var stopping = false

    val session: String
        get() = Prefs.session

    init {
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch {
            val loaded = runCatching {
                val arr = Api.getArray("/api/chat/history?session=" + encode(session))
                val list = ArrayList<ChatMsg>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val role = o.optString("role", "")
                    val content = o.optString("content", "")
                    if (role == "user" || role == "assistant") list.add(ChatMsg(role, content))
                }
                list
            }.getOrElse { emptyList() }
            withContext(Dispatchers.Main) {
                messages.clear()
                messages.addAll(loaded)
            }
        }
    }

    fun send(text: String) {
        val msg = text.trim()
        if (msg.isEmpty() || streaming) return
        stopping = false
        error = null
        draft = null
        pendingConfirm = null
        notice = null
        messages.add(ChatMsg("user", msg))
        val index = messages.size
        messages.add(ChatMsg("assistant", ""))
        streaming = true
        job = viewModelScope.launch {
            runCatching {
                Api.post(
                    "/api/chat/history",
                    JSONObject().put("role", "user").put("content", msg).put("session", session)
                )
            }
            val sb = StringBuilder()
            var finalText = ""
            try {
                Api.streamChat(
                    message = msg,
                    session = session,
                    onToken = { token ->
                        sb.append(token)
                        messages[index] = ChatMsg("assistant", sb.toString())
                    },
                    onDone = { done ->
                        finalText = if (done.isBlank()) sb.toString() else done
                    },
                    onConfirm = { confirm -> pendingConfirm = confirm },
                    onDraft = { type, payload -> draft = DraftInfo(type, payload) },
                    onSummary = { }
                )
                var content = if (finalText.isBlank()) sb.toString() else finalText
                if (stopping) content = content + "\n\n_（已终止）_"
                content = processSchedules(content)
                if (content.isBlank()) content = if (stopping) "（任务已被终止）" else "（无回复内容）"
                messages[index] = ChatMsg("assistant", content)
                persistAssistant(content)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                val content = if (sb.isBlank()) {
                    "（任务已被终止）"
                } else {
                    sb.toString() + "\n\n_（已终止）_"
                }
                messages[index] = ChatMsg("assistant", content)
                persistAssistant(content)
            } catch (e: Exception) {
                val partial = sb.toString()
                if (partial.isNotBlank()) {
                    val content = partial + "\n\n⚠️ 连接中断：" + (e.message ?: "未知错误")
                    messages[index] = ChatMsg("assistant", content)
                    persistAssistant(partial)
                } else {
                    messages[index] = ChatMsg("assistant", "⚠️ 请求失败：" + (e.message ?: "请检查后端服务"))
                }
                error = e.message ?: "请求失败"
            } finally {
                streaming = false
                job = null
            }
        }
    }

    fun stop() {
        stopping = true
        job?.cancel()
        Api.cancelStream()
        viewModelScope.launch {
            runCatching {
                Api.post("/api/assistant/stop", JSONObject().put("session", session))
            }
        }
    }

    fun resolveConfirm(execute: Boolean) {
        val pending = pendingConfirm ?: return
        pendingConfirm = null
        if (!execute) {
            appendToLast("已取消该操作。")
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                if (pending.op == "write_file" || pending.op == "delete_file" ||
                    pending.op == "run_script" || pending.op == "mkdir"
                ) {
                    Api.post(
                        "/api/assistant/confirm",
                        JSONObject().put("op", pending.op).put("path", pending.arg1)
                            .put("content", pending.arg2)
                    )
                } else {
                    Api.post(
                        "/api/assistant/confirm",
                        JSONObject().put("endpoint", pending.arg1).put("args", pending.arg2)
                    )
                }
            }
            val response = result.getOrNull()
            if (response != null && response.optBoolean("success")) {
                appendToLast("✅ 操作已执行：\n" + response.optString("result", "(无返回)"))
            } else {
                val reason = response?.optString("error").takeUnless { it.isNullOrBlank() }
                    ?: result.exceptionOrNull()?.message ?: "未知错误"
                appendToLast("❌ 操作执行失败：" + reason)
            }
        }
    }

    fun importDraft() {
        val current = draft ?: return
        draft = null
        viewModelScope.launch {
            val isSkill = current.type == "skill_draft"
            val path = if (isSkill) "/api/skills/import-draft" else "/api/mcp/import-draft"
            val result = runCatching { Api.post(path, current.payload) }
            val response = result.getOrNull()
            if (response != null && response.optBoolean("success")) {
                val label = if (isSkill) "技能" else "MCP 服务"
                val name = if (isSkill) {
                    response.optJSONObject("skill")?.optString("name") ?: current.payload.optString("name")
                } else {
                    response.optJSONObject("server")?.optString("name") ?: current.payload.optString("name")
                }
                appendToLast("✅ 已导入" + label + "「" + name + "」")
                notice = "导入成功"
            } else {
                val reason = response?.optString("error").takeUnless { it.isNullOrBlank() }
                    ?: result.exceptionOrNull()?.message ?: "未知错误"
                appendToLast("❌ 导入失败：" + reason)
                notice = "导入失败：" + reason
            }
        }
    }

    fun dismissDraft() {
        draft = null
    }

    fun dismissNotice() {
        notice = null
    }

    fun newSession() {
        job?.cancel()
        Api.cancelStream()
        streaming = false
        Prefs.newSession()
        messages.clear()
        error = null
        notice = null
        draft = null
        pendingConfirm = null
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching { Api.delete("/api/chat/history?session=" + encode(session)) }
            withContext(Dispatchers.Main) { messages.clear() }
        }
    }

    private fun appendToLast(text: String) {
        if (messages.isEmpty()) return
        val last = messages.last()
        if (last.role != "assistant") return
        messages[messages.size - 1] = ChatMsg("assistant", last.content + "\n\n" + text)
    }

    private fun persistAssistant(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                Api.post(
                    "/api/chat/history",
                    JSONObject().put("role", "assistant").put("content", content).put("session", session)
                )
            }
        }
    }

    private fun processSchedules(text: String): String {
        var output = text
        val regex = Regex("\\[\\[SCHEDULE:([^\\]]*)\\]\\]")
        for (match in regex.findAll(text)) {
            val fields = HashMap<String, String>()
            match.groupValues[1].split(";").forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) fields[pair.substring(0, idx).trim()] = pair.substring(idx + 1).trim()
            }
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    Api.post(
                        "/api/assistant/schedule",
                        JSONObject()
                            .put("instruct", fields["instruct"] ?: "")
                            .put("delayMinutes", fields["delayMinutes"]?.toIntOrNull() ?: 0)
                            .put("title", fields["title"] ?: "⏰ 定时提醒")
                            .put("session", session)
                    )
                }
            }
            output = output.replace(match.value, "")
        }
        return output.trim()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

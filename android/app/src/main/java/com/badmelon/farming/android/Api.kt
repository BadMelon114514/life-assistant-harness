package com.badmelon.farming.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ConfirmReq(val op: String, val arg1: String, val arg2: String)

object Api {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val streamClient: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var currentStream: Call? = null

    class ApiException(message: String) : Exception(message)

    fun cancelStream() {
        currentStream?.cancel()
        currentStream = null
    }

    private suspend fun raw(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val rb = Request.Builder().url(Prefs.baseUrl + path)
        when (method) {
            "GET" -> rb.get()
            "DELETE" -> rb.delete()
            else -> rb.method(method, (body ?: "").toRequestBody(jsonMedia))
        }
        for ((k, v) in headers) rb.header(k, v)
        client.newCall(rb.build()).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw ApiException("HTTP " + resp.code + if (text.isBlank()) "" else "：" + text.take(300))
            }
            text
        }
    }

    suspend fun getText(path: String): String = raw("GET", path)

    suspend fun getObject(path: String): JSONObject =
        JSONObject(raw("GET", path).ifBlank { "{}" })

    suspend fun getArray(path: String): JSONArray {
        val text = raw("GET", path)
        return if (text.isBlank()) JSONArray() else JSONArray(text)
    }

    suspend fun post(path: String, body: JSONObject): JSONObject {
        val text = raw("POST", path, body.toString())
        return JSONObject(text.ifBlank { "{}" })
    }

    suspend fun postWithHeaders(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val text = raw("POST", path, body.toString(), headers)
        return JSONObject(text.ifBlank { "{}" })
    }

    suspend fun delete(path: String): JSONObject {
        val text = raw("DELETE", path)
        return JSONObject(text.ifBlank { "{}" })
    }

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        try {
            val text = raw("GET", "/api/monitor/overview")
            if (text.isBlank()) throw ApiException("返回为空")
            "连接成功（" + (System.currentTimeMillis() - started) + " ms）"
        } catch (e: Exception) {
            throw ApiException("连接失败：" + (e.message ?: "未知错误"))
        }
    }

    suspend fun streamChat(
        message: String,
        session: String,
        onToken: suspend (String) -> Unit,
        onDone: suspend (String) -> Unit,
        onConfirm: suspend (ConfirmReq) -> Unit,
        onDraft: suspend (String, JSONObject) -> Unit,
        onSummary: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("message", message)
            .put("session", session)
        val request = Request.Builder()
            .url(Prefs.baseUrl + "/api/assistant/chat-stream")
            .post(payload.toString().toRequestBody(jsonMedia))
            .header("Accept", "text/event-stream")
            .build()
        val call = streamClient.newCall(request)
        currentStream = call
        try {
            call.execute().use { resp: Response ->
                if (!resp.isSuccessful) throw ApiException("HTTP " + resp.code)
                val source = resp.body?.source() ?: throw ApiException("响应为空")
                var event = ""
                val data = StringBuilder()
                var hasData = false

                suspend fun dispatch() {
                    val payloadText = data.toString()
                    when (event) {
                        "token" -> onToken(payloadText)
                        "done" -> onDone(payloadText)
                        "confirm" -> {
                            val parts = payloadText.split("|", limit = 3)
                            onConfirm(
                                ConfirmReq(
                                    parts.getOrElse(0) { "" },
                                    parts.getOrElse(1) { "" },
                                    if (parts.size > 2) parts[2] else ""
                                )
                            )
                        }
                        "skill_draft", "mcp_draft" -> {
                            runCatching { JSONObject(payloadText) }.getOrNull()?.let {
                                onDraft(event, it)
                            }
                        }
                        "summary" -> onSummary(payloadText)
                        "error" -> throw ApiException(payloadText.ifBlank { "流式处理失败" })
                    }
                }

                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) {
                        if (hasData) dispatch()
                        event = ""
                        data.setLength(0)
                        hasData = false
                    } else if (line.startsWith("event:")) {
                        event = line.substring(6).trim()
                    } else if (line.startsWith("data:")) {
                        var value = line.substring(5)
                        if (value.startsWith(" ")) value = value.substring(1)
                        if (hasData) data.append('\n')
                        data.append(value)
                        hasData = true
                    }
                }
                if (hasData) dispatch()
            }
        } finally {
            currentStream = null
        }
    }

    fun connectMonitor(onEvent: (JSONObject) -> Unit, onClosed: () -> Unit = {}): WebSocket? {
        // OkHttp 只接受 http/https 的 HttpUrl，WebSocket 升级由 newWebSocket 内部完成
        val url = (Prefs.baseUrl + "/ws/monitor").toHttpUrlOrNull() ?: return null
        val request = Request.Builder().url(url).build()
        return runCatching {
            client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { JSONObject(text) }.getOrNull()?.let(onEvent)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onEvent(JSONObject().put("type", "ws_error"))
                onClosed()
            }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onClosed()
                }
            })
        }.getOrElse {
            onClosed()
            null
        }
    }
}

fun JSONObject.str(key: String): String {
    val v = optString(key, "")
    return if (v == "null" || v == "undefined") "" else v
}
fun JSONObject.num(key: String): Int = optInt(key, 0)
fun JSONObject.arr(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

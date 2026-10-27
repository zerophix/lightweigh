package com.xcwk.scalehub.infrastructure.ai

import com.xcwk.scalehub.application.ai.AiPort
import com.xcwk.scalehub.application.debug.DebugLog
import com.xcwk.scalehub.application.sync.CloudSessionPort
import com.xcwk.scalehub.infrastructure.config.BackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI 问答实现（旧库 CloudSync.askNow :133-165 逐位移植）：
 * takeLast(8) 轮次、单条 trim(1500)、55s 读超时、result 双层 payload 兼容、
 * message 回退链。HTTP 本体随 M2 部署联调。
 */
class CloudFunctionAi(private val session: CloudSessionPort) : AiPort {

    override suspend fun ask(turns: List<Pair<String, Boolean>>): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val token = session.accessToken()
                ?: run {
                    DebugLog.add("ai", "ask: aborted (no token)")
                    return@withContext false to "登录已失效，请重新登录后再提问"
                }
            val messages = JSONArray()
            for ((text, mine) in turns.takeLast(BackendConfig.AI_CONTEXT_TURNS)) {
                val content = text.trim().take(BackendConfig.AI_MAX_CHARS)
                if (content.isEmpty()) continue
                messages.put(
                    JSONObject().put("role", if (mine) "user" else "assistant").put("content", content),
                )
            }
            if (messages.length() == 0) return@withContext false to "请输入问题"
            val result = requestAi(token, messages.toString())
                ?: run {
                    DebugLog.add("ai", "ask: network FAILED")
                    return@withContext false to "当前网络不可用，请联网后使用AI功能"
                }
            val json = runCatching { JSONObject(result.second) }.getOrNull()
            val payload = json?.optJSONObject("result") ?: json
            val text = payload?.optString("text").orEmpty()
            if (result.first in 200..299 && payload?.optBoolean("ok") == true && text.isNotEmpty()) {
                DebugLog.add("ai", "ask: ok chars=${text.length}")
                return@withContext true to text
            }
            val message = payload?.optString("message").orEmpty()
            DebugLog.add("ai", "ask: failed http=${result.first} ok=${payload?.optBoolean("ok")} message=${message.take(80).ifEmpty { "-" }}")
            if (message.isNotEmpty()) return@withContext false to message
            false to "暂时没有得到回答，请稍后再试"
        }

    private fun requestAi(token: String, body: String, endpoint: String = DEFAULT_ENDPOINT): Pair<Int, String>? = runCatching {
        val conn = URL("${BackendConfig.BASE}${endpoint}").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = BackendConfig.CONNECT_TIMEOUT_MS.toInt()
        conn.readTimeout = BackendConfig.AI_READ_TIMEOUT_MS.toInt()   // 旧库 askNow readTimeoutMs=55s
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        DebugLog.add("ai", "http POST ai-chat -> $code")
        code to text
    }.onFailure {
        DebugLog.add("ai", "http POST ai-chat FAILED: ${it.javaClass.simpleName}: ${it.message}")
    }.getOrNull()

    /** 周总结生成（调用 ai-summary 云函数）。 */
    override suspend fun weeklySummary(prompt: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val token = session.accessToken()
                ?: run {
                    DebugLog.add("ai", "weeklySummary: aborted (no token)")
                    return@withContext false to "请先登录后再查看周总结"
                }
            val messages = org.json.JSONArray().apply {
                put(org.json.JSONObject().put("role", "user").put("content", prompt.trim().take(2000)))
            }
            val result = requestAi(token, messages.toString(), "/v1/functions/ai-summary")
                ?: run {
                    DebugLog.add("ai", "weeklySummary: network FAILED")
                    return@withContext false to "当前网络不可用，请联网后重试"
                }
            val json = runCatching { JSONObject(result.second) }.getOrNull()
            val payload = json?.optJSONObject("result") ?: json
            val text = payload?.optString("text").orEmpty()
            if (result.first in 200..299 && payload?.optBoolean("ok") == true && text.isNotEmpty()) {
                DebugLog.add("ai", "weeklySummary: ok chars=${text.length}")
                return@withContext true to text
            }
            val message = payload?.optString("message").orEmpty()
            if (message.isNotEmpty()) return@withContext false to message
            false to "暂时无法生成周总结，请稍后再试"
        }

    /** AI 健康建议（调用 ai-advice 云函数）。 */
    override suspend fun healthAdvice(context: String, query: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val token = session.accessToken()
                ?: run {
                    return@withContext false to "请先登录后再获取建议"
                }
            val prompt = "你是一位健康顾问。根据以下用户数据：$context，请回答：$query"
            val messages = org.json.JSONArray().apply {
                put(org.json.JSONObject().put("role", "user").put("content", prompt.trim().take(2000)))
            }
            val result = requestAi(token, messages.toString(), "/v1/functions/ai-advice")
                ?: run {
                    return@withContext false to "当前网络不可用，请联网后使用"
                }
            val json = runCatching { JSONObject(result.second) }.getOrNull()
            val payload = json?.optJSONObject("result") ?: json
            val text = payload?.optString("text").orEmpty()
            if (result.first in 200..299 && payload?.optBoolean("ok") == true && text.isNotEmpty()) {
                return@withContext true to text
            }
            val message = payload?.optString("message").orEmpty()
            if (message.isNotEmpty()) return@withContext false to message
            false to "暂时无法获取建议，请稍后再试"
        }

    companion object {
        private const val DEFAULT_ENDPOINT = "/v1/functions/ai-chat"
    }
}

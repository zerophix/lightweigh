package com.xcwk.scalehub.infrastructure.ai

import com.xcwk.scalehub.application.ai.AiPort
import com.xcwk.scalehub.application.sync.CloudSessionPort
import com.xcwk.scalehub.infrastructure.session.SessionStore
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
                ?: return@withContext false to "登录已失效，请重新登录后再提问"
            val messages = JSONArray()
            for ((text, mine) in turns.takeLast(8)) {
                val content = text.trim().take(1500)
                if (content.isEmpty()) continue
                messages.put(
                    JSONObject().put("role", if (mine) "user" else "assistant").put("content", content),
                )
            }
            if (messages.length() == 0) return@withContext false to "请输入问题"
            val result = requestAi(token, messages.toString())
                ?: return@withContext false to "当前网络不可用，请联网后使用AI功能"
            val json = runCatching { JSONObject(result.second) }.getOrNull()
            val payload = json?.optJSONObject("result") ?: json
            val text = payload?.optString("text").orEmpty()
            if (result.first in 200..299 && payload?.optBoolean("ok") == true && text.isNotEmpty()) {
                return@withContext true to text
            }
            val message = payload?.optString("message").orEmpty()
            if (message.isNotEmpty()) return@withContext false to message
            false to "暂时没有得到回答，请稍后再试"
        }

    private fun requestAi(token: String, body: String): Pair<Int, String>? = runCatching {
        val conn = URL("${SessionStore.BASE}/v1/functions/ai-chat").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = 55_000   // 旧库 askNow readTimeoutMs=55s
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        code to text
    }.getOrNull()
}

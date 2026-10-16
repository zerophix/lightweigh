package com.xcwk.scalehub.infrastructure.cloud

import com.xcwk.scalehub.application.sync.CloudDataPort
import com.xcwk.scalehub.application.sync.CloudRow
import com.xcwk.scalehub.application.sync.CloudSessionPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * CloudBase RDB 网关实现（旧库 Gateway.kt 语义：分块 40 行 upsert、
 * Prefer: resolution=merge-duplicates,return=minimal、GET 行透传过滤语法）。
 * 凭据由 SessionCloudAdapter 注入（见 AppContainer 装配）。
 */
class CloudBaseGateway(private val session: CloudSessionPort) : CloudDataPort {

    override suspend fun rows(table: String, query: String): List<CloudRow>? =
        withContext(Dispatchers.IO) {
            val token = session.accessToken() ?: return@withContext null
            val result = request("GET", "$BASE/v1/rdb/rest/$table?$query", token, null, null)
                ?: return@withContext null
            if (result.first !in 200..299) return@withContext null
            parseRowsJson(result.second)
        }

    override suspend fun upsert(table: String, conflict: String, rows: List<CloudRow>): Boolean =
        withContext(Dispatchers.IO) {
            val token = session.accessToken() ?: return@withContext false
            for (chunk in rows.chunked40()) {
                val body = JSONArray().apply { chunk.forEach { put(JSONObject(it.fields)) } }.toString()
                val result = request(
                    "POST", "$BASE/v1/rdb/rest/$table?on_conflict=$conflict",
                    token, body, PREFER_MERGE,
                ) ?: return@withContext false
                if (result.first !in 200..299) return@withContext false
            }
            true
        }

    override suspend fun delete(table: String, filter: String): Boolean =
        withContext(Dispatchers.IO) {
            val token = session.accessToken() ?: return@withContext false
            val result = request("DELETE", "$BASE/v1/rdb/rest/$table?$filter", token, null, null)
                ?: return@withContext false
            result.first in 200..299
        }

    private fun request(
        method: String, url: String, token: String?, body: String?, prefer: String?,
    ): Pair<Int, String>? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (prefer != null) conn.setRequestProperty("Prefer", prefer)
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        code to text
    }.getOrNull()

    companion object {
        internal const val BASE = "https://bodyfat-d0gj8wdw1bfe9b421.api.tcloudbasegateway.com"
        private const val PREFER_MERGE = "resolution=merge-duplicates,return=minimal"

        /** 旧库 Gateway.rows 解析：JSONArray → CloudRow 列表；NULL→null；坏 JSON→null。 */
        internal fun parseRowsJson(body: String): List<CloudRow>? {
            val array = runCatching { JSONArray(body) }.getOrNull() ?: return null
            return (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val fields = mutableMapOf<String, Any?>()
                for (key in obj.keys()) {
                    fields[key] = if (obj.isNull(key)) null else obj.get(key)
                }
                CloudRow(fields)
            }
        }

        /** 旧库 Gateway.upsert 分块（:35-49）。 */
        internal fun <T> List<T>.chunked40(): List<List<T>> =
            if (isEmpty()) emptyList() else chunked(40)
    }
}

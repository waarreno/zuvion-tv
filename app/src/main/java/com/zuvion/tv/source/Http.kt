package com.zuvion.tv.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.zuvion.tv.data.AppJson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT = "ZuvionTV/1.0 (Linux; Android)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /** Cliente sem timeout de leitura, para streams/gravação. */
    val streamClient: OkHttpClient = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val rb = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> rb.header(k, v) }
        client.newCall(rb.build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string() ?: ""
        }
    }

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement {
        val body = get(url, headers).trim()
        if (body.isEmpty()) return JsonNull
        return AppJson.parseToJsonElement(body)
    }
}

fun normalizeBase(url: String): String {
    var u = url.trim()
    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
    return u.trimEnd('/')
}

// Helpers tolerantes: servidores IPTV misturam string/número/null.
fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray
fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
fun JsonObject.int(key: String): Int = str(key)?.toDoubleOrNull()?.toInt() ?: 0
fun JsonObject.long(key: String): Long = str(key)?.toDoubleOrNull()?.toLong() ?: 0L

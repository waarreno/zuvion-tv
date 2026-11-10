package com.zuvion.tv.remote

import android.content.Context
import com.zuvion.tv.data.AppJson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors

/** O que o celular enviou. */
sealed interface RemoteEvent {
    /** Campos do login: type (XTREAM|M3U_URL|STALKER), name, url, user, pass, mac, epg. */
    data class Login(val fields: Map<String, String>) : RemoteEvent
    data class Search(val text: String, val final: Boolean) : RemoteEvent
}

/**
 * Servidor HTTP mínimo, só na rede local, para digitar no celular o que aparece na TV.
 * Fica ligado apenas enquanto o QR Code está na tela e exige um código aleatório na URL.
 */
object RemoteServer {
    private val _events = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<RemoteEvent> = _events

    private var server: ServerSocket? = null
    private var users = 0
    private var token = ""
    private var page = ""
    private val pool = Executors.newCachedThreadPool()

    /** Liga o servidor (se preciso) e devolve a URL para o QR Code, ou null sem Wi-Fi. */
    @Synchronized
    fun start(context: Context, mode: String): String? {
        val ip = localIp() ?: return null
        if (server == null) {
            page = context.assets.open("remote.html").bufferedReader().use { it.readText() }
            token = ByteArray(9).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val s = ServerSocket()
            s.reuseAddress = true
            val port = (8686..8696).firstOrNull { p -> runCatching { s.bind(InetSocketAddress(p)); true }.getOrDefault(false) } ?: return null
            server = s
            pool.execute { acceptLoop(s) }
            portUsed = port
        }
        users++
        return "http://$ip:$portUsed/?t=$token&m=$mode"
    }

    private var portUsed = 8686

    @Synchronized
    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) { runCatching { server?.close() }; server = null }
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val c = runCatching { s.accept() }.getOrNull() ?: break
            pool.execute { runCatching { handle(c) }; runCatching { c.close() } }
        }
    }

    private fun handle(c: Socket) {
        c.soTimeout = 10_000
        val input = BufferedInputStream(c.getInputStream())
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val target = parts[1]
        var length = 0
        while (true) {
            val h = readLine(input) ?: break
            if (h.isEmpty()) break
            if (h.lowercase().startsWith("content-length:")) length = h.substringAfter(":").trim().toIntOrNull() ?: 0
        }
        val path = target.substringBefore("?")
        val query = parseQuery(target.substringAfter("?", ""))
        val out = c.getOutputStream()
        fun respond(code: Int, type: String, body: String) {
            val bytes = body.toByteArray()
            val status = when (code) { 200 -> "OK"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Bad Request" }
            out.write("HTTP/1.1 $code $status\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(bytes)
            out.flush()
        }
        if (query["t"] != token) { respond(403, "text/plain", "Código expirado. Escaneie o QR Code de novo na TV."); return }
        when {
            method == "GET" && path == "/" -> respond(200, "text/html", page)
            method == "POST" && (path == "/login" || path == "/search") -> {
                val body = ByteArray(length.coerceIn(0, 64 * 1024))
                var read = 0
                while (read < body.size) { val n = input.read(body, read, body.size - read); if (n < 0) break; read += n }
                val json = runCatching { AppJson.parseToJsonElement(String(body, 0, read)) as JsonObject }.getOrNull()
                if (json == null) { respond(400, "text/plain", "JSON inválido"); return }
                val fields = json.mapValues { (it.value as? JsonPrimitive)?.contentOrNull.orEmpty() }
                val ev = if (path == "/login") RemoteEvent.Login(fields)
                else RemoteEvent.Search(fields["text"].orEmpty(), fields["final"] == "true")
                _events.tryEmit(ev)
                respond(200, "application/json", "{\"ok\":true}")
            }
            else -> respond(404, "text/plain", "Não encontrado")
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) return buf.toString("UTF-8").trimEnd('\r')
            buf.write(b)
            if (buf.size() > 8192) return null
        }
    }

    private fun parseQuery(q: String): Map<String, String> = q.split("&").filter { it.contains("=") }.associate {
        val (k, v) = it.split("=", limit = 2)
        java.net.URLDecoder.decode(k, "UTF-8") to java.net.URLDecoder.decode(v, "UTF-8")
    }

    private fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()
}

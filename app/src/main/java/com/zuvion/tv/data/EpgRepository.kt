package com.zuvion.tv.data

import android.content.Context
import android.util.Xml
import com.zuvion.tv.source.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

/** Guia de programação XMLTV (com cache em disco de 6h). */
class EpgRepository(private val context: Context) {
    private val _programs = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val programs: StateFlow<Map<String, List<EpgProgram>>> = _programs
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private var loadedUrl: String? = null
    private val lock = Mutex()
    /** nome normalizado do canal -> id do EPG */
    private var displayNames: Map<String, String> = emptyMap()

    fun clear() { _programs.value = emptyMap(); loadedUrl = null; displayNames = emptyMap() }

    /**
     * Carrega o XMLTV. Retorna o número de canais com programação, 0 se não há URL
     * ou -1 em caso de erro. [force] ignora o cache em disco.
     */
    suspend fun load(url: String?, force: Boolean = false): Int = lock.withLock {
        if (url.isNullOrBlank()) return@withLock 0
        if (url == loadedUrl && !force) return@withLock _programs.value.size
        _loading.value = true
        try {
            val map = withContext(Dispatchers.IO) { parse(open(url, force)) }
            _programs.value = map
            loadedUrl = url
            map.size
        } catch (_: Throwable) {
            -1
        } finally {
            _loading.value = false
        }
    }

    private fun open(url: String, force: Boolean): InputStream {
        val cache = File(context.cacheDir, "epg_" + md5(url) + ".xml")
        if (force || !cache.exists() || System.currentTimeMillis() - cache.lastModified() > 6 * 3600_000L) {
            val req = Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
            Http.streamClient.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("EPG HTTP ${r.code}")
                val tmp = File(cache.path + ".tmp")
                r.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(cache)
            }
        }
        val raw = BufferedInputStream(cache.inputStream())
        raw.mark(4)
        val b1 = raw.read(); val b2 = raw.read()
        raw.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(raw) else raw
    }

    private fun parse(input: InputStream): Map<String, List<EpgProgram>> {
        val now = System.currentTimeMillis()
        val from = now - 36 * 3600_000L
        val to = now + 48 * 3600_000L
        val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val fmtNoTz = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        fun time(s: String?): Long {
            if (s.isNullOrBlank()) return 0
            val t = s.trim()
            return runCatching { fmt.parse(t)!!.time }.getOrElse { runCatching { fmtNoTz.parse(t.take(14))!!.time }.getOrDefault(0) }
        }
        val out = HashMap<String, ArrayList<EpgProgram>>()
        val names = HashMap<String, String>()
        input.use { stream ->
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(stream, null)
            var ev = p.eventType
            var chId: String? = null
            var start = 0L; var stop = 0L; var title = ""; var desc = ""
            var inProg = false; var inChannel = false
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "channel" -> { inChannel = true; chId = p.getAttributeValue(null, "id") }
                        "display-name" -> if (inChannel) {
                            val n = p.nextText()
                            chId?.let { names[normalize(n)] = it }
                        }
                        "programme" -> {
                            inProg = true
                            chId = p.getAttributeValue(null, "channel")
                            start = time(p.getAttributeValue(null, "start"))
                            stop = time(p.getAttributeValue(null, "stop"))
                            title = ""; desc = ""
                        }
                        "title" -> if (inProg && title.isEmpty()) title = p.nextText()
                        "desc" -> if (inProg && desc.isEmpty()) desc = p.nextText()
                    }
                    XmlPullParser.END_TAG -> when (p.name) {
                        "channel" -> inChannel = false
                        "programme" -> {
                            inProg = false
                            val id = chId
                            if (id != null && stop > from && start < to && stop > start) {
                                out.getOrPut(id) { ArrayList() } += EpgProgram(id, start, stop, title.trim(), desc.trim())
                            }
                        }
                    }
                }
                ev = p.next()
            }
        }
        displayNames = names
        return out.mapValues { e -> e.value.sortedBy { it.start } }
    }

    /** Programação de um canal, por tvg-id ou pelo nome. */
    fun forChannel(c: Content): List<EpgProgram> {
        val map = _programs.value
        if (map.isEmpty()) return emptyList()
        c.epgId?.let { id -> map[id]?.let { return it } }
        val byName = displayNames[normalize(c.name)] ?: c.epgId?.let { displayNames[normalize(it)] }
        return byName?.let { map[it] } ?: emptyList()
    }

    fun nowNext(c: Content): Pair<EpgProgram?, EpgProgram?> {
        val list = forChannel(c)
        val now = System.currentTimeMillis()
        val i = list.indexOfFirst { it.isNow(now) }
        return if (i >= 0) list[i] to list.getOrNull(i + 1) else null to list.firstOrNull { it.start > now }
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("""\b(hd|fhd|uhd|4k|sd|h265|hevc)\b"""), "")
        .replace(Regex("[^a-z0-9]"), "")

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

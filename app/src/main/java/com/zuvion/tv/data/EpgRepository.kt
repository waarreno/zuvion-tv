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
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

/** Guia de programação XMLTV (com cache em disco de 6h). */
class EpgRepository(private val context: Context) {
    private val _programs = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val programs: StateFlow<Map<String, List<EpgProgram>>> = _programs
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private var loadedUrl: List<String>? = null
    private val lock = Mutex()
    /** nome normalizado do canal -> id do EPG */
    private var displayNames: Map<String, String> = emptyMap()

    fun clear() { _programs.value = emptyMap(); loadedUrl = null; displayNames = emptyMap() }

    /**
     * Carrega o XMLTV. Retorna o número de canais com programação, 0 se não há URL
     * ou -1 em caso de erro. [force] ignora o cache em disco.
     */
    suspend fun load(urls: List<String>, force: Boolean = false): Int = lock.withLock {
        val list = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (list.isEmpty()) return@withLock 0
        if (list == loadedUrl && !force) return@withLock _programs.value.size
        _loading.value = true
        try {
            // Junta o guia do provedor com o guia complementar (o primeiro tem prioridade).
            val map = HashMap<String, List<EpgProgram>>()
            val names = HashMap<String, String>()
            var ok = 0
            for (u in list) {
                val r = runCatching { withContext(Dispatchers.IO) { parse(open(u, force)) } }.getOrNull() ?: continue
                ok++
                r.first.forEach { (k, v) -> map.putIfAbsent(k, v) }
                r.second.forEach { (k, v) -> names.putIfAbsent(k, v) }
            }
            if (ok == 0) return@withLock -1
            displayNames = names
            _programs.value = map
            loadedUrl = list
            map.size
        } finally {
            _loading.value = false
        }
    }

    suspend fun load(url: String?, force: Boolean = false): Int = load(listOfNotNull(url), force)

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

    private fun parse(input: InputStream): Pair<Map<String, List<EpgProgram>>, Map<String, String>> {
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
                            chId?.let { id -> normalize(n).takeIf { it.isNotEmpty() }?.let { names.putIfAbsent(it, id) } }
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
        return out.mapValues { e -> e.value.sortedBy { it.start } } to names
    }

    /** Programação de um canal, por tvg-id ou pelo nome. */
    fun forChannel(c: Content): List<EpgProgram> {
        val map = _programs.value
        if (map.isEmpty()) return emptyList()
        c.epgId?.let { id -> map[id]?.let { return it } }
        val byName = displayNames[normalize(c.name)] ?: c.epgId?.let { displayNames[normalize(it)] }
            ?: c.epgId?.let { id -> displayNames[normalize(id.substringBeforeLast('.'))] }
        return byName?.let { map[it] } ?: emptyList()
    }

    fun nowNext(c: Content): Pair<EpgProgram?, EpgProgram?> {
        val list = forChannel(c)
        val now = System.currentTimeMillis()
        val i = list.indexOfFirst { it.isNow(now) }
        return if (i >= 0) list[i] to list.getOrNull(i + 1) else null to list.firstOrNull { it.start > now }
    }

    /** "São Paulo/SP  HBO HD 2 Ⓢ" e "HBO 2 [FHD]" viram "hbo2". */
    private fun normalize(s: String): String {
        var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marksRx, "")
        t = t.replace(cityRx, "")
        t = Variants.displayName(t)
        return t.replace(qualityRx, "").replace(nonAlnumRx, "")
    }
    private val marksRx = Regex("\\p{M}")
    private val cityRx = Regex("""^.{2,40}?/[a-z]{2}\s+""")
    private val qualityRx = Regex("""(?<![a-z0-9])(hd|fhd|uhd|4k|sd|h265|hevc)(?![a-z0-9])""")
    private val nonAlnumRx = Regex("[^a-z0-9]")

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

package com.zuvion.tv.data

/**
 * Agrupa o mesmo canal/filme em formatos diferentes ("HBO 2 [FHD]", "HBO 2 [HD]", "HBO 2 [FHD][H265]")
 * para mostrar só uma vez na lista; o formato é escolhido no player.
 */
object Variants {
    private val qualityRx = Regex("""(?i)(?<![a-z0-9])(4k|uhd|2160p?|fhd|full\s?hd|1080p?|hd|720p?|sd|480p?|360p)(?![a-z0-9])""")
    private val codecRx = Regex("""(?i)(?<![a-z0-9])(h\.?265|hevc|x265)(?![a-z0-9])""")
    private val h264Rx = Regex("""(?i)(?<![a-z0-9])(h\.?264|x264|avc)(?![a-z0-9])""")
    private val legRx = Regex("""(?i)[\[\(](l|leg|legendado)[\]\)]|(?<![a-z0-9])legendado(?![a-z0-9])""")
    private val dubRx = Regex("""(?i)[\[\(](d|dub|dublado)[\]\)]|(?<![a-z0-9])dublado(?![a-z0-9])""")
    private val extraRx = Regex("""(?i)(?<![a-z0-9])(hdr|hdr10|dolby vision|dv|60fps|50fps|alt|backup|bkp|²|³|\*)(?![a-z0-9])""")

    /** Qualidade normalizada: "4K", "FHD", "HD", "SD" ou "" (não informada). */
    fun quality(name: String): String {
        val q = qualityRx.findAll(name).lastOrNull()?.value?.lowercase()?.replace(" ", "") ?: return ""
        return when (q) {
            "4k", "uhd", "2160", "2160p" -> "4K"
            "fhd", "fullhd", "1080", "1080p" -> "Full HD"
            "hd", "720", "720p" -> "HD"
            else -> "SD"
        }
    }

    fun isH265(name: String) = codecRx.containsMatchIn(name)

    /** Qualidade pela altura real do vídeo. */
    fun qualityFromHeight(h: Int): String = when {
        h >= 1800 -> "4K"; h >= 1000 -> "Full HD"; h >= 700 -> "HD"; h > 0 -> "SD"; else -> ""
    }

    /** Rótulo do formato para o seletor: "Full HD", "4K · H.265", "HD · Legendado"… */
    fun label(name: String): String = listOfNotNull(
        quality(name).ifEmpty { "Padrão" },
        if (isH265(name)) "H.265" else null,
        when { legRx.containsMatchIn(name) -> "Legendado"; dubRx.containsMatchIn(name) -> "Dublado"; else -> null }
    ).joinToString(" · ")

    // Cache: os mesmos nomes são consultados a cada rolagem da lista.
    private val displayCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val keyCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Nome sem as marcas de formato: "HBO 2 [FHD][H265]" -> "HBO 2". */
    fun displayName(name: String): String =
        displayCache[name] ?: computeDisplay(name).also { if (displayCache.size < 200_000) displayCache[name] = it }

    private fun computeDisplay(name: String): String {
        var t = name
        listOf(qualityRx, codecRx, h264Rx, extraRx, legRx, dubRx).forEach { t = t.replace(it, " ") }
        t = t.replace(emptyBracketsRx, " ").replace(trailingSepRx, "").replace(spacesRx, " ").trim()
        return t.ifEmpty { name.trim() }
    }

    // Atenção: o Android usa o motor ICU, que exige "}" e "]" escapados (o Java do PC não exige).
    private val emptyBracketsRx = Regex("""\[\s*\]|\(\s*\)|\{\s*\}""")
    private val trailingSepRx = Regex("""\s*[|\-–:]\s*$""")
    private val spacesRx = Regex("""\s+""")
    private val marksRx = Regex("\\p{M}")
    private val nonAlnumRx = Regex("[^a-z0-9]+")

    /** Chave do grupo: nome sem formato, sem acentos/pontuação. */
    fun key(c: Content): String = "${c.kind}|" + (keyCache[c.name] ?: computeKey(c.name).also { if (keyCache.size < 200_000) keyCache[c.name] = it })

    private fun computeKey(name: String): String =
        java.text.Normalizer.normalize(displayName(name).lowercase(), java.text.Normalizer.Form.NFD)
            .replace(marksRx, "")
            .replace(nonAlnumRx, " ").trim()

    private fun qualityRank(q: String) = when (q) { "Full HD" -> 0; "HD" -> 1; "" -> 2; "4K" -> 3; else -> 4 }

    /**
     * Ordem de preferência: a última escolha do usuário ([preferred]) e, na primeira vez,
     * Full HD em H.264 (o mais compatível).
     */
    fun score(c: Content, preferred: String?): Int {
        val l = label(c.name)
        if (preferred != null) {
            if (l == preferred) return -100
            if (l.substringBefore(" · ") == preferred.substringBefore(" · ")) return -50 + (if (isH265(c.name)) 5 else 0)
        }
        return qualityRank(quality(c.name)) * 10 + (if (isH265(c.name)) 5 else 0) + (if (legRx.containsMatchIn(c.name)) 1 else 0)
    }

    /** Remove duplicados mantendo a ordem da lista e escolhendo o formato preferido de cada grupo. */
    fun dedupe(list: List<Content>, preferred: String?): List<Content> {
        if (list.size < 2) return list
        val groups = LinkedHashMap<String, MutableList<Content>>()
        list.forEach { if (it.kind == Kind.LIVE || it.kind == Kind.MOVIE) groups.getOrPut(key(it)) { ArrayList(2) }.add(it) else groups[it.key] = mutableListOf(it) }
        return groups.values.map { g -> if (g.size == 1) g[0] else g.minBy { score(it, preferred) } }
    }
}

/** Nome para exibir, sem marcas de formato. */
val Content.title: String get() = Variants.displayName(name)

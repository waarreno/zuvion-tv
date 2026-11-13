package com.zuvion.tv.data

import java.text.Normalizer
import java.util.Calendar

/**
 * Gêneros do Zuvion, no lugar das categorias do provedor.
 * Filmes e séries usam os gêneros do TMDB (pelo id do TMDB de cada título, ou pelo nome da
 * categoria quando o título ainda não foi consultado). Canais usam grupos fixos, pelo nome.
 */
object Genres {
    const val PREFIX = "g:"
    const val RELEASES = "Lançamentos"
    const val OTHERS = "Outros"
    const val ADULT = "Adultos"

    /** Gêneros de filmes do TMDB (id -> nome em português). */
    val movie: Map<Int, String> = linkedMapOf(
        28 to "Ação", 12 to "Aventura", 16 to "Animação", 35 to "Comédia", 80 to "Crime",
        99 to "Documentário", 18 to "Drama", 10751 to "Família", 14 to "Fantasia", 36 to "História",
        27 to "Terror", 10402 to "Música", 9648 to "Mistério", 10749 to "Romance",
        878 to "Ficção científica", 10770 to "Cinema TV", 53 to "Suspense", 10752 to "Guerra", 37 to "Faroeste"
    )

    /** Gêneros de séries do TMDB. */
    val tv: Map<Int, String> = linkedMapOf(
        10759 to "Ação e Aventura", 16 to "Animação", 35 to "Comédia", 80 to "Crime", 99 to "Documentário",
        18 to "Drama", 10751 to "Família", 10762 to "Infantil", 9648 to "Mistério", 10763 to "Notícias",
        10764 to "Reality", 10765 to "Ficção científica e Fantasia", 10766 to "Novela", 10767 to "Talk show",
        10768 to "Guerra e Política", 37 to "Faroeste"
    )

    /** Palavras do nome da categoria do provedor -> gênero (filme, série). */
    private val vodWords: List<Triple<List<String>, String?, String?>> = listOf(
        Triple(listOf("acao", "action"), "Ação", "Ação e Aventura"),
        Triple(listOf("aventura", "adventure"), "Aventura", "Ação e Aventura"),
        Triple(listOf("animacao", "animation", "anime", "desenho"), "Animação", "Animação"),
        Triple(listOf("comedia", "comedy", "humor"), "Comédia", "Comédia"),
        Triple(listOf("crime", "policia"), "Crime", "Crime"),
        Triple(listOf("document", "docu"), "Documentário", "Documentário"),
        Triple(listOf("drama"), "Drama", "Drama"),
        Triple(listOf("famil"), "Família", "Família"),
        Triple(listOf("fantasia", "fantasy"), "Fantasia", "Ficção científica e Fantasia"),
        Triple(listOf("histor", "biograf", "epico"), "História", null),
        Triple(listOf("terror", "horror"), "Terror", null),
        Triple(listOf("musica", "musical", "show"), "Música", null),
        Triple(listOf("misterio", "mystery"), "Mistério", "Mistério"),
        Triple(listOf("romance", "romant"), "Romance", null),
        Triple(listOf("ficcao", "sci", "scifi"), "Ficção científica", "Ficção científica e Fantasia"),
        Triple(listOf("suspense", "thriller"), "Suspense", null),
        Triple(listOf("guerra", "war"), "Guerra", "Guerra e Política"),
        Triple(listOf("faroeste", "western"), "Faroeste", "Faroeste"),
        Triple(listOf("infantil", "kids", "crianca"), "Família", "Infantil"),
        Triple(listOf("novela", "dorama"), null, "Novela"),
        Triple(listOf("reality"), null, "Reality"),
        Triple(listOf("noticia", "jornal"), null, "Notícias"),
    )
    private val releaseWords = listOf("lancamento", "estreia", "cinema", "novidade", "recente")

    /** Grupos de canais, na ordem em que aparecem (e em que são testados). */
    private val liveGroups: List<Pair<String, List<String>>> = listOf(
        "Notícias" to listOf("globonews", "globo news", "cnn", "bandnews", "band news", "record news", "recordnews", "jovem pan",
            "jp news", "sbt news", "cnbc", "bbc news", "euronews", "fox news", "news", "noticia", "jornal"),
        "Esportes" to listOf("espn", "sportv", "premiere", "combate", "bandsports", "band sports", "tnt sports", "xsport", "dazn",
            "nba", "nfl", "ufc", "fight", "sport", "esporte", "futebol", "ppv", "golf", "eleven", "fifa", "paulistao", "brasileirao"),
        "Infantil" to listOf("cartoon", "discovery kids", "gloob", "nick", "disney", "baby", "tooncast", "boomerang", "kids",
            "infantil", "zoomoo", "ratimbum", "rati bum", "ra tim bum", "dreamworks", "nat geo kids", "cartoonito", "desenho"),
        "Religiosos" to listOf("cancao nova", "aparecida", "rede vida", "redevida", "novo tempo", "gospel", "evangel", "catolic",
            "boa vontade", "seculo 21", "pai eterno", "imaculada", "religio", "igreja"),
        "Documentários" to listOf("discovery", "history", "h2", "nat geo", "national geographic", "animal planet", "smithsonian",
            "curiosity", "investigation", "investigacao", "docu", "document"),
        "Música" to listOf("mtv", "multishow", "bis", "vh1", "music", "musica", "radio", "trace", "play tv"),
        "Filmes e Séries" to listOf("hbo", "telecine", "megapix", "tnt", "warner", "axn", "sony", "universal", "fx", "space",
            "cinemax", "paramount", "star channel", "amc", "tcm", "film", "cine", "movie", "serie", "a e", "canal brasil",
            "prime box", "studio", "lifetime", "24h", "24 h", "24 horas"),
        "Variedades" to listOf("fazenda", "camera", "gnt", "viva", "canal off", "tlc", "food network", "hgtv", "home health", "comedy central",
            "woohoo", "arte 1", "arte1", "travel", "fashion", "e entertainment", "variedade", "entretenimento", "reality", "bbb"),
        "Abertos" to listOf("globo", "sbt", "record", "band", "redetv", "rede tv", "cultura", "tv brasil", "gazeta", "cnt",
            "rede minas", "tve", "tv camara", "tv senado", "tv justica", "aberto", "aberta", "regiona", "local"),
        "Internacionais" to listOf("internacional", "international", "usa", "uk", "portugal", "latino", "espanha", "mexico",
            "argentina", "italia", "france", "deutsch"),
    )
    val liveOrder: List<String> = liveGroups.map { it.first } + OTHERS + ADULT

    /** Texto sem acento, minúsculo, com letras e números separados e envolto em espaços (" sportv 2 "). */
    fun norm(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
        val sb = StringBuilder(n.length + 8).append(' ')
        var prev = ' '
        for (ch in n) {
            val c = ch.lowercaseChar()
            val kind = when {
                c in 'a'..'z' -> 'a'
                c in '0'..'9' -> '0'
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> continue
                c == '&' -> { if (sb.last() != ' ') sb.append(' '); sb.append("e "); prev = ' '; continue }
                else -> ' '
            }
            if (kind == ' ') { if (sb.last() != ' ') sb.append(' ') }
            else { if (prev != ' ' && prev != kind && sb.last() != ' ') sb.append(' '); sb.append(c) }
            prev = kind
        }
        if (sb.last() != ' ') sb.append(' ')
        return sb.toString()
    }

    /** A palavra-chave começa no início de uma palavra do texto normalizado. */
    private fun has(n: String, kw: String) = n.contains(" $kw")

    /** Só palavras inequívocas: "hot" sozinho pega "Hot Wheels" e canais HOT; "adult" pega "Adult Swim". */
    fun adultTitle(name: String): Boolean {
        val n = name.lowercase()
        return listOf("xxx", "porn", "+18", "18+", "erotic", "erótic", "adulto").any { n.contains(it) } ||
            (n.contains("adult") && !n.contains("adult swim"))
    }

    /** Prefixo de país no nome ("[IL] …", "US: …", "PT | …"), exceto Brasil. */
    private fun foreignPrefix(name: String): Boolean {
        val t = name.trim()
        val code = when {
            t.startsWith("[") && t.indexOf(']') in 3..4 -> t.substring(1, t.indexOf(']'))
            t.length > 4 && t[0].isLetter() && t[1].isLetter() && (t[2] == ':' || t[2] == '|' || (t[2] == ' ' && (t[3] == '|' || t[3] == ':'))) -> t.substring(0, 2)
            else -> return false
        }.trim()
        return code.length in 2..3 && code.all { it in 'A'..'Z' } && code != "BR"
    }

    fun liveGroup(channel: Content, category: Category?): String {
        if (category?.adult == true || adultTitle(channel.name)) return ADULT
        if (foreignPrefix(channel.name)) return "Internacionais"
        val byName = norm(channel.name)
        liveGroups.firstOrNull { (_, kws) -> kws.any { has(byName, it) } }?.let { return it.first }
        val byCat = category?.name?.let { norm(it) } ?: return OTHERS
        return liveGroups.firstOrNull { (_, kws) -> kws.any { has(byCat, it) } }?.first ?: OTHERS
    }

    /** Gêneros pelo nome da categoria do provedor (reserva enquanto o TMDB não respondeu). */
    fun fromCategory(kind: Kind, category: Category?): Set<String> {
        val n = category?.name?.let { norm(it) } ?: return emptySet()
        val tv = kind == Kind.SERIES
        return vodWords.mapNotNullTo(LinkedHashSet()) { (kws, m, s) -> if (kws.any { has(n, it) }) (if (tv) s else m) else null }
    }

    fun fromTmdb(kind: Kind, ids: List<Int>): Set<String> {
        val table = if (kind == Kind.SERIES) tv else movie
        return ids.mapNotNullTo(LinkedHashSet()) { table[it] }
    }

    private val thisYear = Calendar.getInstance().get(Calendar.YEAR)

    fun isRelease(item: Content, category: Category?): Boolean {
        val n = category?.name?.let { norm(it) }
        if (n != null && releaseWords.any { has(n, it) }) return true
        val y = item.year?.take(4)?.toIntOrNull() ?: Regex("""\b(20\d{2})\b""").findAll(item.name).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
        return y != null && y >= thisYear - 1
    }

    /** Ordem das abas de gêneros de filmes/séries: Lançamentos, gêneros em ordem alfabética, Outros, Adultos. */
    private val collator = java.text.Collator.getInstance(java.util.Locale("pt", "BR"))

    fun vodOrder(names: Collection<String>): List<String> {
        val middle = names.filter { it != RELEASES && it != OTHERS && it != ADULT }.sortedWith(collator)
        return listOfNotNull(RELEASES.takeIf { it in names }) + middle +
            listOfNotNull(OTHERS.takeIf { it in names }, ADULT.takeIf { it in names })
    }
}

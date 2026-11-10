package com.zuvion.tv.source

import android.content.Context
import com.zuvion.tv.data.Category
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.EpgProgram
import com.zuvion.tv.data.Kind
import com.zuvion.tv.data.Playlist
import com.zuvion.tv.data.SeriesInfo
import com.zuvion.tv.data.SourceType

interface IptvSource {
    val playlist: Playlist

    suspend fun categories(kind: Kind): List<Category>

    /** Todos os itens do tipo, ou null se a fonte só carrega por categoria. */
    suspend fun items(kind: Kind): List<Content>?

    /** Itens de uma categoria (usado quando [items] retorna null). */
    suspend fun itemsInCategory(kind: Kind, categoryId: String): List<Content> =
        items(kind)?.filter { it.categoryId == categoryId } ?: emptyList()

    suspend fun seriesInfo(series: Content): SeriesInfo = SeriesInfo(series.plot, series.poster, emptyMap())

    suspend fun movieInfo(movie: Content): Content = movie

    /** URL final para tocar (Stalker precisa gerar link). */
    suspend fun resolveUrl(item: Content): String = item.streamUrl ?: error("Sem URL")

    /** URL de catch-up/replay, se o servidor suportar. */
    fun catchupUrl(item: Content, startMs: Long, endMs: Long): String? = null

    /** URL do EPG XMLTV. */
    fun epgUrl(): String? = playlist.epgUrl.takeIf { it.isNotBlank() }

    suspend fun shortEpg(item: Content): List<EpgProgram> = emptyList()

    companion object {
        fun create(context: Context, p: Playlist, liveFormat: String): IptvSource = when (p.type) {
            SourceType.XTREAM -> XtreamSource(p, liveFormat)
            SourceType.M3U_URL, SourceType.M3U_FILE -> M3uSource(context, p)
            SourceType.STALKER -> StalkerSource(p)
        }
    }
}

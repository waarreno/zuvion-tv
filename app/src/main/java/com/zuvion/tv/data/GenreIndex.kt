package com.zuvion.tv.data

import android.content.Context
import com.zuvion.tv.meta.Metadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File

@Serializable
private class GenreEntry(val key: String, val ids: List<Int> = emptyList())

@Serializable
private class GenreCache(val entries: List<GenreEntry> = emptyList())

/**
 * Gêneros do TMDB de cada título da lista, consultados aos poucos em segundo plano
 * (uma busca por título, 8 de cada vez) e guardados no aparelho. Títulos ainda não
 * consultados usam o nome da categoria do provedor.
 */
@OptIn(ExperimentalSerializationApi::class)
class GenreIndex(private val context: Context, private val meta: Metadata, private val scope: CoroutineScope) {
    private val file get() = File(context.filesDir, "genres.pb")
    @Volatile private var map: Map<String, List<Int>> = emptyMap()
    @Volatile private var loaded = false
    private val keys = HashMap<String, String>()
    private var job: Job? = null

    /** Muda quando chegam gêneros novos (as telas refazem os grupos na próxima abertura). */
    @Volatile var version = 0
        private set

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            map = runCatching { ProtoBuf.decodeFromByteArray(GenreCache.serializer(), file.readBytes()).entries.associate { it.key to it.ids } }.getOrDefault(emptyMap())
            loaded = true
        }
    }

    private fun keyOf(c: Content): String = synchronized(keys) { keys.getOrPut(c.key) { meta.titleKey(c) } }

    /** Ids de gênero do TMDB; null = ainda não consultado. */
    fun idsOf(c: Content): List<Int>? {
        ensureLoaded()
        return map[keyOf(c)]
    }

    fun enrich(items: List<Content>) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            ensureLoaded()
            if (!meta.tmdbReady) return@launch
            val todo = items.asSequence().distinctBy { keyOf(it) }.filter { map[keyOf(it)] == null }.toList()
            if (todo.isEmpty()) return@launch
            android.util.Log.i("Zuvion", "gêneros: consultando ${todo.size} títulos no TMDB")
            val work = HashMap(map)
            var done = 0
            var lastSave = 0
            for (chunk in todo.chunked(8)) {
                val res = coroutineScope { chunk.map { c -> async { c to meta.tmdbGenres(c) } }.awaitAll() }
                var fails = 0
                res.forEach { (c, ids) -> if (ids != null) work[keyOf(c)] = ids else fails++ }
                done += chunk.size
                if (done - lastSave >= 300) { lastSave = done; publish(work) }
                // Sem rede ou limite do TMDB: espera um pouco antes de seguir.
                if (fails == chunk.size) delay(5000)
            }
            publish(work)
            android.util.Log.i("Zuvion", "gêneros: concluído (${work.size} títulos)")
        }
    }

    private fun publish(work: HashMap<String, List<Int>>) {
        val snapshot = HashMap(work)
        map = snapshot
        version++
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeBytes(ProtoBuf.encodeToByteArray(GenreCache.serializer(), GenreCache(snapshot.map { GenreEntry(it.key, it.value) })))
            tmp.renameTo(file)
        }
    }
}

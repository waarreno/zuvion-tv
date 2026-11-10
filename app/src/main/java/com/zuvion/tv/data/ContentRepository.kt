package com.zuvion.tv.data

import android.content.Context
import com.zuvion.tv.source.IptvSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class Library(
    val categories: Map<Kind, List<Category>> = emptyMap(),
    val items: Map<Kind, List<Content>> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null
)

/** Mantém em memória o conteúdo da lista ativa. */
class ContentRepository(
    private val context: Context,
    private val store: Store,
    private val epg: EpgRepository,
    private val scope: CoroutineScope
) {
    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library
    var source: IptvSource? = null
        private set
    private val categoryCache = HashMap<String, List<Content>>()
    private val lock = Mutex()

    fun reload() {
        val p = store.activePlaylist ?: run { source = null; _library.value = Library(); return }
        val src = IptvSource.create(context, p, store.settings.liveFormat)
        source = src
        categoryCache.clear()
        epg.clear()
        _library.value = Library(loading = true)
        scope.launch(Dispatchers.IO) {
            try {
                coroutineScope {
                    val kinds = listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES)
                    val cats = kinds.map { k -> async { k to runCatching { src.categories(k) }.getOrDefault(emptyList()) } }
                    val items = kinds.map { k -> async { k to runCatching { src.items(k) }.getOrNull() } }
                    val catMap = cats.associate { it.await() }
                    val itemMap = items.associate { it.await() }.mapValues { it.value ?: emptyList() }
                    if (catMap.values.all { it.isEmpty() } && itemMap.values.all { it.isEmpty() }) {
                        _library.value = Library(error = "Não foi possível carregar a lista. Verifique o login e a conexão.")
                    } else {
                        _library.value = Library(catMap, itemMap)
                    }
                }
                epg.load(src.epgUrl())
            } catch (e: Throwable) {
                _library.value = Library(error = e.message ?: "Erro ao carregar")
            }
        }
    }

    private fun allowed(c: Category?): Boolean {
        val kids = store.activeProfile?.kids == true
        val hide = store.settings.hideAdult
        return c == null || !(c.adult && (kids || hide))
    }

    fun categories(kind: Kind): List<Category> = _library.value.categories[kind].orEmpty().filter { allowed(it) }

    suspend fun itemsIn(kind: Kind, categoryId: String?): List<Content> {
        val lib = _library.value
        val all = lib.items[kind].orEmpty()
        val catById = lib.categories[kind].orEmpty().associateBy { it.id }
        if (categoryId == null) return all.filter { allowed(catById[it.categoryId]) }
        if (all.isNotEmpty()) return all.filter { it.categoryId == categoryId }
        val src = source ?: return emptyList()
        return lock.withLock {
            categoryCache.getOrPut("$kind:$categoryId") { runCatching { src.itemsInCategory(kind, categoryId) }.getOrDefault(emptyList()) }
        }
    }

    fun search(query: String): List<Content> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val lib = _library.value
        return listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES).flatMap { k ->
            val cats = lib.categories[k].orEmpty().associateBy { it.id }
            lib.items[k].orEmpty().asSequence().filter { it.name.lowercase().contains(q) && allowed(cats[it.categoryId]) }.take(60).toList()
        }
    }

    fun categoryOf(c: Content): Category? = _library.value.categories[c.kind]?.firstOrNull { it.id == c.categoryId }
}

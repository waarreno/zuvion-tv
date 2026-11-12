package com.zuvion.tv.data

import android.content.Context
import com.zuvion.tv.source.IptvSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File

enum class StepState { WAITING, LOADING, OK, FAILED }

/** Etapa do carregamento mostrada na tela de progresso (TV ao vivo, Filmes, Séries, Guia). */
data class LoadStep(val id: String, val label: String, val state: StepState = StepState.WAITING, val count: Int = 0)

data class Library(
    val categories: Map<Kind, List<Category>> = emptyMap(),
    val items: Map<Kind, List<Content>> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Atualização pela rede em andamento (mostra a tela de progresso). */
    val refreshing: Boolean = false,
    val steps: List<LoadStep> = emptyList(),
    val updatedAt: Long = 0
)

@Serializable
private class LibraryCache(
    val live: List<Content> = emptyList(),
    val movies: List<Content> = emptyList(),
    val series: List<Content> = emptyList(),
    val liveCats: List<Category> = emptyList(),
    val movieCats: List<Category> = emptyList(),
    val seriesCats: List<Category> = emptyList(),
    val savedAt: Long = 0
)

/**
 * Mantém em memória o conteúdo da lista ativa.
 * A lista fica em cache no aparelho e só é baixada de novo periodicamente
 * (Ajustes → intervalo) ou quando o usuário pede.
 */
@OptIn(ExperimentalSerializationApi::class)
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
    private var job: Job? = null

    private fun cacheFile(id: String) = File(context.filesDir, "library/$id.pb")

    /** Abre a lista ativa: usa o cache se existir e só atualiza pela rede quando venceu. */
    fun open() {
        val p = store.activePlaylist ?: run { source = null; _library.value = Library(); return }
        source = IptvSource.create(context, p, store.settings.liveFormat)
        categoryCache.clear()
        epg.clear()
        job?.cancel()
        _library.value = Library(loading = true)
        job = scope.launch {
            val cached = withContext(Dispatchers.IO) { readCache(p.id) }
            if (cached == null) { refreshNow(); return@launch }
            _library.value = cached
            groups()
            val hours = store.settings.refreshHours
            val age = System.currentTimeMillis() - cached.updatedAt
            if (hours > 0 && age > hours * 3600_000L) refreshNow()
            else launch(Dispatchers.IO) { epg.load(epgUrls(source)) }
        }
    }

    /** Alias antigo: abrir a lista (ex.: ao trocar de lista ativa). */
    fun reload() = open()

    /** Baixa tudo de novo pela rede, mostrando o progresso por tipo. */
    fun refresh() {
        job?.cancel()
        job = scope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        val p = store.activePlaylist ?: return
        val src = IptvSource.create(context, p, store.settings.liveFormat)
        source = src
        categoryCache.clear()
        val steps = listOf(
            LoadStep("LIVE", "TV ao vivo"), LoadStep("MOVIE", "Filmes"),
            LoadStep("SERIES", "Séries"), LoadStep("EPG", "Guia de programação")
        )
        val old = _library.value
        _library.value = old.copy(loading = old.items.isEmpty(), refreshing = true, error = null, steps = steps)
        fun step(id: String, st: StepState, count: Int = 0) = _library.update { l ->
            l.copy(steps = l.steps.map { if (it.id == id) it.copy(state = st, count = count) else it })
        }
        try {
            val result = withContext(Dispatchers.IO) {
                coroutineScope {
                    val kinds = listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES)
                    kinds.map { k ->
                        async {
                            step(k.name, StepState.LOADING)
                            val cats = runCatching { src.categories(k) }
                            val items = runCatching { src.items(k) }
                            val ok = cats.isSuccess || (items.isSuccess && items.getOrNull() != null)
                            val catList = cats.getOrDefault(emptyList())
                            val itemList = items.getOrNull().orEmpty()
                            step(k.name, if (ok) StepState.OK else StepState.FAILED, if (itemList.isNotEmpty()) itemList.size else catList.size)
                            Triple(k, catList, itemList)
                        }
                    }.map { it.await() }
                }
            }
            val catMap = result.associate { it.first to it.second }
            val itemMap = result.associate { it.first to it.third }
            if (catMap.values.all { it.isEmpty() } && itemMap.values.all { it.isEmpty() }) {
                // Mantém o que já havia em cache, se houver.
                _library.update { l ->
                    l.copy(loading = false, error = if (l.items.isEmpty() && l.categories.isEmpty())
                        "Não foi possível carregar a lista. Verifique o login e a conexão." else null)
                }
                step("EPG", StepState.FAILED)
            } else {
                val now = System.currentTimeMillis()
                _library.update { it.copy(categories = catMap, items = itemMap, loading = false, error = null, updatedAt = now) }
                groups()
                withContext(Dispatchers.IO) { writeCache(p.id, catMap, itemMap, now) }
                store.updateSettings { it.copy(libraryUpdated = it.libraryUpdated + (p.id to now)) }
                step("EPG", StepState.LOADING)
                val n = withContext(Dispatchers.IO) { epg.load(epgUrls(src), force = true) }
                step("EPG", if (n >= 0) StepState.OK else StepState.FAILED, n.coerceAtLeast(0))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            _library.update { it.copy(loading = false, error = if (it.items.isEmpty()) (e.message ?: "Erro ao carregar") else null) }
        } finally {
            _library.update { it.copy(refreshing = false) }
        }
    }

    private fun epgUrls(src: IptvSource?): List<String> = listOfNotNull(src?.epgUrl(), store.settings.extraEpgUrl.takeIf { it.isNotBlank() })

    /** Recarrega só o guia (ao mudar o guia complementar). */
    fun reloadEpg() { scope.launch(Dispatchers.IO) { epg.load(epgUrls(source), force = false) } }

    private fun readCache(id: String): Library? = runCatching {
        val f = cacheFile(id)
        if (!f.exists()) return null
        val c = ProtoBuf.decodeFromByteArray(LibraryCache.serializer(), f.readBytes())
        Library(
            categories = mapOf(Kind.LIVE to c.liveCats, Kind.MOVIE to c.movieCats, Kind.SERIES to c.seriesCats),
            items = mapOf(Kind.LIVE to c.live, Kind.MOVIE to c.movies, Kind.SERIES to c.series),
            updatedAt = c.savedAt
        )
    }.getOrNull()

    private fun writeCache(id: String, cats: Map<Kind, List<Category>>, items: Map<Kind, List<Content>>, at: Long) {
        runCatching {
            val c = LibraryCache(
                live = items[Kind.LIVE].orEmpty(), movies = items[Kind.MOVIE].orEmpty(), series = items[Kind.SERIES].orEmpty(),
                liveCats = cats[Kind.LIVE].orEmpty(), movieCats = cats[Kind.MOVIE].orEmpty(), seriesCats = cats[Kind.SERIES].orEmpty(),
                savedAt = at
            )
            val f = cacheFile(id)
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            tmp.writeBytes(ProtoBuf.encodeToByteArray(LibraryCache.serializer(), c))
            tmp.renameTo(f)
        }
    }

    fun deleteCache(id: String) { cacheFile(id).delete() }

    private fun allowed(c: Category?): Boolean {
        val kids = store.activeProfile?.kids == true
        val hide = store.settings.hideAdult
        return c == null || !(c.adult && (kids || hide))
    }

    // Índice de formatos (mesmo canal/filme em SD/HD/FHD/4K), refeito quando a lista muda.
    private var groupIndex: Map<String, List<Content>> = emptyMap()
    private var groupIndexOf: Library? = null

    @Volatile private var indexJob: Job? = null

    /** Monta o índice em segundo plano; enquanto não fica pronto, cada item vale por si. */
    private fun groups(): Map<String, List<Content>> {
        val lib = _library.value
        if (groupIndexOf !== lib && lib.items.isNotEmpty() && indexJob?.isActive != true) {
            indexJob = scope.launch(Dispatchers.Default) {
                val idx = (lib.items[Kind.LIVE].orEmpty() + lib.items[Kind.MOVIE].orEmpty()).groupBy { Variants.key(it) }
                synchronized(this@ContentRepository) { groupIndex = idx; groupIndexOf = lib }
            }
        }
        return if (groupIndexOf === lib) groupIndex else emptyMap()
    }

    private val preferred get() = store.settings.preferredFormat

    /** Todos os formatos disponíveis do mesmo canal/filme, do preferido para o menos indicado. */
    fun variantsOf(c: Content): List<Content> {
        if (c.kind != Kind.LIVE && c.kind != Kind.MOVIE) return listOf(c)
        val g = synchronized(this) { groups() }[Variants.key(c)].orEmpty()
        val all = if (g.none { it.key == c.key }) listOf(c) + g else g
        return all.distinctBy { it.key }.sortedBy { Variants.score(it, null) }
    }

    /** O formato preferido do mesmo conteúdo (usado ao abrir de favoritos/histórico). */
    fun preferredVariant(c: Content): Content = variantsOf(c).minByOrNull { Variants.score(it, preferred) } ?: c

    fun categories(kind: Kind): List<Category> = _library.value.categories[kind].orEmpty().filter { allowed(it) }

    suspend fun itemsIn(kind: Kind, categoryId: String?): List<Content> = withContext(Dispatchers.Default) { itemsInImpl(kind, categoryId) }

    private suspend fun itemsInImpl(kind: Kind, categoryId: String?): List<Content> {
        val lib = _library.value
        val all = lib.items[kind].orEmpty()
        val catById = lib.categories[kind].orEmpty().associateBy { it.id }
        if (categoryId == null) return Variants.dedupe(all.filter { allowed(catById[it.categoryId]) }, preferred)
        if (all.isNotEmpty()) return Variants.dedupe(all.filter { it.categoryId == categoryId }, preferred)
        val src = source ?: return emptyList()
        return lock.withLock {
            categoryCache.getOrPut("$kind:$categoryId") { runCatching { src.itemsInCategory(kind, categoryId) }.getOrDefault(emptyList()) }
        }
    }

    suspend fun search(query: String): List<Content> = withContext(Dispatchers.Default) { searchImpl(query) }

    private fun searchImpl(query: String): List<Content> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val lib = _library.value
        return listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES).flatMap { k ->
            val cats = lib.categories[k].orEmpty().associateBy { it.id }
            Variants.dedupe(lib.items[k].orEmpty().asSequence().filter { it.name.lowercase().contains(q) && allowed(cats[it.categoryId]) }.take(150).toList(), preferred).take(60)
        }
    }

    /**
     * Qualidade de um item: marca no nome ("4K", "FHD"…), senão a da categoria ("Filmes | 4K"),
     * senão a altura real do vídeo informada pelo servidor. Vazio quando não dá para saber.
     */
    fun qualityOf(c: Content): String =
        Variants.quality(c.name).ifEmpty { categoryOf(c)?.name?.let { Variants.quality(it) }.orEmpty() }
            .ifEmpty { Variants.qualityFromHeight(c.videoHeight) }

    fun categoryOf(c: Content): Category? = _library.value.categories[c.kind]?.firstOrNull { it.id == c.categoryId }
}

package com.zuvion.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.zuvion.tv.data.ContentRepository
import com.zuvion.tv.data.EpgRepository
import com.zuvion.tv.data.Store
import com.zuvion.tv.meta.Metadata
import com.zuvion.tv.source.Http
import com.zuvion.tv.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ZuvionApp : Application(), ImageLoaderFactory {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var store: Store
    lateinit var epg: EpgRepository
    lateinit var repo: ContentRepository
    lateinit var updater: Updater
    lateinit var meta: Metadata

    override fun onCreate() {
        super.onCreate()
        // A tela de erro roda em processo próprio e não precisa do resto do app.
        if (CrashActivity.isCrashProcess(this)) return
        CrashActivity.install(this)
        // Remove o APK de atualização já instalado (libera espaço em TVs com pouco armazenamento).
        runCatching { cacheDir.resolve("updates").deleteRecursively() }
        store = Store(this, scope)
        store.ensureDefaultProfile()
        epg = EpgRepository(this)
        repo = ContentRepository(this, store, epg, scope)
        updater = Updater(this)
        meta = Metadata(this, store)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Http.client)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(150L * 1024 * 1024).build() }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}

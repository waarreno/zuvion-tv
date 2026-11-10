package com.zuvion.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.zuvion.tv.data.ContentRepository
import com.zuvion.tv.data.EpgRepository
import com.zuvion.tv.data.Store
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

    override fun onCreate() {
        super.onCreate()
        store = Store(this, scope)
        store.ensureDefaultProfile()
        epg = EpgRepository(this)
        repo = ContentRepository(this, store, epg, scope)
        updater = Updater(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Http.client)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(150L * 1024 * 1024).build() }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}

package com.mangareader.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mangareader.app.data.local.MangaDatabase
import com.mangareader.app.data.repository.MangaRepository
import com.mangareader.app.network.NetworkClient

class MangaApplication : Application(), ImageLoaderFactory {

    lateinit var repository: MangaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = MangaDatabase.getDatabase(this)
        repository = MangaRepository(
            context = this,
            mangaDao = database.mangaDao(),
            chapterDao = database.mangaChapterDao()
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(NetworkClient.imageOkHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("manga_image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024) // 256 MB disk cache
                    .build()
            }
            .crossfade(true)
            .build()
    }
}

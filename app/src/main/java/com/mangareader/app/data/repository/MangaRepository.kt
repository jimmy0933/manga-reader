package com.mangareader.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.mangareader.app.data.local.*
import com.mangareader.app.network.NetworkClient
import com.mangareader.app.parser.ManhuarenParser
import com.mangareader.app.parser.MangaSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

data class MangaDownloadStatus(
    val mangaId: String,
    val mangaTitle: String,
    val current: Int,
    val total: Int,
    val currentChapterTitle: String,
    val isDownloading: Boolean
)

private val globalMangaMutexes = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
private fun getMangaMutex(mangaId: String) = globalMangaMutexes.getOrPut(mangaId) { kotlinx.coroutines.sync.Mutex() }

class MangaRepository(
    private val context: Context,
    private val database: MangaDatabase
) {

    private val mangaDao = database.mangaDao()
    private val chapterDao = database.mangaChapterDao()
    private val _downloadStatus = kotlinx.coroutines.flow.MutableStateFlow<MangaDownloadStatus?>(null)
    val downloadStatus: kotlinx.coroutines.flow.StateFlow<MangaDownloadStatus?> = _downloadStatus

    fun getAllManga(): Flow<List<MangaEntity>> = mangaDao.getAllMangaFlow()

    fun getMangaFlow(id: String): Flow<MangaEntity?> = mangaDao.getMangaFlow(id)

    fun getChaptersFlow(mangaId: String): Flow<List<MangaChapterEntity>> = chapterDao.getChaptersFlow(mangaId)

    suspend fun importMangaFromUrl(url: String, htmlContent: String? = null): Result<MangaEntity> = withContext(Dispatchers.IO) {
        try {
            val html = htmlContent ?: NetworkClient.getHtml(url)
            val parsed = ManhuarenParser.parseDetail(url, html)

            if (parsed.chapters.isEmpty()) {
                return@withContext Result.failure(Exception("解析失敗，目錄為空"))
            }

            val savedManga = database.withTransaction {
                val existing = mangaDao.getMangaById(parsed.id)
                val manga = MangaEntity(
                    id = parsed.id,
                    title = parsed.title,
                    author = parsed.author,
                    coverUrl = parsed.coverUrl,
                    intro = parsed.intro,
                    sourceUrl = parsed.sourceUrl,
                    latestChapterTitle = parsed.latestChapterTitle,
                    lastReadChapterId = existing?.lastReadChapterId ?: parsed.chapters.firstOrNull()?.chapterId,
                    lastReadChapterTitle = existing?.lastReadChapterTitle ?: parsed.chapters.firstOrNull()?.title,
                    lastReadChapterIndex = existing?.lastReadChapterIndex ?: 0,
                    lastReadPageIndex = existing?.lastReadPageIndex ?: 0,
                    lastReadTime = System.currentTimeMillis(),
                    totalChapters = parsed.chapters.size,
                    downloadedChapters = existing?.downloadedChapters ?: 0,
                    isFavorite = true
                )

                mangaDao.insertManga(manga)

                val existingChapters = chapterDao.getChapters(parsed.id)
                val existingDownloadedMap = existingChapters.associateBy { it.chapterId }
                chapterDao.deleteChaptersByMangaId(parsed.id)

                val chapterEntities = parsed.chapters.map { ch ->
                    val prev = existingDownloadedMap[ch.chapterId]
                    var migratedPathsJson = prev?.localImagePathsJson

                    if (prev != null && prev.isDownloaded) {
                        val oldDir = java.io.File(context.filesDir, "manga/${parsed.id}/${ch.chapterIndex}")
                        val newDir = java.io.File(context.filesDir, "manga/${parsed.id}/${ch.chapterId}")
                        if (oldDir.exists() && oldDir.isDirectory && !newDir.exists()) {
                            oldDir.renameTo(newDir)
                        }
                        if (migratedPathsJson != null && migratedPathsJson!!.contains("/manga/${parsed.id}/${ch.chapterIndex}/")) {
                            migratedPathsJson = migratedPathsJson!!.replace("/manga/${parsed.id}/${ch.chapterIndex}/", "/manga/${parsed.id}/${ch.chapterId}/")
                        }
                    }
                    MangaChapterEntity(
                        mangaId = manga.id,
                        chapterIndex = ch.chapterIndex,
                        chapterId = ch.chapterId,
                        title = ch.title,
                        url = ch.url,
                        imageUrlsJson = prev?.imageUrlsJson,
                        localImagePathsJson = migratedPathsJson,
                        pageCount = prev?.pageCount ?: 0,
                        isDownloaded = prev?.isDownloaded ?: false
                    )
                }
                chapterDao.insertChapters(chapterEntities)
                mangaDao.refreshDownloadedCount(parsed.id)
                manga
            }

            Result.success(savedManga)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchManga(query: String): Result<List<MangaSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val searchUrl = "${ManhuarenParser.BASE_URL}/search?title=$encodedQuery"
            val html = NetworkClient.getHtml(searchUrl)
            val results = ManhuarenParser.parseSearchResults(html)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncLatestChapters(mangaId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val manga = mangaDao.getMangaById(mangaId)
                ?: return@withContext Result.failure(Exception("找不到該漫畫"))

            val html = NetworkClient.getHtml(manga.sourceUrl)
            val parsed = ManhuarenParser.parseDetail(manga.sourceUrl, html)

            if (parsed.chapters.isEmpty()) {
                return@withContext Result.failure(Exception("解析失敗，目錄為空"))
            }

            var newCount = 0
            database.withTransaction {
                val existingChapters = chapterDao.getChapters(mangaId)
                val existingDownloadedMap = existingChapters.associateBy { it.chapterId }
                val existingUrls = existingChapters.map { it.url }.toSet()

                newCount = parsed.chapters.count { !existingUrls.contains(it.url) }

                val updatedEntities = parsed.chapters.map { ch ->
                    val prev = existingDownloadedMap[ch.chapterId]
                    var migratedPathsJson = prev?.localImagePathsJson

                    if (prev != null && prev.isDownloaded) {
                        val oldDir = java.io.File(context.filesDir, "manga/$mangaId/${ch.chapterIndex}")
                        val newDir = java.io.File(context.filesDir, "manga/$mangaId/${ch.chapterId}")
                        if (oldDir.exists() && oldDir.isDirectory && !newDir.exists()) {
                            oldDir.renameTo(newDir)
                        }
                        if (migratedPathsJson != null && migratedPathsJson!!.contains("/manga/$mangaId/${ch.chapterIndex}/")) {
                            migratedPathsJson = migratedPathsJson!!.replace("/manga/$mangaId/${ch.chapterIndex}/", "/manga/$mangaId/${ch.chapterId}/")
                        }
                    }
                    MangaChapterEntity(
                        mangaId = mangaId,
                        chapterIndex = ch.chapterIndex,
                        chapterId = ch.chapterId,
                        title = ch.title,
                        url = ch.url,
                        imageUrlsJson = prev?.imageUrlsJson,
                        localImagePathsJson = migratedPathsJson,
                        pageCount = prev?.pageCount ?: 0,
                        isDownloaded = prev?.isDownloaded ?: false
                    )
                }

                chapterDao.deleteChaptersByMangaId(mangaId)
                chapterDao.insertChapters(updatedEntities)
                mangaDao.updateTotalChapters(mangaId, updatedEntities.size)
                if (parsed.latestChapterTitle.isNotBlank()) {
                    mangaDao.updateLatestChapterTitle(mangaId, parsed.latestChapterTitle)
                }

                val manga = mangaDao.getMangaById(mangaId)
                if (manga?.lastReadChapterId != null) {
                    val newIndex = updatedEntities.indexOfFirst { it.chapterId == manga.lastReadChapterId }
                    if (newIndex != -1 && newIndex != manga.lastReadChapterIndex) {
                        mangaDao.updateReadingProgress(
                            mangaId,
                            manga.lastReadChapterId,
                            manga.lastReadChapterTitle ?: "",
                            newIndex,
                            manga.lastReadPageIndex
                        )
                    }
                }

                mangaDao.refreshDownloadedCount(mangaId)
            }

            Result.success(newCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChapterImages(mangaId: String, chapterId: String, forceRefresh: Boolean = false): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val chapter = chapterDao.getChapterById(mangaId, chapterId)
                ?: return@withContext Result.failure(Exception("章節不存在"))

            // 1. If downloaded locally, return local file paths
            if (chapter.isDownloaded && !chapter.localImagePathsJson.isNullOrBlank()) {
                val arr = JSONArray(chapter.localImagePathsJson)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                if (list.isNotEmpty()) {
                    return@withContext Result.success(list)
                }
            }

            // 2. If cached remotely and not forced refresh, return cached URLs
            if (!forceRefresh && !chapter.imageUrlsJson.isNullOrBlank()) {
                val arr = JSONArray(chapter.imageUrlsJson)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                if (list.isNotEmpty()) {
                    return@withContext Result.success(list)
                }
            }

            // 3. Fetch chapter HTML and unpack Dean Edwards packer
            val html = NetworkClient.getHtml(chapter.url)
            val images = ManhuarenParser.unpackChapterImages(html)

            if (images.isEmpty()) {
                return@withContext Result.failure(Exception("解析漫畫圖片失敗，該章節可能需要付費或需等待載入。"))
            }

            // Cache to database
            val jsonArr = JSONArray()
            images.forEach { jsonArr.put(it) }
            chapterDao.saveChapterImages(mangaId, chapterId, jsonArr.toString(), images.size)

            Result.success(images)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadChapter(mangaId: String, chapterId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val imagesRes = getChapterImages(mangaId, chapterId)
            if (imagesRes.isFailure) return@withContext Result.failure(imagesRes.exceptionOrNull()!!)
            val imageUrls = imagesRes.getOrNull() ?: emptyList()
            if (imageUrls.isEmpty()) return@withContext Result.failure(Exception("無可下載圖片"))

            val mangaDir = java.io.File(context.filesDir, "manga/$mangaId/$chapterId")
            if (!mangaDir.exists()) mangaDir.mkdirs()

            val localPaths = mutableListOf<String>()
            val client = NetworkClient.imageOkHttpClient

            imageUrls.forEachIndexed { idx, url ->
                val ext = if (url.contains(".png")) "png" else "jpg"
                val file = java.io.File(mangaDir, "page_${idx + 1}.$ext")
                if (!file.exists() || file.length() == 0L) {
                    val tmpFile = java.io.File(mangaDir, "page_${idx + 1}.tmp")
                    val req = Request.Builder().url(url).build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.byteStream()?.use { input ->
                                java.io.FileOutputStream(tmpFile).use { output ->
                                    input.copyTo(output)
                                }
                            }

                            val options = android.graphics.BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            android.graphics.BitmapFactory.decodeFile(tmpFile.absolutePath, options)

                            if (options.outWidth > 0 && options.outHeight > 0) {
                                tmpFile.renameTo(file)
                            } else {
                                tmpFile.delete()
                                throw Exception("圖片第 ${idx + 1} 頁損毀或無效")
                            }
                        } else {
                            throw Exception("圖片第 ${idx + 1} 頁下載失敗: HTTP ${resp.code}")
                        }
                    }
                }
                localPaths.add("file://${file.absolutePath}")
            }

            if (localPaths.size == imageUrls.size) {
                val jsonArr = org.json.JSONArray()
                localPaths.forEach { jsonArr.put(it) }
                chapterDao.saveChapterDownloadPaths(mangaId, chapterId, jsonArr.toString())
                mangaDao.refreshDownloadedCount(mangaId)
            } else {
                throw Exception("下載頁數不符")
            }

            Result.success(localPaths.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun downloadAllChapters(mangaId: String) {
        val req = androidx.work.OneTimeWorkRequestBuilder<com.mangareader.app.worker.MangaDownloadWorker>()
            .setInputData(androidx.work.workDataOf("mangaId" to mangaId))
            .build()
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "download_manga_$mangaId",
            androidx.work.ExistingWorkPolicy.KEEP,
            req
        )
    }

    suspend fun processDownloadQueue(mangaId: String) {
        val manga = mangaDao.getMangaById(mangaId) ?: return
        val pending = chapterDao.getPendingDownloadChapters(mangaId)
        if (pending.isEmpty()) return

        var failed = 0
        try {
            pending.forEachIndexed { i, ch ->
                val statusText = if (failed > 0) "${ch.title} (已有 $failed 章失敗)" else ch.title
                _downloadStatus.value = MangaDownloadStatus(
                    mangaId = mangaId,
                    mangaTitle = manga.title,
                    current = i + 1,
                    total = pending.size,
                    currentChapterTitle = statusText,
                    isDownloading = true
                )
                try {
                    val result = downloadChapter(mangaId, ch.chapterId)
                    if (result.isFailure) failed++
                } catch (e: Exception) {
                    failed++
                }
            }
            if (failed > 0) {
                throw Exception("下載完成，但有 $failed 話失敗，請重新下載或檢查網路")
            }
        } finally {
            _downloadStatus.value = null
        }
    }

    suspend fun updateReadingProgress(mangaId: String, chapterId: String, chapterTitle: String, chapterIndex: Int, pageIndex: Int) {
        mangaDao.updateReadingProgress(mangaId, chapterId, chapterTitle, chapterIndex, pageIndex)
    }

    suspend fun deleteManga(manga: MangaEntity) = withContext(Dispatchers.IO) {
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("download_manga_${manga.id}")
        // Delete local files
        val dir = File(context.filesDir, "manga/${manga.id}")
        if (dir.exists()) dir.deleteRecursively()

        chapterDao.deleteChaptersByMangaId(manga.id)
        mangaDao.deleteManga(manga)
    }
}

package com.mangareader.app.data.repository

import android.content.Context
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

class MangaRepository(
    private val context: Context,
    private val mangaDao: MangaDao,
    private val chapterDao: MangaChapterDao
) {

    private val _downloadStatus = kotlinx.coroutines.flow.MutableStateFlow<MangaDownloadStatus?>(null)
    val downloadStatus: kotlinx.coroutines.flow.StateFlow<MangaDownloadStatus?> = _downloadStatus

    fun getAllManga(): Flow<List<MangaEntity>> = mangaDao.getAllMangaFlow()

    fun getMangaFlow(id: String): Flow<MangaEntity?> = mangaDao.getMangaFlow(id)

    fun getChaptersFlow(mangaId: String): Flow<List<MangaChapterEntity>> = chapterDao.getChaptersFlow(mangaId)

    suspend fun importMangaFromUrl(url: String, htmlContent: String? = null): Result<MangaEntity> = withContext(Dispatchers.IO) {
        try {
            val html = htmlContent ?: NetworkClient.getHtml(url)
            val parsed = ManhuarenParser.parseDetail(url, html)

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
                MangaChapterEntity(
                    mangaId = manga.id,
                    chapterIndex = ch.chapterIndex,
                    chapterId = ch.chapterId,
                    title = ch.title,
                    url = ch.url,
                    imageUrlsJson = prev?.imageUrlsJson,
                    localImagePathsJson = prev?.localImagePathsJson,
                    pageCount = prev?.pageCount ?: 0,
                    isDownloaded = prev?.isDownloaded ?: false
                )
            }
            chapterDao.insertChapters(chapterEntities)
            mangaDao.refreshDownloadedCount(manga.id)

            Result.success(manga)
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

            val existingChapters = chapterDao.getChapters(mangaId)
            val existingDownloadedMap = existingChapters.associateBy { it.chapterId }
            val existingUrls = existingChapters.map { it.url }.toSet()

            val newCount = parsed.chapters.count { !existingUrls.contains(it.url) }

            val updatedEntities = parsed.chapters.map { ch ->
                val prev = existingDownloadedMap[ch.chapterId]
                MangaChapterEntity(
                    mangaId = mangaId,
                    chapterIndex = ch.chapterIndex,
                    chapterId = ch.chapterId,
                    title = ch.title,
                    url = ch.url,
                    imageUrlsJson = prev?.imageUrlsJson,
                    localImagePathsJson = prev?.localImagePathsJson,
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
            mangaDao.refreshDownloadedCount(mangaId)

            Result.success(newCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChapterImages(mangaId: String, chapterIndex: Int, forceRefresh: Boolean = false): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val chapter = chapterDao.getChapterByIndex(mangaId, chapterIndex)
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
            chapterDao.saveChapterImages(mangaId, chapterIndex, jsonArr.toString(), images.size)

            Result.success(images)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadChapter(mangaId: String, chapterIndex: Int): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val imagesRes = getChapterImages(mangaId, chapterIndex)
            if (imagesRes.isFailure) return@withContext Result.failure(imagesRes.exceptionOrNull()!!)
            val imageUrls = imagesRes.getOrNull() ?: emptyList()
            if (imageUrls.isEmpty()) return@withContext Result.failure(Exception("無可下載圖片"))

            val mangaDir = File(context.filesDir, "manga/$mangaId/$chapterIndex")
            if (!mangaDir.exists()) mangaDir.mkdirs()

            val localPaths = mutableListOf<String>()
            val client = NetworkClient.imageOkHttpClient

            imageUrls.forEachIndexed { idx, url ->
                val ext = if (url.contains(".png")) "png" else "jpg"
                val file = File(mangaDir, "page_${idx + 1}.$ext")
                if (!file.exists() || file.length() == 0L) {
                    val req = Request.Builder().url(url).build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { input ->
                            FileOutputStream(file).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        throw Exception("圖片第 ${idx + 1} 頁下載失敗: HTTP ${resp.code}")
                    }
                }
                localPaths.add("file://${file.absolutePath}")
            }

            val jsonArr = JSONArray()
            localPaths.forEach { jsonArr.put(it) }
            chapterDao.saveChapterDownloadPaths(mangaId, chapterIndex, jsonArr.toString())
            mangaDao.refreshDownloadedCount(mangaId)

            Result.success(localPaths.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadAllChapters(mangaId: String) = withContext(Dispatchers.IO) {
        val manga = mangaDao.getMangaById(mangaId) ?: return@withContext
        val pending = chapterDao.getPendingDownloadChapters(mangaId)
        if (pending.isEmpty()) return@withContext

        try {
            pending.forEachIndexed { i, ch ->
                _downloadStatus.value = MangaDownloadStatus(
                    mangaId = mangaId,
                    mangaTitle = manga.title,
                    current = i + 1,
                    total = pending.size,
                    currentChapterTitle = ch.title,
                    isDownloading = true
                )
                try {
                    downloadChapter(mangaId, ch.chapterIndex)
                } catch (ignored: Exception) {}
            }
        } finally {
            _downloadStatus.value = null
        }
    }

    suspend fun updateReadingProgress(mangaId: String, chapterId: String, chapterTitle: String, chapterIndex: Int, pageIndex: Int) {
        mangaDao.updateReadingProgress(mangaId, chapterId, chapterTitle, chapterIndex, pageIndex)
    }

    suspend fun deleteManga(manga: MangaEntity) = withContext(Dispatchers.IO) {
        // Delete local files
        val dir = File(context.filesDir, "manga/${manga.id}")
        if (dir.exists()) dir.deleteRecursively()

        chapterDao.deleteChaptersByMangaId(manga.id)
        mangaDao.deleteManga(manga)
    }
}

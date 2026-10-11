package com.mangareader.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Query("SELECT * FROM manga ORDER BY lastReadTime DESC")
    fun getAllMangaFlow(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE id = :mangaId")
    suspend fun getMangaById(mangaId: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE id = :mangaId")
    fun getMangaFlow(mangaId: String): Flow<MangaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManga(manga: MangaEntity)

    @Update
    suspend fun updateManga(manga: MangaEntity)

    @Delete
    suspend fun deleteManga(manga: MangaEntity)

    @Query("UPDATE manga SET lastReadChapterId = :chapterId, lastReadChapterTitle = :title, lastReadChapterIndex = :chapterIndex, lastReadPageIndex = :pageIndex, lastReadTime = :time WHERE id = :mangaId")
    suspend fun updateReadingProgress(mangaId: String, chapterId: String, title: String, chapterIndex: Int, pageIndex: Int, time: Long = System.currentTimeMillis())

    @Query("UPDATE manga SET totalChapters = :total WHERE id = :mangaId")
    suspend fun updateTotalChapters(mangaId: String, total: Int)

    @Query("UPDATE manga SET latestChapterTitle = :title WHERE id = :mangaId")
    suspend fun updateLatestChapterTitle(mangaId: String, title: String)

    @Query("UPDATE manga SET downloadedChapters = (SELECT COUNT(*) FROM manga_chapters WHERE mangaId = :mangaId AND isDownloaded = 1) WHERE id = :mangaId")
    suspend fun refreshDownloadedCount(mangaId: String)
}

@Dao
interface MangaChapterDao {
    @Query("SELECT * FROM manga_chapters WHERE mangaId = :mangaId ORDER BY chapterIndex ASC")
    fun getChaptersFlow(mangaId: String): Flow<List<MangaChapterEntity>>

    @Query("SELECT * FROM manga_chapters WHERE mangaId = :mangaId ORDER BY chapterIndex ASC")
    suspend fun getChapters(mangaId: String): List<MangaChapterEntity>

    @Query("SELECT * FROM manga_chapters WHERE mangaId = :mangaId AND chapterIndex = :index")
    suspend fun getChapterByIndex(mangaId: String, index: Int): MangaChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<MangaChapterEntity>)

    @Query("UPDATE manga_chapters SET imageUrlsJson = :imagesJson, pageCount = :pageCount WHERE mangaId = :mangaId AND chapterIndex = :index")
    suspend fun saveChapterImages(mangaId: String, index: Int, imagesJson: String, pageCount: Int)

    @Query("UPDATE manga_chapters SET localImagePathsJson = :localPathsJson, isDownloaded = 1 WHERE mangaId = :mangaId AND chapterIndex = :index")
    suspend fun saveChapterDownloadPaths(mangaId: String, index: Int, localPathsJson: String)

    @Query("SELECT COUNT(*) FROM manga_chapters WHERE mangaId = :mangaId AND isDownloaded = 1")
    fun getDownloadedCountFlow(mangaId: String): Flow<Int>

    @Query("SELECT * FROM manga_chapters WHERE mangaId = :mangaId AND isDownloaded = 0 ORDER BY chapterIndex ASC")
    suspend fun getPendingDownloadChapters(mangaId: String): List<MangaChapterEntity>

    @Query("DELETE FROM manga_chapters WHERE mangaId = :mangaId")
    suspend fun deleteChaptersByMangaId(mangaId: String)
}

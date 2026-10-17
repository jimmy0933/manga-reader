package com.mangareader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manga")
data class MangaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val intro: String,
    val sourceUrl: String,
    val latestChapterTitle: String? = null,
    val lastReadChapterId: String? = null,
    val lastReadChapterTitle: String? = null,
    val lastReadChapterIndex: Int = 0,
    val lastReadPageIndex: Int = 0,
    val lastReadTime: Long = System.currentTimeMillis(),
    val totalChapters: Int = 0,
    val downloadedChapters: Int = 0,
    val isFavorite: Boolean = true
)

@Entity(tableName = "manga_chapters", primaryKeys = ["mangaId", "chapterId"])
data class MangaChapterEntity(
    val mangaId: String,
    val chapterIndex: Int,
    val chapterId: String,
    val title: String,
    val url: String,
    val imageUrlsJson: String? = null, // JSON array of remote URLs
    val localImagePathsJson: String? = null, // JSON array of local file paths
    val pageCount: Int = 0,
    val isDownloaded: Boolean = false
)

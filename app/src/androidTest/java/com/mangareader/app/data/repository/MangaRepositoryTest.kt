package com.mangareader.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mangareader.app.data.local.MangaChapterEntity
import com.mangareader.app.data.local.MangaDatabase
import com.mangareader.app.data.local.MangaEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MangaRepositoryTest {

    private lateinit var db: MangaDatabase
    private lateinit var repository: MangaRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, MangaDatabase::class.java
        ).build()
        repository = MangaRepository(context, db)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun emptyParse_doesNotWipeData() = runBlocking {
        val mangaId = "test_manga"

        // Insert mock data
        db.mangaDao().insertManga(
            MangaEntity(mangaId, "Test Manga", "Author", "", "", "url")
        )
        db.mangaChapterDao().insertChapters(
            listOf(
                MangaChapterEntity(mangaId, 0, "ch_1", "Chapter 1", "url1")
            )
        )

        // Try to update with empty chapters (simulated by syncing an empty html)
        repository.syncLatestChapters(mangaId)

        val chapters = db.mangaChapterDao().getChapters(mangaId)
        assertEquals(1, chapters.size)
        assertEquals("Chapter 1", chapters[0].title)
    }
}

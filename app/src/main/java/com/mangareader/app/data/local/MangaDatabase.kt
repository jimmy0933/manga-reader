package com.mangareader.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MangaEntity::class, MangaChapterEntity::class], version = 3, exportSchema = false)
abstract class MangaDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun mangaChapterDao(): MangaChapterDao

    companion object {
        @Volatile
        private var INSTANCE: MangaDatabase? = null


        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the new table
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `manga_chapters_new` (`mangaId` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, `chapterId` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `imageUrlsJson` TEXT, `localImagePathsJson` TEXT, `pageCount` INTEGER NOT NULL, `isDownloaded` INTEGER NOT NULL, PRIMARY KEY(`mangaId`, `chapterId`))"
                )

                // Copy the data
                database.execSQL(
                    "INSERT OR REPLACE INTO `manga_chapters_new` (`mangaId`, `chapterIndex`, `chapterId`, `title`, `url`, `imageUrlsJson`, `localImagePathsJson`, `pageCount`, `isDownloaded`) SELECT `mangaId`, `chapterIndex`, `chapterId`, `title`, `url`, `imageUrlsJson`, `localImagePathsJson`, `pageCount`, `isDownloaded` FROM `manga_chapters`"
                )

                // Remove the old table
                database.execSQL("DROP TABLE `manga_chapters`")

                // Change the table name to the correct one
                database.execSQL("ALTER TABLE `manga_chapters_new` RENAME TO `manga_chapters`")
            }
        }

        fun getDatabase(context: Context): MangaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MangaDatabase::class.java,
                    "manga_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

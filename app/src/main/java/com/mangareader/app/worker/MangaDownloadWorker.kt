package com.mangareader.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mangareader.app.MangaApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MangaDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val mangaId = inputData.getString("mangaId") ?: return@withContext Result.failure()

        val app = applicationContext as MangaApplication
        val repository = app.repository

        try {
            repository.processDownloadQueue(mangaId)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

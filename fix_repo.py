import sys
import re

repo = "./app/src/main/java/com/mangareader/app/data/repository/MangaRepository.kt"
with open(repo, "r") as f:
    content = f.read()

# Let's remove the object level getMutex completely and define a simple global mutexes map outside the class.
content = content.replace(
    """class MangaRepository(
    private val context: Context,
    private val database: MangaDatabase
) {""",
    """private val globalMangaMutexes = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
private fun getMangaMutex(mangaId: String) = globalMangaMutexes.getOrPut(mangaId) { kotlinx.coroutines.sync.Mutex() }

class MangaRepository(
    private val context: Context,
    private val database: MangaDatabase
) {"""
)

# Remove the broken class member declarations of getMutex
content = content.replace(
    """    private val chapterDao = database.mangaChapterDao()

    companion object {
        private val mangaMutexes = ConcurrentHashMap<String, Mutex>()
    }
    private fun getMutex(mangaId: String): Mutex = mangaMutexes.getOrPut(mangaId) { Mutex() }""",
    """    private val chapterDao = database.mangaChapterDao()"""
)

# And correctly reference getMangaMutex globally
content = content.replace(
    """    suspend fun syncLatestChapters(mangaId: String): Result<Int> = this@MangaRepository.getMutex(mangaId).withLock {
        withContext(Dispatchers.IO) {""",
    """    suspend fun syncLatestChapters(mangaId: String): Result<Int> = getMangaMutex(mangaId).withLock {
        withContext(Dispatchers.IO) {"""
)

content = content.replace(
    """                try {
                    this@MangaRepository.getMutex(mangaId).withLock {
                        if (mangaDao.getMangaById(mangaId) == null) { throw kotlinx.coroutines.CancellationException("Manga deleted") }
                    }""",
    """                try {
                    getMangaMutex(mangaId).withLock {
                        if (mangaDao.getMangaById(mangaId) == null) { throw kotlinx.coroutines.CancellationException("Manga deleted") }
                    }"""
)

content = content.replace(
    """    suspend fun deleteManga(manga: MangaEntity) = this@MangaRepository.getMutex(manga.id).withLock {
        withContext(Dispatchers.IO) {""",
    """    suspend fun deleteManga(manga: MangaEntity) = getMangaMutex(manga.id).withLock {
        withContext(Dispatchers.IO) {"""
)


with open(repo, "w") as f:
    f.write(content)

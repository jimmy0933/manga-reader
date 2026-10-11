package com.mangareader.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.mangareader.app.parser.MangaSearchResult
import com.mangareader.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MangaApplication
        val repository = app.repository

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val mangaList by repository.getAllManga().collectAsState(initial = emptyList())
                val downloadStatus by repository.downloadStatus.collectAsState()

                var showAddDialog by remember { mutableStateOf(false) }
                var showSearchDialog by remember { mutableStateOf(false) }
                var searchResults by remember { mutableStateOf<List<MangaSearchResult>>(emptyList()) }
                var isSearching by remember { mutableStateOf(false) }

                var inputUrl by remember { mutableStateOf("") }
                var isImporting by remember { mutableStateOf(false) }

                NavHost(navController = navController, startDestination = "mangashelf") {
                    // 1. 漫畫書架主頁
                    composable("mangashelf") {
                        MangaShelfScreen(
                            mangaList = mangaList,
                            downloadStatus = downloadStatus,
                            onMangaClick = { mangaId ->
                                // 進入該漫畫時先到目錄頁
                                navController.navigate("manga_detail/$mangaId")
                            },
                            onAddClick = { showAddDialog = true },
                            onSearchClick = { showSearchDialog = true },
                            onOpenWebClick = { navController.navigate("web_browser") },
                            onDownloadAll = { mangaId ->
                                lifecycleScope.launch {
                                    Toast.makeText(this@MainActivity, "已在背景開始整部漫畫下載！", Toast.LENGTH_SHORT).show()
                                    repository.downloadAllChapters(mangaId)
                                }
                            },
                            onDeleteManga = { manga ->
                                lifecycleScope.launch {
                                    repository.deleteManga(manga)
                                    Toast.makeText(this@MainActivity, "已從書架移除《${manga.title}》", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    // 2. 漫畫詳情與章節目錄
                    composable(
                        "manga_detail/{mangaId}",
                        arguments = listOf(navArgument("mangaId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val mangaId = backStackEntry.arguments?.getString("mangaId") ?: return@composable
                        val manga by repository.getMangaFlow(mangaId).collectAsState(initial = null)
                        val chapters by repository.getChaptersFlow(mangaId).collectAsState(initial = emptyList())

                        MangaDetailScreen(
                            manga = manga,
                            chapters = chapters,
                            downloadStatus = downloadStatus,
                            onBack = { navController.popBackStack() },
                            onChapterClick = { index ->
                                navController.navigate("manga_reader/$mangaId/$index")
                            },
                            onDownloadAll = {
                                lifecycleScope.launch {
                                    Toast.makeText(this@MainActivity, "已在背景開始整部下載！", Toast.LENGTH_SHORT).show()
                                    repository.downloadAllChapters(mangaId)
                                }
                            },
                            onSyncLatestChapters = {
                                lifecycleScope.launch {
                                    Toast.makeText(this@MainActivity, "正在檢查並同步最新話數...", Toast.LENGTH_SHORT).show()
                                    val result = repository.syncLatestChapters(mangaId)
                                    if (result.isSuccess) {
                                        val newCount = result.getOrNull() ?: 0
                                        if (newCount > 0) {
                                            Toast.makeText(this@MainActivity, "同步成功！新增了 $newCount 個最新章節", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(this@MainActivity, "目前已是最新話數，無新內容", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(this@MainActivity, "同步失敗：${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    }

                    // 3. 漫畫閱讀器
                    composable(
                        "manga_reader/{mangaId}/{chapterIndex}",
                        arguments = listOf(
                            navArgument("mangaId") { type = NavType.StringType },
                            navArgument("chapterIndex") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        val mangaId = backStackEntry.arguments?.getString("mangaId") ?: return@composable
                        val chapterIndex = backStackEntry.arguments?.getInt("chapterIndex") ?: 0

                        MangaReaderScreen(
                            mangaId = mangaId,
                            initialChapterIndex = chapterIndex,
                            repository = repository,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // 4. 內建線上瀏覽器
                    composable("web_browser") {
                        MangaWebBrowserScreen(
                            onBack = { navController.popBackStack() },
                            onImportHtml = { url, html ->
                                lifecycleScope.launch {
                                    Toast.makeText(this@MainActivity, "正在解析漫畫資訊...", Toast.LENGTH_SHORT).show()
                                    val result = repository.importMangaFromUrl(url, html)
                                    if (result.isSuccess) {
                                        Toast.makeText(this@MainActivity, "成功加入書架！", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    } else {
                                        Toast.makeText(this@MainActivity, "加入失敗：${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    }
                }

                // 搜尋對話框
                if (showSearchDialog) {
                    MangaSearchDialog(
                        onDismiss = { showSearchDialog = false },
                        onSearch = { query ->
                            isSearching = true
                            lifecycleScope.launch {
                                val res = repository.searchManga(query)
                                isSearching = false
                                if (res.isSuccess) {
                                    searchResults = res.getOrNull() ?: emptyList()
                                } else {
                                    Toast.makeText(this@MainActivity, "搜尋失敗：${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onSelectManga = { selected ->
                            showSearchDialog = false
                            lifecycleScope.launch {
                                Toast.makeText(this@MainActivity, "正在將《${selected.title}》加入書架...", Toast.LENGTH_SHORT).show()
                                val res = repository.importMangaFromUrl(selected.url)
                                if (res.isSuccess) {
                                    Toast.makeText(this@MainActivity, "成功加入書架！", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@MainActivity, "加入失敗：${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        searchResults = searchResults,
                        isSearching = isSearching
                    )
                }

                // 貼網址匯入對話框
                if (showAddDialog) {
                    AlertDialog(
                        onDismissRequest = { if (!isImporting) showAddDialog = false },
                        title = { Text("貼上漫畫人網址") },
                        text = {
                            Column {
                                Text("支援 manhuaren.com 漫畫主頁或目錄連結：", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = inputUrl,
                                    onValueChange = { inputUrl = it },
                                    label = { Text("漫畫網址 (https://www.manhuaren.com/...)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (isImporting) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                enabled = inputUrl.isNotBlank() && !isImporting,
                                onClick = {
                                    isImporting = true
                                    lifecycleScope.launch {
                                        val result = repository.importMangaFromUrl(inputUrl.trim())
                                        isImporting = false
                                        if (result.isSuccess) {
                                            Toast.makeText(this@MainActivity, "成功加入書架！", Toast.LENGTH_SHORT).show()
                                            showAddDialog = false
                                            inputUrl = ""
                                        } else {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "直接解析失敗：${result.exceptionOrNull()?.message}。可使用內建瀏覽器加入！",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            ) {
                                Text("加入")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = !isImporting,
                                onClick = { showAddDialog = false }
                            ) {
                                Text("取消")
                            }
                        }
                    )
                }
            }
        }
    }
}

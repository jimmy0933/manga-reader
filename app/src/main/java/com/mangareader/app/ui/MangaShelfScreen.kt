package com.mangareader.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mangareader.app.data.local.MangaEntity
import com.mangareader.app.data.repository.MangaDownloadStatus
import com.mangareader.app.parser.MangaSearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaShelfScreen(
    mangaList: List<MangaEntity>,
    downloadStatus: MangaDownloadStatus?,
    onMangaClick: (String) -> Unit,
    onAddClick: () -> Unit,
    onSearchClick: () -> Unit,
    onOpenWebClick: () -> Unit,
    onDownloadAll: (String) -> Unit,
    onDeleteManga: (MangaEntity) -> Unit
) {
    var mangaToDelete by remember { mutableStateOf<MangaEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的漫畫書架", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "搜尋漫畫")
                    }
                    IconButton(onClick = onOpenWebClick) {
                        Icon(Icons.Default.Language, contentDescription = "漫畫人網站瀏覽")
                    }
                    IconButton(onClick = onAddClick) {
                        Icon(Icons.Default.Add, contentDescription = "貼上漫畫網址")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 下載狀態進度條提示
            if (downloadStatus != null && downloadStatus.isDownloading) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "正在下載：《${downloadStatus.mangaTitle}》",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val percent = (downloadStatus.current * 100 / downloadStatus.total.coerceAtLeast(1))
                            Text(
                                text = "${downloadStatus.current} / ${downloadStatus.total} 話 ($percent%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "當前：${downloadStatus.currentChapterTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadStatus.current.toFloat() / downloadStatus.total.coerceAtLeast(1).toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (mangaList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(
                            Icons.Default.CollectionsBookmark,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("書架目前是空的", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "點選右上角 🔍 搜尋漫畫人、🌐 瀏覽網站，或 ➕ 貼上漫畫連結加入！",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onSearchClick) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("搜尋熱門漫畫")
                            }
                            OutlinedButton(onClick = onOpenWebClick) {
                                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("線上瀏覽")
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(mangaList, key = { it.id }) { manga ->
                        MangaGridCard(
                            manga = manga,
                            onClick = { onMangaClick(manga.id) },
                            onDetailClick = { onMangaClick(manga.id) },
                            onDownload = { onDownloadAll(manga.id) },
                            onDelete = { mangaToDelete = manga }
                        )
                    }
                }
            }
        }

        mangaToDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { mangaToDelete = null },
                title = { Text("確認刪除漫畫") },
                text = { Text("確定要將《${target.title}》從書架移除嗎？這將會連同已下載的離線圖片完全刪除。") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            onDeleteManga(target)
                            mangaToDelete = null
                        }
                    ) {
                        Text("確認刪除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mangaToDelete = null }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@Composable
fun MangaGridCard(
    manga: MangaEntity,
    onClick: () -> Unit,
    onDetailClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            // 漫畫封面
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = manga.coverUrl,
                    contentDescription = manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // 閱讀進度角標
                val progressText = if (!manga.lastReadChapterTitle.isNullOrBlank()) {
                    "讀至 ${manga.lastReadChapterTitle}"
                } else {
                    "尚未閱讀"
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = progressText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // 總話數與最新話角標
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(topEnd = 8.dp),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    val badge = if (!manga.latestChapterTitle.isNullOrBlank()) {
                        "最新：${manga.latestChapterTitle}"
                    } else {
                        "共 ${manga.totalChapters} 卷/話"
                    }
                    Text(
                        text = badge,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "作者：${manga.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDetailClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "目錄與詳情",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "下載",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "刪除",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

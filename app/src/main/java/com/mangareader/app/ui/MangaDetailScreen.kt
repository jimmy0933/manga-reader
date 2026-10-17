package com.mangareader.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangareader.app.data.local.MangaChapterEntity
import com.mangareader.app.data.local.MangaEntity
import com.mangareader.app.data.repository.MangaDownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailScreen(
    manga: MangaEntity?,
    chapters: List<MangaChapterEntity>,
    downloadStatus: MangaDownloadStatus?,
    onBack: () -> Unit,
    onChapterClick: (String) -> Unit,
    onDownloadAll: () -> Unit,
    onSyncLatestChapters: () -> Unit
) {
    // 預設為倒序（最新話例如第889話排在最上方）
    var isReverseOrder by remember { mutableStateOf(true) }

    // 進入詳情頁時自動在背景檢查並同步最新話數
    LaunchedEffect(manga?.id) {
        if (manga != null) {
            onSyncLatestChapters()
        }
    }

    val displayChapters = remember(chapters, isReverseOrder) {
        if (isReverseOrder) chapters.reversed() else chapters
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = manga?.title ?: "漫畫詳情",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onSyncLatestChapters) {
                        Icon(Icons.Default.Sync, contentDescription = "同步最新話數")
                    }
                    IconButton(onClick = onDownloadAll) {
                        Icon(Icons.Default.Download, contentDescription = "下載整部漫畫")
                    }
                }
            )
        }
    ) { padding ->
        if (manga == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 漫畫簡介卡片
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = manga.coverUrl,
                                contentDescription = manga.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(125.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = manga.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "作者：${manga.author}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val statusStr = if (!manga.latestChapterTitle.isNullOrBlank()) {
                                    "更新至：${manga.latestChapterTitle} (目錄共 ${chapters.size} 卷/話)"
                                } else {
                                    "狀態：共 ${chapters.size} 卷/話"
                                }
                                Text(
                                    text = statusStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (manga.downloadedChapters > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "已下載：${manga.downloadedChapters} 話",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        // 即時下載進度顯示
                        if (downloadStatus != null && downloadStatus.isDownloading && downloadStatus.mangaId == manga.id) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "正在下載：${downloadStatus.currentChapterTitle}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${downloadStatus.current}/${downloadStatus.total} 話",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { downloadStatus.current.toFloat() / downloadStatus.total.coerceAtLeast(1).toFloat() },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        if (manga.intro.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = manga.intro,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 繼續閱讀按鈕（秒開至之前看的章節與頁數）
                        val lastChapterId = manga.lastReadChapterId ?: chapters.lastOrNull()?.chapterId ?: ""
                        val readBtnText = if (!manga.lastReadChapterTitle.isNullOrBlank()) {
                            "繼續閱讀：${manga.lastReadChapterTitle} (第 ${manga.lastReadPageIndex + 1} 頁)"
                        } else {
                            "開始閱讀第 1 話"
                        }

                        Button(
                            onClick = { onChapterClick(lastChapterId) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(readBtnText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDownloadAll,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("下載 (${manga.downloadedChapters}/${manga.totalChapters})")
                            }

                            OutlinedButton(
                                onClick = onSyncLatestChapters,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("同步更新")
                            }
                        }
                    }
                }

                // 目錄標題列與排序切換
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "章節目錄 (共 ${chapters.size} 卷/話)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "早期章節為單行本卷次，近期為單話連載",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    TextButton(onClick = { isReverseOrder = !isReverseOrder }) {
                        Icon(
                            if (isReverseOrder) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isReverseOrder) "倒序" else "正序")
                    }
                }

                HorizontalDivider()

                // 章節列表
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(displayChapters, key = { "${it.mangaId}_${it.chapterIndex}" }) { ch ->
                        val isCurrentRead = (ch.chapterIndex == manga.lastReadChapterIndex)
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = ch.title,
                                    fontWeight = if (isCurrentRead) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrentRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingContent = {
                                if (ch.isDownloaded) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "已下載離線",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onChapterClick(ch.chapterId) }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

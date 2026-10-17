package com.mangareader.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.mangareader.app.data.local.MangaChapterEntity
import com.mangareader.app.data.repository.MangaRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MangaReaderScreen(
    mangaId: String,
    initialChapterId: String,
    repository: MangaRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("manga_reader_prefs", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    val chapters by repository.getChaptersFlow(mangaId).collectAsState(initial = emptyList())
    var chapterIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(chapters, initialChapterId) {
        if (chapters.isNotEmpty() && chapterIndex == 0) {
            val idx = chapters.indexOfFirst { it.chapterId == initialChapterId }
            if (idx != -1) {
                chapterIndex = idx
            }
        }
    }

    val chapter = chapters.getOrNull(chapterIndex)

    val hasPrevious = chapterIndex > 0
    val hasNext = chapters.isNotEmpty() && chapterIndex < chapters.size - 1

    // 0 = 上下滑動翻頁 (Vertical Pager - 預設), 1 = 垂直連續條漫 (Vertical Scroll), 2 = 左右滑動翻頁 (Horizontal Pager)
    var readMode by remember { mutableStateOf(prefs.getInt("read_mode", 0)) }
    fun updateReadMode(newMode: Int) {
        readMode = newMode
        prefs.edit().putInt("read_mode", newMode).apply()
    }

    // 圖片載入狀態
    var imageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingImages by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 介面控制面板顯示切換 (預設為 false 徹底隱藏上下黑條，提供全螢幕乾淨沉浸式閱讀)
    var showControls by remember { mutableStateOf(false) }

    // 載入當前章節的圖片
    LaunchedEffect(mangaId, chapterIndex, chapter?.url) {
        imageUrls = emptyList() // 清除前一章圖片避免殘留
        isLoadingImages = true
        errorMessage = null
        val result = repository.getChapterImages(mangaId, chapter?.chapterId ?: "")
        if (result.isSuccess) {
            imageUrls = result.getOrNull() ?: emptyList()
        } else {
            errorMessage = result.exceptionOrNull()?.message ?: "載入圖片失敗"
        }
        isLoadingImages = false
    }

    // 各模式的滾動與翻頁控制器
    val verticalPagerState = rememberPagerState(pageCount = { imageUrls.size.coerceAtLeast(1) })
    val horizontalPagerState = rememberPagerState(pageCount = { imageUrls.size.coerceAtLeast(1) })
    val lazyListState = rememberLazyListState()

    // 當閱讀進度改變時記憶頁數
    var currentPageIndex by remember { mutableStateOf(0) }

    // 同步 Pager / LazyList 的滾動狀態到 currentPageIndex (若非復原中)
    var isRestoring by remember { mutableStateOf(false) }

    LaunchedEffect(verticalPagerState.currentPage, lazyListState.firstVisibleItemIndex, horizontalPagerState.currentPage, readMode) {
        if (!isRestoring && imageUrls.isNotEmpty()) {
            val newIndex = when (readMode) {
                0 -> verticalPagerState.currentPage
                1 -> lazyListState.firstVisibleItemIndex
                else -> horizontalPagerState.currentPage
            }
            currentPageIndex = newIndex.coerceIn(0, (imageUrls.size - 1).coerceAtLeast(0))
        }
    }

    // 翻頁輔助函式（支援向上/向下滑動或點擊上下區域）
    fun goToPreviousPage() {
        if (currentPageIndex > 0) {
            scope.launch {
                when (readMode) {
                    0 -> verticalPagerState.animateScrollToPage(currentPageIndex - 1)
                    1 -> lazyListState.animateScrollToItem((currentPageIndex - 1).coerceAtLeast(0))
                    else -> horizontalPagerState.animateScrollToPage(currentPageIndex - 1)
                }
            }
        } else if (hasPrevious) {
            chapterIndex--
        }
    }

    fun goToNextPage() {
        if (currentPageIndex < imageUrls.size - 1) {
            scope.launch {
                when (readMode) {
                    0 -> verticalPagerState.animateScrollToPage(currentPageIndex + 1)
                    1 -> lazyListState.animateScrollToItem((currentPageIndex + 1).coerceAtMost(imageUrls.size - 1))
                    else -> horizontalPagerState.animateScrollToPage(currentPageIndex + 1)
                }
            }
        } else if (hasNext) {
            chapterIndex++
        }
    }

    // 進入章節時恢復上次閱讀頁數；切換章節時一律從第 1 頁 (index 0) 開始
    var isFirstLaunch by remember { mutableStateOf(true) }

    // 遷移舊的 chapterIndex 進度到 chapterId
    LaunchedEffect(chapter) {
        chapter?.let {
            if (!prefs.contains("page_${mangaId}_${it.chapterId}") && prefs.contains("page_${mangaId}_${chapterIndex}")) {
                val oldProgress = prefs.getInt("page_${mangaId}_${chapterIndex}", 0)
                prefs.edit().putInt("page_${mangaId}_${it.chapterId}", oldProgress).apply()
                prefs.edit().remove("page_${mangaId}_${chapterIndex}").apply()
            }
        }
    }

    // 初始化時與切換模式時，同步當前的 targetPage 到 Pager/List
    LaunchedEffect(chapterIndex, imageUrls, readMode) {
        if (imageUrls.isNotEmpty()) {
            isRestoring = true
            val targetPage = if (isFirstLaunch) {
                isFirstLaunch = false
                // 如果已經遷移過了，就讀取新的 id key，否則會先 fallback 到舊的（或者0）以防萬一
                val savedPage = if (prefs.contains("page_${mangaId}_${chapter?.chapterId ?: ""}")) {
                    prefs.getInt("page_${mangaId}_${chapter?.chapterId ?: ""}", 0)
                } else {
                    prefs.getInt("page_${mangaId}_${chapterIndex}", 0)
                }
                savedPage.coerceIn(0, imageUrls.size - 1)
            } else {
                currentPageIndex.coerceIn(0, imageUrls.size - 1)
            }

            // Mode changed during reading, use current page; Chapter changed, reset to 0
            val actualTarget = if (targetPage >= imageUrls.size) 0 else targetPage

            when (readMode) {
                0 -> verticalPagerState.scrollToPage(actualTarget)
                1 -> lazyListState.scrollToItem(actualTarget)
                else -> horizontalPagerState.scrollToPage(actualTarget)
            }
            currentPageIndex = actualTarget
            isRestoring = false
        }
    }

    LaunchedEffect(chapterIndex) {
        if (!isFirstLaunch) {
            currentPageIndex = 0
            isRestoring = true
        }
    }

    LaunchedEffect(chapterIndex, currentPageIndex) {
        if (imageUrls.isNotEmpty() && !isRestoring) {
            prefs.edit().putInt("page_${mangaId}_${chapter?.chapterId ?: ""}", currentPageIndex).apply()
            chapter?.let {
                repository.updateReadingProgress(mangaId, it.chapterId, it.title, chapterIndex, currentPageIndex)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(readMode, currentPageIndex, imageUrls.size, hasPrevious, hasNext) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val h = size.height
                        val w = size.width
                        when (readMode) {
                            0 -> { // 上下滑動翻頁模式：點擊上半部上一頁，點擊下半部下一頁，中間 40% 切換工具列
                                when {
                                    tapOffset.y < h * 0.32f -> goToPreviousPage()
                                    tapOffset.y > h * 0.68f -> goToNextPage()
                                    else -> showControls = !showControls
                                }
                            }
                            2 -> { // 左右翻頁模式：點擊左側上一頁，點擊右側下一頁，中間 40% 切換工具列
                                when {
                                    tapOffset.x < w * 0.32f -> goToPreviousPage()
                                    tapOffset.x > w * 0.68f -> goToNextPage()
                                    else -> showControls = !showControls
                                }
                            }
                            else -> { // 連續條漫模式：點擊任意處切換工具列
                                showControls = !showControls
                            }
                        }
                    }
                )
            }
    ) {
        // 內容區域
        if (isLoadingImages) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在解析並載入漫畫圖片...", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = errorMessage ?: "載入失敗", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        scope.launch {
                            isLoadingImages = true
                            errorMessage = null
                            val res = repository.getChapterImages(mangaId, chapter?.chapterId ?: "", forceRefresh = true)
                            if (res.isSuccess) {
                                imageUrls = res.getOrNull() ?: emptyList()
                            } else {
                                errorMessage = res.exceptionOrNull()?.message
                            }
                            isLoadingImages = false
                        }
                    }) {
                        Text("重新載入")
                    }
                }
            }
        } else if (imageUrls.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("此話無圖片內容", color = Color.White)
            }
        } else {
            when (readMode) {
                // 模式 0：上下滑動切換頁次 (Vertical Pager - 預設)
                0 -> {
                    VerticalPager(
                        state = verticalPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            MangaImageItem(
                                imageUrl = imageUrls[page],
                                pageNumber = page + 1,
                                totalPages = imageUrls.size,
                                isVerticalMode = false
                            )
                        }
                    }
                }

                // 模式 1：連續垂直滾動條漫 (Vertical Scroll)
                1 -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(
                            count = imageUrls.size,
                            key = { idx -> "${chapterIndex}_$idx" }
                        ) { idx ->
                            MangaImageItem(
                                imageUrl = imageUrls[idx],
                                pageNumber = idx + 1,
                                totalPages = imageUrls.size,
                                isVerticalMode = true
                            )
                        }

                        item(key = "footer") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (hasNext) {
                                    Button(
                                        onClick = { chapterIndex++ },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("進入下一話")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                                    }
                                } else {
                                    Text("— 已是最新一話 —", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // 模式 2：左右翻頁模式 (Horizontal Pager)
                else -> {
                    HorizontalPager(
                        state = horizontalPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            MangaImageItem(
                                imageUrl = imageUrls[page],
                                pageNumber = page + 1,
                                totalPages = imageUrls.size,
                                isVerticalMode = false
                            )
                        }
                    }
                }
            }
        }

        // 當上下控制條隱藏時，僅在右下角顯示極簡半透明頁數小標籤（不遮擋畫面）
        if (!showControls && imageUrls.isNotEmpty()) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(12.dp)
            ) {
                Text(
                    text = "${currentPageIndex + 1} / ${imageUrls.size}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // 頂部控制面板（輕觸螢幕時浮現，再次點擊隱藏）
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chapter?.title ?: "漫畫閱讀",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (imageUrls.isNotEmpty()) {
                            Text(
                                text = "第 ${currentPageIndex + 1} / ${imageUrls.size} 頁",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // 閱讀模式循環切換按鈕 (上下翻頁 -> 連續條漫 -> 左右翻頁)
                    IconButton(onClick = { updateReadMode((readMode + 1) % 3) }) {
                        Icon(
                            when (readMode) {
                                0 -> Icons.Default.SwapVert
                                1 -> Icons.Default.ViewAgenda
                                else -> Icons.Default.ViewCarousel
                            },
                            contentDescription = "切換閱讀模式",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // 底部控制面板（輕觸螢幕時浮現，再次點擊隱藏）
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // 滑桿進度跳轉
                    if (imageUrls.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${currentPageIndex + 1}",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.width(36.dp)
                            )
                            Slider(
                                value = currentPageIndex.toFloat(),
                                onValueChange = { targetPage ->
                                    val page = targetPage.toInt().coerceIn(0, imageUrls.size - 1)
                                    scope.launch {
                                        when (readMode) {
                                            0 -> verticalPagerState.scrollToPage(page)
                                            1 -> lazyListState.scrollToItem(page)
                                            else -> horizontalPagerState.scrollToPage(page)
                                        }
                                    }
                                },
                                valueRange = 0f..(imageUrls.size - 1).toFloat(),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${imageUrls.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.width(36.dp)
                            )
                        }
                    }

                    // 上一頁 / 下一頁 切換與頁碼顯示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { goToPreviousPage() },
                            enabled = currentPageIndex > 0 || hasPrevious,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.DarkGray
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                            Text("上一頁")
                        }

                        Text(
                            text = if (imageUrls.isNotEmpty()) "第 ${currentPageIndex + 1} / ${imageUrls.size} 頁" else "",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )

                        TextButton(
                            onClick = { goToNextPage() },
                            enabled = currentPageIndex < imageUrls.size - 1 || hasNext,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.DarkGray
                            )
                        ) {
                            Text("下一頁")
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 上一話 / 下一話切換與模式文字
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { if (hasPrevious) chapterIndex-- },
                            enabled = hasPrevious,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFF90CAF9),
                                disabledContentColor = Color.DarkGray
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                            Text("上一話")
                        }

                        Text(
                            text = when (readMode) {
                                0 -> "上下翻頁模式"
                                1 -> "連續條漫模式"
                                else -> "左右翻頁模式"
                            },
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )

                        TextButton(
                            onClick = { if (hasNext) chapterIndex++ },
                            enabled = hasNext,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFF90CAF9),
                                disabledContentColor = Color.DarkGray
                            )
                        ) {
                            Text("下一話")
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MangaImageItem(
    imageUrl: String,
    pageNumber: Int,
    totalPages: Int,
    isVerticalMode: Boolean
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(imageUrl) {
        scale = 1f
        offset = Offset.Zero
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 3f)
        if (scale > 1f) {
            val maxX = (scale - 1f) * 500f
            val maxY = (scale - 1f) * 500f
            val newOffset = offset + panChange
            offset = androidx.compose.ui.geometry.Offset(
                newOffset.x.coerceIn(-maxX, maxX),
                newOffset.y.coerceIn(-maxY, maxY)
            )
        } else {
            offset = androidx.compose.ui.geometry.Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isVerticalMode) Modifier.fillMaxHeight() else Modifier.wrapContentHeight()
            )
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .transformable(
                state = transformState,
                enabled = true // 永遠啟用，transformable 預設就會忽略單指拖曳，所以不會干擾 Pager 翻頁
            ),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = "第 $pageNumber 頁",
            contentScale = if (isVerticalMode) ContentScale.FillWidth else ContentScale.Fit,
            modifier = if (isVerticalMode) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$pageNumber / $totalPages",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFF221111)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("第 $pageNumber 頁圖片載入失敗", color = Color.Red, fontSize = 13.sp)
                }
            }
        )
    }
}

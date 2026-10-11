package com.mangareader.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mangareader.app.network.NetworkClient

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MangaWebBrowserScreen(
    onBack: () -> Unit,
    onImportHtml: (url: String, html: String) -> Unit
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var currentUrl by remember { mutableStateOf(NetworkClient.BASE_URL) }
    var pageTitle by remember { mutableStateOf("漫畫人") }
    var isLoading by remember { mutableStateOf(false) }

    val isMangaPage = remember(currentUrl) {
        currentUrl.contains("/manhua-") || currentUrl.matches(Regex(""".*/m\d+/?.*"""))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = pageTitle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = currentUrl,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (webView?.canGoBack() == true) {
                            webView?.goBack()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新整理")
                    }
                }
            )
        },
        floatingActionButton = {
            if (isMangaPage) {
                ExtendedFloatingActionButton(
                    onClick = {
                        webView?.evaluateJavascript("document.documentElement.outerHTML") { rawHtml ->
                            if (rawHtml != null && rawHtml != "null") {
                                val unescaped = try {
                                    org.json.JSONTokener(rawHtml).nextValue().toString()
                                } catch (e: Exception) {
                                    rawHtml.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                                }
                                onImportHtml(currentUrl, unescaped)
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
                    text = { Text("加入書架") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            userAgentString = NetworkClient.USER_AGENT
                            useWideViewPort = true
                            loadWithOverviewMode = true
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                url?.let { currentUrl = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                url?.let { currentUrl = it }
                                view?.title?.let { pageTitle = it }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                title?.let { pageTitle = it }
                            }
                        }
                        loadUrl(NetworkClient.BASE_URL)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

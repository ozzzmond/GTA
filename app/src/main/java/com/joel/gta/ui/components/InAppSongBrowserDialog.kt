package com.joel.gta.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joel.gta.data.scraper.ScrapedSong
import com.joel.gta.data.scraper.WebScraperEngine
import com.joel.gta.ui.theme.LocalGtaColors
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppSongBrowserDialog(
    initialUrl: String,
    sourceName: String = "External Source",
    onDismissRequest: () -> Unit,
    onSongCaptured: (ScrapedSong) -> Unit
) {
    val customColors = LocalGtaColors.current
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf(sourceName) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            topBar = {
                Surface(
                    color = customColors.surfaceBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismissRequest) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Browser",
                                    tint = customColors.textPrimary
                                )
                            }

                            // Navigation controls
                            IconButton(
                                onClick = { webViewInstance?.goBack() },
                                enabled = canGoBack
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (canGoBack) customColors.textPrimary else customColors.textSecondary.copy(alpha = 0.4f)
                                )
                            }

                            IconButton(
                                onClick = { webViewInstance?.goForward() },
                                enabled = canGoForward
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Forward",
                                    tint = if (canGoForward) customColors.textPrimary else customColors.textSecondary.copy(alpha = 0.4f)
                                )
                            }

                            IconButton(onClick = { webViewInstance?.reload() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload",
                                    tint = customColors.textSecondary
                                )
                            }

                            // Current page / URL title pill
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = pageTitle.ifBlank { sourceName },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = customColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Loading progress indicator
                        if (isLoading) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = customColors.chordAccent,
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        val webView = webViewInstance ?: return@ExtendedFloatingActionButton
                        isCapturing = true

                        val extractionJs = """
                            (function() {
                                var docTitle = document.title || '';
                                
                                // 1. CCLI SongSelect chord sheet
                                var ccli = document.querySelector('.chord-sheet, .lead-sheet, [data-testid="chord-sheet"], .ccli-chord-sheet');
                                if (ccli && ccli.innerText.trim().length > 20) {
                                    return JSON.stringify({ title: docTitle, content: ccli.innerText });
                                }
                                
                                // 2. Preformatted chord block (<pre>)
                                var pre = document.querySelector('pre');
                                if (pre && pre.innerText.trim().length > 20) {
                                    return JSON.stringify({ title: docTitle, content: pre.innerText });
                                }
                                
                                // 3. Ultimate Guitar js-store
                                var ugStore = document.querySelector('.js-store');
                                if (ugStore && ugStore.getAttribute('data-content')) {
                                    try {
                                        var d = JSON.parse(ugStore.getAttribute('data-content'));
                                        var ugContent = d.store.page.data.tab_view.wiki_tab.content;
                                        if (ugContent) {
                                            return JSON.stringify({ title: docTitle, content: ugContent });
                                        }
                                    } catch(e) {}
                                }
                                
                                // 4. Article or main song content
                                var article = document.querySelector('article, main, #song-content, .chord-content, .entry-content');
                                if (article && article.innerText.trim().length > 20) {
                                    return JSON.stringify({ title: docTitle, content: article.innerText });
                                }
                                
                                // 5. Body fallback
                                return JSON.stringify({ title: docTitle, content: document.body.innerText });
                            })()
                        """.trimIndent()

                        webView.evaluateJavascript(extractionJs) { resultJson ->
                            isCapturing = false
                            try {
                                val rawJson = if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                                    // Java String unescape from evaluateJavascript
                                    val unquoted = resultJson.substring(1, resultJson.length - 1)
                                    WebScraperEngine.unescapeJson(unquoted)
                                } else {
                                    resultJson
                                }

                                val parsedObj = JSONObject(rawJson)
                                val extractedTitle = parsedObj.optString("title", pageTitle)
                                val extractedContent = parsedObj.optString("content", "")

                                if (extractedContent.isNotBlank()) {
                                    // Parse content into ScrapedSong
                                    val cleanedRaw = WebScraperEngine.sanitizeUgContent(extractedContent)
                                    val (songTitle, artist) = WebScraperEngine.splitTitleAndArtist(
                                        WebScraperEngine.cleanWebPageTitle(extractedTitle)
                                    )

                                    val defaultTag = when {
                                        currentUrl.contains("ccli") || currentUrl.contains("songselect") -> "Worship, Church"
                                        currentUrl.contains("opmtunes") -> "OPM"
                                        else -> ""
                                    }

                                    val captured = ScrapedSong(
                                        title = songTitle.ifBlank { "Imported Song" },
                                        artist = artist,
                                        rawContent = cleanedRaw,
                                        sourceUrl = currentUrl
                                    )
                                    onSongCaptured(captured)
                                }
                            } catch (e: Exception) {
                                // Fallback: capture page title
                                val (songTitle, artist) = WebScraperEngine.splitTitleAndArtist(
                                    WebScraperEngine.cleanWebPageTitle(pageTitle)
                                )
                                onSongCaptured(
                                    ScrapedSong(
                                        title = songTitle,
                                        artist = artist,
                                        rawContent = "",
                                        sourceUrl = currentUrl
                                    )
                                )
                            }
                        }
                    },
                    icon = {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Download, contentDescription = "Import Song", tint = Color.Black)
                        }
                    },
                    text = {
                        Text(
                            text = if (isCapturing) "Importing..." else "Import Song",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    containerColor = customColors.chordAccent,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
                )
            },
            containerColor = customColors.canvasBackground
        ) { innerPadding ->
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        // Persist cookies across sessions so users don't have to log into CCLI repeatedly
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                isLoading = newProgress < 100
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) {
                                    pageTitle = title
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                url?.let { currentUrl = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                url?.let { currentUrl = it }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                view?.title?.takeIf { it.isNotBlank() }?.let { pageTitle = it }
                            }
                        }

                        loadUrl(initialUrl)
                        webViewInstance = this
                    }
                },
                update = { webView ->
                    webViewInstance = webView
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

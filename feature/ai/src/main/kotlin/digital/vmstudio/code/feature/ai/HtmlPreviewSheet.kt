package digital.vmstudio.code.feature.ai

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ui.component.VmErrorPanel

/**
 * Integrated Web & API Live Preview Deck.
 * Renders static workspace HTML files or connects directly to live VPS web servers (ports 3000, 5173, etc.).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HtmlPreviewSheet(
    serverId: String,
    path: String,
    onDismiss: () -> Unit,
    onOpenInBrowser: ((String) -> Unit)? = null,
    viewModel: HtmlPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isPageLoading by remember { mutableStateOf(false) }

    LaunchedEffect(serverId, path) {
        viewModel.init(serverId, path)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // Header bar with URL and Quick Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.mode == PreviewMode.FILE) {
                            state.path.substringAfterLast('/')
                        } else {
                            state.liveUrl
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (state.mode == PreviewMode.FILE) "Workspace file" else "Live VPS server",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::toggleMobileView) {
                        Icon(
                            imageVector = if (state.isMobileView) Icons.Default.Devices
                            else Icons.Default.PhoneAndroid,
                            contentDescription = "Toggle viewport",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(
                        onClick = {
                            if (state.mode == PreviewMode.FILE) {
                                viewModel.refresh()
                            } else {
                                webViewInstance?.reload()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (state.mode == PreviewMode.PORT && state.liveUrl.isNotBlank()) {
                        IconButton(
                            onClick = {
                                if (onOpenInBrowser != null) {
                                    onOpenInBrowser(state.liveUrl)
                                    onDismiss()
                                } else {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.liveUrl))
                                        context.startActivity(intent)
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open in browser",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                        )
                    }
                }
            }

            // Port and Mode selection chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.path.endsWith(".html", ignoreCase = true) ||
                    state.path.endsWith(".htm", ignoreCase = true)
                ) {
                    FilterChip(
                        selected = state.mode == PreviewMode.FILE,
                        onClick = { viewModel.loadStaticFile(state.path) },
                        label = { Text("File Preview") },
                    )
                }

                state.availablePorts.forEach { port ->
                    val isSelected = state.mode == PreviewMode.PORT && state.selectedPort == port
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectPort(port) },
                        label = { Text(":$port") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Main Web Preview Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(if (state.isMobileView) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                val previewModifier = if (state.isMobileView) {
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = 390.dp)
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                } else {
                    Modifier.fillMaxSize()
                }

                Box(modifier = previewModifier) {
                    when {
                        state.isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        state.error != null -> {
                            VmErrorPanel(
                                error = state.error!!,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp),
                            )
                        }
                        state.mode == PreviewMode.FILE && state.htmlContent != null -> {
                            SandboxedWebView(
                                html = state.htmlContent!!,
                                onWebViewCreated = { webViewInstance = it },
                            )
                        }
                        state.mode == PreviewMode.PORT && state.liveUrl.isNotBlank() -> {
                            LiveUrlWebView(
                                url = state.liveUrl,
                                onLoadingChange = { isPageLoading = it },
                                onWebViewCreated = { webViewInstance = it },
                            )
                        }
                    }

                    if (isPageLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

            // Bottom status bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (state.mode == PreviewMode.FILE) "Local rendering engine"
                        else "Proxy: ${state.serverHost}:${state.selectedPort}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (state.isMobileView) "Mobile (390px)" else "Desktop (100%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Hosts the preview sheet and returns the callback that opens it.
 */
@Composable
internal fun rememberHtmlPreview(
    serverId: String?,
    onOpenInBrowser: ((String) -> Unit)? = null,
): ((String) -> Unit)? {
    var path by remember { mutableStateOf<String?>(null) }
    val effectiveServerId = serverId ?: digital.vmstudio.code.core.sftp.fs.RemoteFileSystem.LOCAL_SERVER_ID
    val current = path
    if (current != null) {
        HtmlPreviewSheet(
            serverId = effectiveServerId,
            path = current,
            onDismiss = { path = null },
            onOpenInBrowser = onOpenInBrowser,
        )
    }
    return { selected: String -> path = selected }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SandboxedWebView(
    html: String,
    onWebViewCreated: (WebView) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                onWebViewCreated(this)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LiveUrlWebView(
    url: String,
    onLoadingChange: (Boolean) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        onLoadingChange(true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        onLoadingChange(false)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        onLoadingChange(false)
                    }
                }
                onWebViewCreated(this)
            }
        },
        update = { webView ->
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}

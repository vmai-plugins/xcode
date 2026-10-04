package digital.vmstudio.code.feature.ai

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ui.component.VmErrorPanel

/**
 * Renders an HTML file from the server inside the chat, so a page the agent just
 * wrote can be checked without leaving the app.
 *
 * The WebView is sandboxed: scripts run so the page behaves as written, but it has
 * no file or content access and no JavaScript bridge into the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HtmlPreviewSheet(
    serverId: String,
    path: String,
    onDismiss: () -> Unit,
    viewModel: HtmlPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(serverId, path) { viewModel.load(serverId, path) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = path.substringAfterLast('/'),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                when (val current = state) {
                    HtmlPreviewState.Loading -> CircularProgressIndicator()
                    is HtmlPreviewState.Failed -> VmErrorPanel(
                        error = current.error,
                        modifier = Modifier.padding(16.dp),
                    )
                    is HtmlPreviewState.Ready -> SandboxedWebView(html = current.html)
                }
            }
        }
    }
}

/**
 * Hosts the preview sheet and returns the callback that opens it, or null when
 * there is no server to read the file from (a chat reopened from history).
 */
@Composable
internal fun rememberHtmlPreview(serverId: String?): ((String) -> Unit)? {
    var path by remember { mutableStateOf<String?>(null) }
    val current = path
    if (serverId != null && current != null) {
        HtmlPreviewSheet(serverId = serverId, path = current, onDismiss = { path = null })
    }
    return serverId?.let { { selected: String -> path = selected } }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SandboxedWebView(html: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = true
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        },
    )
}

private val PREVIEW_HEIGHT = 560.dp

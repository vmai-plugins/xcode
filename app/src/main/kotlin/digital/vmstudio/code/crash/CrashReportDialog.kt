package digital.vmstudio.code.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.theme.VmTheme

/** Shown once after a crash: copy the report to send it, or dismiss it. */
@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashReporter.pending(context)) }
    val text = report ?: return

    fun close() {
        CrashReporter.clear(context)
        report = null
    }

    VmDialog(
        title = "X-Codes closed unexpectedly",
        onDismiss = ::close,
        confirmLabel = "Copy report",
        onConfirm = {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("X-Codes crash report", text))
            Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
            close()
        },
        dismissLabel = "Dismiss",
    ) {
        Text(
            text = "A report was saved. Copy it and paste it into your chat with Claude so the " +
                "cause can be fixed. It has no passwords or keys.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = text.lineSequence().take(PREVIEW_LINES).joinToString("\n"),
            style = VmTheme.code.mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}

private const val PREVIEW_LINES = 12

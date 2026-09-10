package digital.vmstudio.code.feature.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ssh.metrics.ProcessItem
import digital.vmstudio.code.core.ssh.metrics.ServerMetrics
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmDivider
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.component.VmInlineError
import digital.vmstudio.code.core.ui.component.VmProgress
import digital.vmstudio.code.core.ui.component.VmSectionHeader
import digital.vmstudio.code.core.ui.component.VmStatus
import digital.vmstudio.code.core.ui.component.VmStatusBadge
import digital.vmstudio.code.core.ui.theme.VmTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Server telemetry, ported from the previous app's monitoring sheet and rebuilt
 * on the shared design system: three utilisation cards, uptime and load, and the
 * processes currently eating the CPU.
 *
 * Live while open: the view model polls every five seconds only while this sheet
 * is subscribed, so dismissing it stops the traffic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerMetricsSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerMetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        ServerMetricsContent(state = state, onRefresh = viewModel::refresh)
    }
}

@Composable
private fun ServerMetricsContent(
    state: ServerMetricsUiState,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = VmTheme.spacing.screenHorizontal)
            .padding(bottom = VmTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.md),
    ) {
        MetricsHeader(state = state, onRefresh = onRefresh)

        val metrics = state.metrics
        state.error?.let { error ->
            if (metrics == null) {
                VmErrorPanel(error = error, onRetry = onRefresh)
            } else {
                // Keep the last good sample on screen; a poll failure over it is
                // a footnote, not a takeover.
                VmInlineError(error = error)
            }
        }

        when {
            state.isLoading && metrics == null -> LoadingRow()
            metrics != null -> {
                MetricCardsRow(metrics)
                LoadUptimeCard(metrics)
                if (metrics.topProcesses.isNotEmpty()) {
                    ProcessTable(processes = metrics.topProcesses)
                }
            }
        }
    }
}
@Composable
private fun MetricsHeader(state: ServerMetricsUiState, onRefresh: () -> Unit) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Column(verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.xxs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Default.Analytics,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SERVER TELEMETRY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.serverName.ifBlank { "Server" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!state.isLoading || state.metrics != null) {
                VmStatusBadge(
                    status = if (state.error == null) VmStatus.CONNECTED else VmStatus.DEGRADED,
                    label = if (state.error == null) "Live" else "Stale",
                    showLabel = false,
                )
            }
            IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh now")
            }
        }
        if (state.fetchedAtMillis > 0L) {
            Text(
                text = "Updated " + formatter.format(Date(state.fetchedAtMillis)) +
                    "  ·  refreshes every 5 s",
                style = VmTheme.code.mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VmTheme.spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = "Collecting metrics…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
@Composable
private fun MetricCardsRow(metrics: ServerMetrics) {
    Row(horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm)) {
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "CPU",
            icon = Icons.Default.Speed,
            value = metrics.cpuPercent
                ?.let { String.format(Locale.US, "%.1f%%", it) }
                ?: NOT_MEASURED,
            fraction = metrics.cpuPercent?.div(100.0)?.toFloat(),
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "Memory",
            icon = Icons.Default.Memory,
            value = formatUsage(
                usedGb = metrics.ramUsedMb / MB_PER_GB,
                totalGb = metrics.ramTotalMb / MB_PER_GB,
            ),
            fraction = metrics.ramFraction,
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "Disk",
            icon = Icons.Default.Storage,
            value = formatUsage(usedGb = metrics.diskUsedGb, totalGb = metrics.diskTotalGb),
            fraction = metrics.diskFraction,
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    icon: ImageVector,
    value: String,
    fraction: Float?,
    modifier: Modifier = Modifier,
) {
    val color = levelColor(fraction)
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(VmTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.xs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = title.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = VmTheme.code.inlineCode,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        VmProgress(progress = fraction, color = color)
    }
}

/** Traffic-light thresholds, matching the semantic colour tokens. */
@Composable
private fun levelColor(fraction: Float?): Color = when {
    fraction == null -> MaterialTheme.colorScheme.outline
    fraction >= LEVEL_ERROR -> MaterialTheme.colorScheme.error
    fraction >= LEVEL_WARNING -> VmTheme.colors.warning
    else -> VmTheme.colors.success
}

private fun formatUsage(usedGb: Double, totalGb: Double): String = when {
    totalGb <= 0.0 -> NOT_MEASURED
    else -> formatGb(usedGb) + " / " + formatGb(totalGb) + " GB"
}

private fun formatGb(value: Double): String =
    if (value >= GB_ONE_DECIMAL_THRESHOLD) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.1f", value)
    }

/** Below this many GB, show one decimal ("7.9 GB"); above it, none ("39 GB"). */
private const val GB_ONE_DECIMAL_THRESHOLD = 10.0

private const val NOT_MEASURED = "—"
private const val LEVEL_WARNING = 0.75f
private const val LEVEL_ERROR = 0.9f
private const val CPU_HIGH_PERCENT = 50.0
private const val CPU_ELEVATED_PERCENT = 20.0
private const val PROCESS_COLUMN_DP = 52
private const val PROCESS_CPU_COLUMN_DP = 48
/** `free -m` reports mebibytes; the UI shows GB. */
private const val MB_PER_GB = 1024.0
@Composable
private fun LoadUptimeCard(metrics: ServerMetrics) {
    VmCard {
        InfoLine(
            icon = Icons.Default.Schedule,
            label = "Uptime",
            value = metrics.uptime ?: NOT_MEASURED,
        )
        VmDivider(modifier = Modifier.padding(vertical = VmTheme.spacing.sm))
        InfoLine(
            icon = Icons.Default.Speed,
            label = "Load average",
            value = metrics.loadAverage
                .take(3)
                .joinToString("  ") { String.format(Locale.US, "%.2f", it) }
                .ifBlank { NOT_MEASURED },
        )
    }
}

@Composable
private fun InfoLine(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = VmTheme.spacing.sm),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = VmTheme.code.mono,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProcessTable(processes: List<ProcessItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm)) {
        VmSectionHeader(title = "Top processes by CPU")
        VmCard(contentPadding = PaddingValues(VmTheme.spacing.md)) {
            ProcessRowHeader()
            VmDivider(modifier = Modifier.padding(vertical = VmTheme.spacing.xs))
            processes.forEach { process -> ProcessRow(process) }
        }
    }
}

@Composable
private fun ProcessRowHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        ProcessCell(
            "USER",
            Modifier.width(PROCESS_COLUMN_DP.dp),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProcessCell(
            "PID",
            Modifier.width(PROCESS_COLUMN_DP.dp),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProcessCell(
            "CPU%",
            Modifier.width(PROCESS_CPU_COLUMN_DP.dp),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProcessCell(
            "MEM%",
            Modifier.width(PROCESS_COLUMN_DP.dp),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "COMMAND",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProcessRow(process: ProcessItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VmTheme.spacing.xxs),
    ) {
        ProcessCell(
            process.user,
            Modifier.width(PROCESS_COLUMN_DP.dp),
            MaterialTheme.colorScheme.onSurface,
        )
        ProcessCell(
            process.pid,
            Modifier.width(PROCESS_COLUMN_DP.dp),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProcessCell(
            text = String.format(Locale.US, "%.1f", process.cpuPercent),
            modifier = Modifier.width(PROCESS_CPU_COLUMN_DP.dp),
            color = processCpuColor(process.cpuPercent),
        )
        ProcessCell(
            text = String.format(Locale.US, "%.1f", process.memPercent),
            modifier = Modifier.width(PROCESS_COLUMN_DP.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = process.command,
            style = VmTheme.code.mono,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProcessCell(text: String, modifier: Modifier = Modifier, color: Color) {
    Text(
        text = text,
        style = VmTheme.code.mono,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun processCpuColor(cpuPercent: Double): Color = when {
    cpuPercent >= CPU_HIGH_PERCENT -> MaterialTheme.colorScheme.error
    cpuPercent >= CPU_ELEVATED_PERCENT -> VmTheme.colors.warning
    else -> MaterialTheme.colorScheme.primary
}

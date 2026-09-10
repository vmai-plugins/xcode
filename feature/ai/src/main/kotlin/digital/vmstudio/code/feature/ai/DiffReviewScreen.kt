package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.git.DiffLine
import digital.vmstudio.code.core.git.DiffLineType
import digital.vmstudio.code.core.git.FileDiff
import digital.vmstudio.code.core.git.GitDiff
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmEmptyState
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * Diff review screen for examining file changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffReviewScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiffReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.filePath.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = state.filePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val currentError = state.error
            val currentDiff = state.diff
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                currentError != null -> {
                    VmErrorPanel(
                        error = currentError,
                        onRetry = viewModel::retry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                currentDiff == null || !currentDiff.hasChanges -> {
                    VmEmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = "No changes",
                        description = "The file is unchanged.",
                    )
                }
                else -> {
                    DiffContent(
                        diff = currentDiff,
                        isSaving = state.isSaving,
                        onAccept = viewModel::accept,
                        onReject = viewModel::reject,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiffContent(
    diff: GitDiff,
    isSaving: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val spacing = VmTheme.spacing

    Column(modifier = Modifier.fillMaxSize()) {
        // Summary header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "${diff.files.size} file(s) changed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "+${diff.totalAdditions}",
                style = MaterialTheme.typography.bodySmall,
                color = VmTheme.colors.success,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "-${diff.totalDeletions}",
                style = MaterialTheme.typography.bodySmall,
                color = VmTheme.colors.warning,
                fontWeight = FontWeight.Bold,
            )
        }

        HorizontalDivider()

        // Diff list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = spacing.sm),
        ) {
            for (fileDiff in diff.files) {
                item(key = "header-${fileDiff.displayPath}") {
                    FileDiffHeader(fileDiff)
                }
                items(fileDiff.lines, key = { "${fileDiff.displayPath}-${it.hashCode()}" }) { line ->
                    DiffLineRow(line)
                }
            }
        }

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            VmButton(
                text = "Reject",
                icon = Icons.Default.Remove,
                style = VmButtonStyle.Secondary,
                onClick = onReject,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            )
            VmButton(
                text = "Accept",
                icon = Icons.Default.CheckCircle,
                style = VmButtonStyle.Primary,
                onClick = onAccept,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FileDiffHeader(fileDiff: FileDiff) {
    val spacing = VmTheme.spacing
    val icon = when {
        fileDiff.isNew -> "new"
        fileDiff.isDeleted -> "deleted"
        fileDiff.isRename -> "renamed"
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = fileDiff.displayPath,
            style = VmTheme.code.mono,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        if (icon != null) {
            Text(
                text = "($icon)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = " +${fileDiff.additions} -${fileDiff.deletions}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val spacing = VmTheme.spacing
    val bgColor = when (line.type) {
        DiffLineType.ADDED -> VmTheme.colors.successContainer
        DiffLineType.REMOVED -> VmTheme.colors.warningContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when (line.type) {
        DiffLineType.ADDED -> VmTheme.colors.onSuccessContainer
        DiffLineType.REMOVED -> VmTheme.colors.onWarningContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val prefix = when (line.type) {
        DiffLineType.ADDED -> "+"
        DiffLineType.REMOVED -> "-"
        else -> " "
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenHorizontal, vertical = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = line.oldLineNumber?.toString() ?: "",
            style = VmTheme.code.mono.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(32.dp),
        )
        Text(
            text = line.newLineNumber?.toString() ?: "",
            style = VmTheme.code.mono.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(32.dp),
        )
        Text(
            text = prefix,
            style = VmTheme.code.mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = textColor,
            modifier = Modifier.width(12.dp),
        )
        Text(
            text = line.text,
            style = VmTheme.code.mono.copy(fontSize = 11.sp),
            color = textColor,
        )
    }
}

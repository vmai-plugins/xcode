package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Touch-Optimized Interactive Git Diff & Merge Deck.
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
    var commitMessage by remember { mutableStateOf("") }
    var pushAfterCommit by remember { mutableStateOf(true) }

    if (state.showCommitSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleCommitSheet(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Commit Changes",
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    label = { Text("Commit message") },
                    placeholder = { Text("e.g. Update ${state.filePath.substringAfterLast('/')}") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = pushAfterCommit,
                        onCheckedChange = { pushAfterCommit = it },
                    )
                    Text(
                        text = "Push to origin/${state.branchName.ifBlank { "main" }}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.toggleCommitSheet(false) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val msg = commitMessage.ifBlank {
                                "Update ${state.filePath.substringAfterLast('/')}"
                            }
                            viewModel.commitAndPush(msg, pushAfterCommit)
                        },
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = if (pushAfterCommit) Icons.Default.Publish else Icons.Default.Commit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (pushAfterCommit) "Commit & Push" else "Commit")
                    }
                }
            }
        }
    }

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
                            text = if (state.branchName.isNotBlank()) {
                                "${state.branchName} • ${state.filePath}"
                            } else {
                                state.filePath
                            },
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
                actions = {
                    if (state.diff?.hasChanges == true) {
                        FilterChip(
                            selected = state.isStaged,
                            onClick = {
                                if (state.isStaged) viewModel.unstageFile() else viewModel.stageFile()
                            },
                            label = { Text(if (state.isStaged) "Staged" else "Unstaged") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (state.isStaged) Icons.Default.Check else Icons.Default.Remove,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2E7D32),
                                selectedLabelColor = Color.White,
                            ),
                            modifier = Modifier.padding(end = 8.dp),
                        )
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
                        title = if (state.isCommitted) "Committed & Clean" else "No changes",
                        description = if (state.isCommitted) {
                            "Changes successfully committed to Git."
                        } else {
                            "Working tree matches HEAD."
                        },
                    )
                }
                else -> {
                    DiffContent(
                        diff = currentDiff,
                        isSaving = state.isSaving,
                        isStaged = state.isStaged,
                        onToggleStage = {
                            if (state.isStaged) viewModel.unstageFile() else viewModel.stageFile()
                        },
                        onOpenCommit = { viewModel.toggleCommitSheet(true) },
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
    isStaged: Boolean,
    onToggleStage: () -> Unit,
    onOpenCommit: () -> Unit,
    onReject: () -> Unit,
) {
    val spacing = VmTheme.spacing

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${diff.files.size} file(s) changed",
                    style = MaterialTheme.typography.bodySmall,
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
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = spacing.sm),
        ) {
            for (fileDiff in diff.files) {
                item(key = "header-${fileDiff.displayPath}") {
                    FileDiffHeader(fileDiff)
                }
                items(fileDiff.lines.size, key = { idx -> "${fileDiff.displayPath}-$idx" }) { idx ->
                    DiffLineRow(fileDiff.lines[idx])
                }
            }
        }

        // Action Deck at Bottom
        Surface(
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                VmButton(
                    text = "Discard",
                    icon = Icons.Default.DeleteOutline,
                    style = VmButtonStyle.Secondary,
                    onClick = onReject,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                )
                VmButton(
                    text = if (isStaged) "Commit..." else "Stage & Commit",
                    icon = Icons.Default.Commit,
                    style = VmButtonStyle.Primary,
                    onClick = onOpenCommit,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1.3f),
                )
            }
        }
    }
}

@Composable
private fun FileDiffHeader(fileDiff: FileDiff) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E1F22),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF29B6F6)),
            )
            Text(
                text = fileDiff.displayPath.substringAfterLast('/'),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE2E2E5),
            )
            Text(
                text = fileDiff.displayPath,
                style = VmTheme.code.mono.copy(fontSize = 11.sp),
                color = Color(0xFF8E919A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "+${fileDiff.additions}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "-${fileDiff.deletions}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFEF5350),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    if (line.type == DiffLineType.HEADER) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2B2D31),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = line.text.trim(),
                        style = VmTheme.code.mono.copy(fontSize = 10.sp),
                        color = Color(0xFF8E919A),
                    )
                }
            }
        }
        return
    }

    val (bg, textCol) = when (line.type) {
        DiffLineType.ADDED -> Color(0x2E2E7D32) to Color(0xFF81C784)
        DiffLineType.REMOVED -> Color(0x2EC62828) to Color(0xFFE57373)
        else -> Color.Transparent to Color(0xFFC4C6D0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = line.oldLineNumber?.toString() ?: "",
            style = VmTheme.code.mono.copy(fontSize = 11.sp),
            color = Color(0xFF5A5D66),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(36.dp).padding(end = 6.dp),
        )
        Text(
            text = line.newLineNumber?.toString() ?: "",
            style = VmTheme.code.mono.copy(fontSize = 11.sp),
            color = Color(0xFF5A5D66),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(36.dp).padding(end = 12.dp),
        )
        Text(
            text = line.text,
            style = VmTheme.code.mono.copy(fontSize = 11.sp),
            color = textCol,
        )
    }
}

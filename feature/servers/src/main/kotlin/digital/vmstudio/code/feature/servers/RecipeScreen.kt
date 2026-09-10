package digital.vmstudio.code.feature.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ssh.recipes.RecipeInstallStatus
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmEmptyState
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.component.VmStatus
import digital.vmstudio.code.core.ui.component.VmStatusBadge
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * Server Recipes: scripted, repeatable setups that provision a development stack.
 * Every recipe command goes through the same approval gate as the terminal, so
 * the safety policy applies uniformly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Recipes · ${state.serverName.ifBlank { "Server" }}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = spacing.screenHorizontal,
                vertical = spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(key = "recipes-header") {
                Text(
                    text = "One-click development stacks",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Each recipe installs a complete stack on your server. " +
                        "Every step is subject to your command-approval policy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }

            state.error?.let { err ->
                item(key = "error") {
                    VmErrorPanel(error = err, onRetry = { })
                }
            }

            if (state.isLoading) {
                item(key = "loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Checking installed recipes…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (!state.hasRecipes) {
                item(key = "empty") {
                    VmEmptyState(
                        icon = Icons.Default.Inventory2,
                        title = "No recipes available",
                        description = "Recipe catalog is empty. Check back later for new stacks.",
                    )
                }
            } else {
                items(state.recipes, key = { "recipe-${it.recipe.id}" }) { item ->
                    RecipeCard(
                        item = item,
                        onRun = { viewModel.runRecipe(item.recipe.id) },
                        onDismissError = { viewModel.clearError(item.recipe.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(
    item: RecipeUiItem,
    onRun: () -> Unit,
    onDismissError: () -> Unit,
) {
    val spacing = VmTheme.spacing
    val isRunning = item.progress != null
    val isInstalled = item.status == RecipeInstallStatus.INSTALLED

    VmCard(contentPadding = PaddingValues(spacing.md)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.recipe.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.recipe.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                when {
                    isInstalled -> VmStatusBadge(label = "Installed", status = VmStatus.CONNECTED)
                    isRunning -> VmStatusBadge(label = "Installing", status = VmStatus.IDLE)
                    item.status == RecipeInstallStatus.UNKNOWN ->
                        VmStatusBadge(label = "Unknown", status = VmStatus.IDLE)
                    else -> VmStatusBadge(label = "Not installed", status = VmStatus.DISCONNECTED)
                }
            }

            item.progress?.let { progress ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    LinearProgressIndicator(
                        progress = { progress.fraction.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = progress.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item.error?.let { err ->
                VmErrorPanel(
                    error = err,
                    onRetry = onRun,
                )
            }

            if (!isRunning && !isInstalled) {
                VmButton(
                    text = "Install",
                    icon = Icons.Default.Download,
                    style = VmButtonStyle.Primary,
                    onClick = onRun,
                )
            }

            if (isInstalled) {
                Text(
                    text = "Estimated install time: ~${item.recipe.estimatedSeconds / 60} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

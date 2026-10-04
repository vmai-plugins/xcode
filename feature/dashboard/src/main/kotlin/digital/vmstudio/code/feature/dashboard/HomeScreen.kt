package digital.vmstudio.code.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.database.entity.ActivityEntity
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmProgress
import digital.vmstudio.code.core.ui.component.VmSectionHeader
import digital.vmstudio.code.core.ui.theme.VmTheme
import digital.vmstudio.code.core.update.UpdateState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onAddServer: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenProjects: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            horizontal = spacing.screenHorizontal,
            vertical = spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (!state.isOnline) {
            item(key = "offline") { OfflineBanner() }
        }

        when (val update = state.updateState) {
            is UpdateState.Available -> item(key = "update-banner") {
                UpdateAvailableBanner(
                    versionName = update.info.versionName,
                    actionLabel = "Download & install",
                    onAction = viewModel::downloadUpdate,
                    onDismiss = viewModel::dismissUpdate,
                )
            }
            is UpdateState.Downloading -> item(key = "update-banner") {
                UpdateDownloadingBanner(progress = update.progress)
            }
            is UpdateState.ReadyToInstall -> item(key = "update-banner") {
                UpdateAvailableBanner(
                    versionName = update.info.versionName,
                    actionLabel = "Install",
                    onAction = viewModel::retryInstall,
                    onDismiss = viewModel::dismissUpdate,
                )
            }
            else -> Unit
        }

        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "x-codes",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Code anywhere. Connect anywhere. Build with AI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.isLoading && !state.hasAnySetup) {
            item(key = "get-started") {
                GetStartedCard(onAddServer = onAddServer)
            }
        }

        item(key = "counts") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Dns,
                    value = state.servers.size.toString(),
                    label = if (state.servers.size == 1) "Server" else "Servers",
                    onClick = onOpenServers,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Workspaces,
                    value = state.recentProjects.size.toString(),
                    label = if (state.recentProjects.size == 1) "Project" else "Projects",
                    onClick = onOpenProjects,
                )
            }
        }

        if (state.recentActivity.isNotEmpty()) {
            item(key = "activity-header") { VmSectionHeader(title = "Recent activity") }
            items(state.recentActivity, key = { it.id }) { entry ->
                ActivityRow(entry)
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    val spacing = VmTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(VmTheme.colors.warningContainer)
            .padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = VmTheme.colors.onWarningContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Offline. Cached projects and past conversations are still available; " +
                "remote operations will fail until you reconnect.",
            style = MaterialTheme.typography.bodySmall,
            color = VmTheme.colors.onWarningContainer,
        )
    }
}

@Composable
private fun UpdateAvailableBanner(
    versionName: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = VmTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Update available: v$versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            VmButton(text = actionLabel, style = VmButtonStyle.Primary, onClick = onAction)
            VmButton(text = "Later", style = VmButtonStyle.Tertiary, onClick = onDismiss)
        }
    }
}

@Composable
private fun UpdateDownloadingBanner(progress: Float) {
    VmCard {
        VmProgress(
            progress = progress,
            label = "Downloading update...",
            trailingLabel = "${(progress * 100).toInt()}%",
        )
    }
}

@Composable
private fun GetStartedCard(onAddServer: () -> Unit) {
    VmCard {
        Column(verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.RocketLaunch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Get started",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = "Add an SSH server to connect to your development machine. " +
                    "From there you can browse files, run commands and open projects.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VmButton(text = "Add your first server", onClick = onAddServer)
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VmCard(modifier = modifier, onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = VmTheme.spacing.sm),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntity) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VmTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.md),
    ) {
        Text(
            text = formatter.format(Date(entry.timestampMillis)),
            style = VmTheme.code.mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

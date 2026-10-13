package digital.vmstudio.code.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.project.Project
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.component.VmEmptyState
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.theme.VmTheme

enum class WorkspaceFilterTab {
    ALL,
    VM_HUB,
    GITHUB,
    LOCAL,
    GDRIVE,
    VPS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onOpenProject: (Project) -> Unit,
    title: String = "Workspaces & Projects",
    onCreateProject: () -> Unit,
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMenu: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    var pendingDelete by remember { mutableStateOf<Project?>(null) }
    var colorPickerProject by remember { mutableStateOf<Project?>(null) }
    var activeTab by remember { mutableStateOf(WorkspaceFilterTab.ALL) }

    pendingDelete?.let { project ->
        VmDialog(
            title = "Delete ${project.name}?",
            onDismiss = { pendingDelete = null },
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.delete(project)
                pendingDelete = null
            },
        ) {
            Text(
                text = "Removes it and its chats from this app. The files on the server or phone storage are not touched.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    colorPickerProject?.let { project ->
        ProjectColorPickerDialog(
            project = project,
            onDismiss = { colorPickerProject = null },
            onSelectColor = { hex ->
                viewModel.updateColor(project, hex)
                colorPickerProject = null
            },
        )
    }

    val filteredProjects = remember(state.projects, activeTab) {
        when (activeTab) {
            WorkspaceFilterTab.ALL -> state.projects
            WorkspaceFilterTab.VM_HUB -> state.projects.filter {
                it.description.contains("VM Hub #") || it.remotePath.startsWith("/vmhub/")
            }
            WorkspaceFilterTab.GITHUB -> state.projects.filter {
                it.branch != null || it.remotePath.contains("git", ignoreCase = true)
            }
            WorkspaceFilterTab.LOCAL -> state.projects.filter { it.isLocal }
            WorkspaceFilterTab.GDRIVE -> state.projects.filter {
                it.remotePath.contains("gdrive", ignoreCase = true) || it.remotePath.contains("drive", ignoreCase = true)
            }
            WorkspaceFilterTab.VPS -> state.projects.filter { it.isRemote }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (state.isSyncingHub) {
                                "Syncing universal workspaces from VM Project Hub…"
                            } else {
                                state.hubStatusMessage ?: "${state.projects.size} active workspaces"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else if (onOpenMenu != null) {
                        IconButton(onClick = onOpenMenu) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::syncFromHub,
                        enabled = !state.isSyncingHub,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Sync VM Project Hub",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.isSyncingHub) "Syncing…" else "Sync Hub")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateProject,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "New workspace")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Workspace Source Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                WorkspaceTabChip(
                    label = "All (${state.projects.size})",
                    selected = activeTab == WorkspaceFilterTab.ALL,
                    accentColor = Color(0xFF6366F1),
                    icon = Icons.Default.Workspaces,
                    onClick = { activeTab = WorkspaceFilterTab.ALL },
                )
                WorkspaceTabChip(
                    label = "VM Hub",
                    selected = activeTab == WorkspaceFilterTab.VM_HUB,
                    accentColor = Color(0xFFEC4899),
                    icon = Icons.Default.Cloud,
                    onClick = { activeTab = WorkspaceFilterTab.VM_HUB },
                )
                WorkspaceTabChip(
                    label = "GitHub",
                    selected = activeTab == WorkspaceFilterTab.GITHUB,
                    accentColor = Color(0xFF8B5CF6),
                    icon = Icons.Default.Code,
                    onClick = { activeTab = WorkspaceFilterTab.GITHUB },
                )
                WorkspaceTabChip(
                    label = "Local Phone",
                    selected = activeTab == WorkspaceFilterTab.LOCAL,
                    accentColor = Color(0xFF10B981),
                    icon = Icons.Default.PhoneAndroid,
                    onClick = { activeTab = WorkspaceFilterTab.LOCAL },
                )
                WorkspaceTabChip(
                    label = "Google Drive",
                    selected = activeTab == WorkspaceFilterTab.GDRIVE,
                    accentColor = Color(0xFFF59E0B),
                    icon = Icons.Default.Cloud,
                    onClick = { activeTab = WorkspaceFilterTab.GDRIVE },
                )
                WorkspaceTabChip(
                    label = "VPS Cloud",
                    selected = activeTab == WorkspaceFilterTab.VPS,
                    accentColor = Color(0xFF06B6D4),
                    icon = Icons.Default.Dns,
                    onClick = { activeTab = WorkspaceFilterTab.VPS },
                )
            }

            state.error?.let { error ->
                VmErrorPanel(error = error, modifier = Modifier.padding(spacing.md))
            }

            when {
                state.isLoading -> Unit

                state.projects.isEmpty() -> VmEmptyState(
                    icon = Icons.Default.Workspaces,
                    title = "No Workspaces Yet",
                    description = "Code directly on your phone, link your Google Drive, or connect a GitHub repository.",
                    actionLabel = "Create Workspace",
                    onAction = onCreateProject,
                )

                filteredProjects.isEmpty() -> VmEmptyState(
                    icon = Icons.Default.Workspaces,
                    title = "No matching workspaces",
                    description = "No workspaces found in this category. Tap '+' to create or clone one.",
                    actionLabel = "Create Workspace",
                    onAction = onCreateProject,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(filteredProjects, key = { it.id }) { project ->
                        ProjectRow(
                            project = project,
                            serverLabel = state.serverFor(project)?.name,
                            onOpen = { viewModel.open(project, onOpenProject) },
                            onToggleFavorite = { viewModel.toggleFavorite(project) },
                            onChangeColor = { colorPickerProject = project },
                            onDelete = { pendingDelete = project },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTabChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accentColor.copy(alpha = 0.15f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun ProjectRow(
    project: Project,
    serverLabel: String?,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onChangeColor: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = VmTheme.spacing
    var menuOpen by remember { mutableStateOf(false) }
    val projectColor = ProjectColors.parseColor(project.colorHex, project.name)

    VmCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .border(1.dp, projectColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Project Colorful Avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                projectColor.copy(alpha = 0.85f),
                                projectColor.copy(alpha = 0.45f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = project.name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                )
            }

            Spacer(Modifier.width(spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = project.remotePath,
                    style = VmTheme.code.mono,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )

                // Colorful Badges Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Source Badge
                    val (sourceLabel, sourceColor) = when {
                        project.remotePath.startsWith("/github/") -> "GitHub" to Color(0xFF8B5CF6)
                        project.remotePath.startsWith("/vmhub/") || project.description.contains("VM Hub #") ->
                            "VM Hub · Universal" to Color(0xFFEC4899)
                        project.remotePath.contains("gdrive", ignoreCase = true) -> "Google Drive" to Color(0xFFF59E0B)
                        project.isLocal -> "Local Phone" to Color(0xFF10B981)
                        project.branch != null -> "GitHub" to Color(0xFF8B5CF6)
                        else -> (serverLabel ?: "VPS Cloud") to Color(0xFF06B6D4)
                    }

                    BadgePill(label = sourceLabel, color = sourceColor)

                    project.branch?.let { branch ->
                        BadgePill(label = "git: $branch", color = Color(0xFF3B82F6))
                    }

                    project.runtime?.let { runtime ->
                        BadgePill(label = runtime, color = Color(0xFF14B8A6))
                    }

                    project.packageManager?.let { pm ->
                        val pmColor = if (pm.equals("urgent", ignoreCase = true)) Color(0xFFEF4444) else Color(0xFFF59E0B)
                        BadgePill(label = pm.uppercase(), color = pmColor)
                    }
                }
            }

            // Color Palette Picker Button
            IconButton(onClick = onChangeColor) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ColorLens,
                        contentDescription = "Change Project Color",
                        tint = projectColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Favorite Star Button
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (project.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (project.isFavorite) "Remove favorite" else "Favorite",
                    tint = if (project.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Assign Color") },
                        leadingIcon = { Icon(Icons.Default.ColorLens, contentDescription = null, tint = projectColor) },
                        onClick = {
                            menuOpen = false
                            onChangeColor()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Workspace", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgePill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun ProjectColorPickerDialog(
    project: Project,
    onDismiss: () -> Unit,
    onSelectColor: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Theme Color: ${project.name}")
            }
        },
        text = {
            Column {
                Text(
                    text = "Assign a vibrant identity color to this project for cards, badges, and editor accents:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                // 2 rows of 5 colors
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProjectColors.Palette.chunked(5).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            rowColors.forEach { option ->
                                val color = ProjectColors.parseColor(option.hex)
                                val isSelected = project.colorHex?.equals(option.hex, ignoreCase = true) == true
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.Transparent,
                                            shape = CircleShape,
                                        )
                                        .clickable { onSelectColor(option.hex) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

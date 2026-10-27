package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.vmstudio.code.core.git.GitHubRepository
import digital.vmstudio.code.core.project.Project
import digital.vmstudio.code.core.ui.theme.VmTheme

private enum class MentionCategory(val title: String) {
    ALL("All"),
    PROJECTS("Projects"),
    GITHUB("GitHub"),
    SWARM("Swarm & Agents"),
    TOOLS("Tools"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectMentionSheet(
    availableProjects: List<Project>,
    githubRepositories: List<GitHubRepository>,
    onSelectMention: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MentionCategory.ALL) }
    val haptic = LocalHapticFeedback.current

    val registeredRepoNames = remember(availableProjects) {
        availableProjects.mapNotNull { project ->
            when {
                project.remotePath.startsWith("/github/") ->
                    project.remotePath.removePrefix("/github/").trim('/').lowercase()
                project.description.contains("github.com", ignoreCase = true) ->
                    project.remotePath.substringAfterLast("github.com/").removeSuffix(".git").trim('/').lowercase()
                else -> null
            }
        }.toSet()
    }

    val filteredProjects = remember(availableProjects, query, selectedCategory) {
        if (selectedCategory == MentionCategory.GITHUB || selectedCategory == MentionCategory.TOOLS || selectedCategory == MentionCategory.SWARM) {
            emptyList()
        } else if (query.isBlank()) {
            availableProjects
        } else {
            availableProjects.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.remotePath.contains(query, ignoreCase = true) ||
                    (it.branch?.contains(query, ignoreCase = true) == true)
            }
        }
    }

    val filteredRepos = remember(githubRepositories, registeredRepoNames, query, selectedCategory) {
        if (selectedCategory == MentionCategory.PROJECTS || selectedCategory == MentionCategory.TOOLS || selectedCategory == MentionCategory.SWARM) {
            emptyList()
        } else {
            githubRepositories
                .filter { repo -> repo.fullName.lowercase() !in registeredRepoNames }
                .filter { repo ->
                    if (query.isBlank()) true
                    else repo.fullName.contains(query, ignoreCase = true) ||
                        (repo.description?.contains(query, ignoreCase = true) == true)
                }
        }
    }

    val showAgents = selectedCategory == MentionCategory.ALL || selectedCategory == MentionCategory.SWARM
    val showTools = selectedCategory == MentionCategory.ALL || selectedCategory == MentionCategory.TOOLS

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "@",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Mention Context",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                        Text(
                            text = "Reference other projects, repos, or tools",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8E919A),
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF8E919A),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        text = "Search projects, repos, or tools…",
                        color = Color(0xFF8E919A),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF8E919A),
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else null,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MentionCategory.values().forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Suggestions List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 1. Projects Section
                if (filteredProjects.isNotEmpty()) {
                    item {
                        Text(
                            text = "PROJECT WORKSPACES (${filteredProjects.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                    items(filteredProjects, key = { "proj_${it.id}" }) { project ->
                        MentionItemRow(
                            icon = Icons.Default.Folder,
                            title = project.name,
                            subtitle = project.remotePath,
                            badge = if (project.isLocal) "Local" else (project.serverId ?: "Remote"),
                            branch = project.branch,
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onSelectMention(project.name)
                            },
                        )
                    }
                }

                // 2. GitHub Repositories Section
                if (filteredRepos.isNotEmpty()) {
                    item {
                        Text(
                            text = "GITHUB REPOSITORIES (${filteredRepos.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF64B5F6),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(filteredRepos, key = { "repo_${it.id}" }) { repo ->
                        MentionItemRow(
                            icon = Icons.Default.Code,
                            title = repo.fullName,
                            subtitle = repo.description ?: repo.cloneUrl,
                            badge = "GitHub",
                            branch = repo.defaultBranch,
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onSelectMention(repo.fullName)
                            },
                        )
                    }
                }

                // 3. Swarm & Specialist Agents
                if (showAgents && (query.isBlank() || "swarm".contains(query, ignoreCase = true) || "agent".contains(query, ignoreCase = true))) {
                    item {
                        Text(
                            text = "AGENTS & SWARMS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFFB74D),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    item {
                        MentionItemRow(
                            icon = Icons.Default.AutoAwesome,
                            title = "swarm",
                            subtitle = "Run parallel agent swarm across all mentioned projects",
                            badge = "Swarm",
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onSelectMention("swarm")
                            },
                        )
                    }
                    item {
                        MentionItemRow(
                            icon = Icons.Default.Code,
                            title = "architect",
                            subtitle = "High-level system design & specification specialist",
                            badge = "Agent",
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onSelectMention("architect")
                            },
                        )
                    }
                    item {
                        MentionItemRow(
                            icon = Icons.Default.Code,
                            title = "frontend",
                            subtitle = "Web UI, Next.js & Jetpack Compose specialist",
                            badge = "Agent",
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onSelectMention("frontend")
                            },
                        )
                    }
                    item {
                        MentionItemRow(
                            icon = Icons.Default.Terminal,
                            title = "backend",
                            subtitle = "Server APIs, database schema & DevOps specialist",
                            badge = "Agent",
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onSelectMention("backend")
                            },
                        )
                    }
                    item {
                        MentionItemRow(
                            icon = Icons.Default.Source,
                            title = "reviewer",
                            subtitle = "Automated diff auditor & QA verification specialist",
                            badge = "Agent",
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onSelectMention("reviewer")
                            },
                        )
                    }
                }

                // 4. Quick System Tools
                if (showTools && query.isBlank()) {
                    item {
                        Text(
                            text = "SYSTEM & CONTEXT HELPERS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFA5D6A7),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    item {
                        MentionItemRow(
                            icon = Icons.Default.Source,
                            title = "git-diff",
                            subtitle = "Include current uncommitted git changes in prompt",
                            badge = "Git",
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onSelectMention("git-diff")
                            },
                        )
                    }
                    item {
                        MentionItemRow(
                            icon = Icons.Default.Folder,
                            title = "file: <path>",
                            subtitle = "Inline specific workspace file contents into prompt",
                            badge = "File",
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onSelectMention("file: ")
                            },
                        )
                    }
                    item {
                        MentionItemRow(
                            icon = Icons.Default.Terminal,
                            title = "terminal",
                            subtitle = "Include recent terminal history into prompt",
                            badge = "Shell",
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onSelectMention("terminal")
                            },
                        )
                    }
                }

                if (filteredProjects.isEmpty() && filteredRepos.isEmpty() && !showTools) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No matching projects or repositories found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF8E919A),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MentionItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    branch: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!branch.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = branch,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

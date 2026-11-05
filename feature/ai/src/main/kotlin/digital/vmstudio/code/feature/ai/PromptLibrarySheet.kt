package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * High-quality developer recipe & prompt template definition for beginners and pros.
 */
data class CodeRecipe(
    val id: String,
    val title: String,
    val description: String,
    val prompt: String,
    val category: String,
    val icon: ImageVector,
    val accentColor: Color,
    val tags: List<String> = emptyList(),
)

object CodeRecipesCatalog {
    val ALL: List<CodeRecipe> = listOf(
        // 🔰 Beginner Essentials
        CodeRecipe(
            id = "explain_project",
            title = "Explain Project Architecture",
            description = "High-level overview of tech stack, module structure, and entry points",
            prompt = "Walk me through this project: what does it do, what modules and architecture patterns are used, and where are the main entry points?",
            category = "Beginner",
            icon = Icons.Default.Folder,
            accentColor = Color(0xFF10B981),
            tags = listOf("explain", "architecture", "overview"),
        ),
        CodeRecipe(
            id = "explain_screen",
            title = "Explain Current Screen / Logic",
            description = "Step-by-step breakdown of how state, ViewModel, and UI compose",
            prompt = "Explain how this screen or component works step-by-step in plain English: how is state managed, where do events go, and how is data fetched?",
            category = "Beginner",
            icon = Icons.Default.Code,
            accentColor = Color(0xFF38BDF8),
            tags = listOf("explain", "state", "ui"),
        ),
        CodeRecipe(
            id = "how_to_run",
            title = "How Do I Build & Test This?",
            description = "Step-by-step instructions for running, building APK, and testing",
            prompt = "Provide exact commands and step-by-step instructions to compile, test, and package this project into a release APK.",
            category = "Beginner",
            icon = Icons.Default.PlayArrow,
            accentColor = Color(0xFFA855F7),
            tags = listOf("build", "run", "guide"),
        ),

        // 🐛 Debug & Fix Errors
        CodeRecipe(
            id = "fix_stacktrace",
            title = "Fix Crash or Runtime Error",
            description = "Trace root cause of a crash and apply minimal, non-breaking fix",
            prompt = "Analyze the recent error or stack trace, identify the exact root cause, explain why it happened, and apply a minimal and correct fix.",
            category = "Debug",
            icon = Icons.Default.BugReport,
            accentColor = Color(0xFFEF4444),
            tags = listOf("fix", "crash", "error"),
        ),
        CodeRecipe(
            id = "find_memory_leaks",
            title = "Audit Memory & Coroutine Leaks",
            description = "Detect unclosed streams, memory leaks, and uncancelled scopes",
            prompt = "Inspect this module for memory leaks, unclosed streams or cursors, unnecessary Compose recompositions, and uncancelled CoroutineScopes.",
            category = "Debug",
            icon = Icons.Default.AutoAwesome,
            accentColor = Color(0xFFF97316),
            tags = listOf("leak", "memory", "coroutines"),
        ),
        CodeRecipe(
            id = "fix_ui_glitches",
            title = "Fix Compose UI Layout Glitches",
            description = "Resolve padding overlaps, keyboard scrolling issues, and jank",
            prompt = "Inspect this Jetpack Compose layout for UI glitches, keyboard overlap, clipping issues, and ensure smooth Material 3 scrolling.",
            category = "Debug",
            icon = Icons.Default.Build,
            accentColor = Color(0xFFEC4899),
            tags = listOf("compose", "ui", "layout"),
        ),

        // ✨ Feature Implementation
        CodeRecipe(
            id = "create_m3_screen",
            title = "Create Material 3 Screen",
            description = "Modern screen with loading, empty, and error states",
            prompt = "Implement a modern Material 3 Jetpack Compose screen with StateFlow state handling, smooth animations, empty/loading states, and dark mode support.",
            category = "Features",
            icon = Icons.Default.Code,
            accentColor = Color(0xFF6366F1),
            tags = listOf("screen", "compose", "m3"),
        ),
        CodeRecipe(
            id = "add_room_cache",
            title = "Add Room Database Offline Cache",
            description = "Entity, DAO, and repository with Flow updates",
            prompt = "Implement an offline-first Room database entity, DAO, and repository pattern with Flow streaming updates for this data model.",
            category = "Features",
            icon = Icons.Default.Folder,
            accentColor = Color(0xFF14B8A6),
            tags = listOf("room", "database", "offline"),
        ),
        CodeRecipe(
            id = "input_validation",
            title = "Form Input Validation & Errors",
            description = "Add real-time validation, error helpers, and haptic feedback",
            prompt = "Add robust form input validation with inline helper texts, error borders, sanitized input handling, and subtle haptic feedback.",
            category = "Features",
            icon = Icons.Default.Check,
            accentColor = Color(0xFFEAB308),
            tags = listOf("validation", "form", "input"),
        ),

        // 🧪 Testing & Security
        CodeRecipe(
            id = "write_unit_tests",
            title = "Write Unit Tests with MockK",
            description = "Comprehensive JUnit 4/5 tests covering happy paths and edge cases",
            prompt = "Write comprehensive unit tests with MockK and Kotlin Coroutines Test covering happy paths, edge cases, error states, and boundary conditions.",
            category = "Testing",
            icon = Icons.Default.Security,
            accentColor = Color(0xFF06B6D4),
            tags = listOf("test", "mockk", "junit"),
        ),
        CodeRecipe(
            id = "security_credential_audit",
            title = "Audit Credentials & Hardcoded Keys",
            description = "Strict scan for leaked tokens, cleartext secrets, and vulnerabilities",
            prompt = "Run a strict security audit on this module. Check for hardcoded API keys, exposed credentials, plaintext secrets, and insecure endpoints.",
            category = "Testing",
            icon = Icons.Default.Security,
            accentColor = Color(0xFFF43F5E),
            tags = listOf("security", "audit", "secrets"),
        ),

        // 🚀 Git, Build & Release
        CodeRecipe(
            id = "review_git_diff",
            title = "Review Git Diff for Regressions",
            description = "Pre-commit review of all modified files and potential bugs",
            prompt = "@git-diff Review all uncommitted changes in detail. Highlight any potential regressions, style inconsistencies, or missing imports before committing.",
            category = "Release",
            icon = Icons.Default.Source,
            accentColor = Color(0xFFF59E0B),
            tags = listOf("diff", "git", "review"),
        ),
        CodeRecipe(
            id = "audit_release_apk",
            title = "Audit Gradle Release Build & R8",
            description = "Verify shrinker rules, Proguard configuration, and APK readiness",
            prompt = "Audit the Gradle release configuration, Proguard/R8 rules, and asset packaging to ensure clean, shrink-optimized APK compilation without runtime crashes.",
            category = "Release",
            icon = Icons.Default.Build,
            accentColor = Color(0xFF8B5CF6),
            tags = listOf("gradle", "r8", "release"),
        ),
        CodeRecipe(
            id = "dispatch_paperclip",
            title = "Dispatch 24/7 Background Task to Paperclip",
            description = "Hand off heavy refactoring to VPS developer fleet",
            prompt = "@paperclip Dispatch this autonomous development task to the Paperclip fleet on agents.vmstudio.digital: plan architecture, apply fixes, and report diff.",
            category = "Release",
            icon = Icons.Default.PlayArrow,
            accentColor = Color(0xFF6366F1),
            tags = listOf("paperclip", "vps", "fleet"),
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromptLibrarySheet(
    onDismiss: () -> Unit,
    onSelectRecipe: (prompt: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Beginner", "Debug", "Features", "Testing", "Release")

    val filteredRecipes = remember(searchQuery, selectedCategory) {
        CodeRecipesCatalog.ALL.filter { recipe ->
            val matchesCategory = (selectedCategory == "All" || recipe.category.equals(selectedCategory, ignoreCase = true))
            val matchesSearch = if (searchQuery.isBlank()) true else {
                recipe.title.contains(searchQuery, ignoreCase = true) ||
                recipe.description.contains(searchQuery, ignoreCase = true) ||
                recipe.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }
            matchesCategory && matchesSearch
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 8.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Prompts & Recipes Library",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Curated high-quality prompts for beginners & pros",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search recipes (e.g. error, test, room, build)…") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedCategory = cat
                            },
                    ) {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Recipes List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(filteredRecipes, key = { it.id }) { recipe ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectRecipe(recipe.prompt)
                                onDismiss()
                            },
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(recipe.accentColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = recipe.icon,
                                        contentDescription = null,
                                        tint = recipe.accentColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = recipe.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = recipe.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = recipe.accentColor.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = recipe.category,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                        color = recipe.accentColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Preview of prompt box
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = recipe.prompt,
                                    style = VmTheme.code.mono.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

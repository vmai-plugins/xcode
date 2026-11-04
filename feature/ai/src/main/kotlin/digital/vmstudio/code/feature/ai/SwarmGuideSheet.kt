package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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

data class SwarmPromptTemplate(
    val title: String,
    val subtitle: String,
    val prompt: String,
    val roles: List<String>,
    val accentColor: Color,
)

private val SWARM_TEMPLATES = listOf(
    SwarmPromptTemplate(
        title = "Full-Stack Feature Swarm",
        subtitle = "Architecture, UI, backend APIs & automated unit tests",
        prompt = "@swarm Implement user authentication with biometric login, session tokens, and automated unit tests for edge cases.",
        roles = listOf("@swarm", "@architect", "@frontend", "@backend", "@tester"),
        accentColor = Color(0xFFFFB74D),
    ),
    SwarmPromptTemplate(
        title = "Deep Architecture & Security Audit",
        subtitle = "System design review, concurrency & vulnerability scan",
        prompt = "@swarm @architect @security Audit API contracts and database schema for race conditions, token leaks, and idempotency.",
        roles = listOf("@swarm", "@architect", "@security"),
        accentColor = Color(0xFFF06292),
    ),
    SwarmPromptTemplate(
        title = "Multi-Project Cross-Workspace Sync",
        subtitle = "Coordinate shared data models & API contracts across repos",
        prompt = "@swarm Sync API models between mobile client and backend repository, update endpoints, and verify end-to-end compatibility.",
        roles = listOf("@swarm", "@backend", "@coder"),
        accentColor = Color(0xFF64B5F6),
    ),
    SwarmPromptTemplate(
        title = "Clean Architecture Refactor",
        subtitle = "Decouple domain logic from UI / framework dependencies",
        prompt = "@swarm @reviewer @coder Refactor business logic into clean domain use-cases, eliminate tight coupling, and write complete test suites.",
        roles = listOf("@swarm", "@reviewer", "@coder", "@tester"),
        accentColor = Color(0xFF81C784),
    ),
    SwarmPromptTemplate(
        title = "Exhaustive QA & Edge Case Suite",
        subtitle = "Network timeouts, disk corruption & concurrency validation",
        prompt = "@swarm @tester Write exhaustive unit tests covering network timeouts, corrupted disk caches, and concurrency race conditions.",
        roles = listOf("@swarm", "@tester"),
        accentColor = Color(0xFFBA68C8),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwarmGuideSheet(
    onSelectPrompt: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFB74D).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Agent Swarm Orchestrator",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                        Text(
                            text = "Multi-agent coordination & specialized subagents",
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

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Architecture Overview Card
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "How Swarm Works",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFFB74D),
                            )
                            Text(
                                text = "Instead of running sequentially in one thread, @swarm turns the AI into a Lead Coordinator that decomposes your request into parallel subtasks per workspace and delegates them to specialist agents.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SwarmStepBadge("1. Decompose", Color(0xFFFFB74D))
                                Text("→", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                                SwarmStepBadge("2. Subagents", Color(0xFF64B5F6))
                                Text("→", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                                SwarmStepBadge("3. Synthesize", Color(0xFF81C784))
                            }
                        }
                    }
                }

                // Roles Directory
                item {
                    Text(
                        text = "SPECIALIST ROLES",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF90CAF9),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RoleChip("@architect", "Design", Color(0xFFA78BFA), Modifier.weight(1f))
                        RoleChip("@research", "Explore", Color(0xFF38BDF8), Modifier.weight(1f))
                        RoleChip("@coder", "Code", Color(0xFFFBBF24), Modifier.weight(1f))
                        RoleChip("@security", "Audit", Color(0xFFF87171), Modifier.weight(1f))
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RoleChip("@tester", "QA", Color(0xFF34D399), Modifier.weight(1f))
                        RoleChip("@frontend", "UI/Web", Color(0xFF818CF8), Modifier.weight(1f))
                        RoleChip("@backend", "APIs/DB", Color(0xFFFB923C), Modifier.weight(1f))
                        RoleChip("@reviewer", "Diff QA", Color(0xFF94A3B8), Modifier.weight(1f))
                    }
                }

                // High-Quality Production Prompt Templates
                item {
                    Text(
                        text = "PRODUCTION PROMPT TEMPLATES (TAP TO APPLY)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(SWARM_TEMPLATES.size) { idx ->
                    val tpl = SWARM_TEMPLATES[idx]
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, tpl.accentColor.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectPrompt(tpl.prompt)
                            },
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = tpl.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = tpl.accentColor.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = "Swarm",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = tpl.accentColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }

                            Text(
                                text = tpl.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = tpl.prompt,
                                    style = VmTheme.code.mono.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    tpl.roles.take(4).forEach { r ->
                                        Text(
                                            text = r,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                            color = tpl.accentColor.copy(alpha = 0.9f),
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSelectPrompt(tpl.prompt)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = tpl.accentColor.copy(alpha = 0.2f),
                                        contentColor = tpl.accentColor,
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp),
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("Apply", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwarmStepBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.35f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun RoleChip(role: String, purpose: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.35f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                color = color,
                maxLines = 1,
            )
            Text(
                text = purpose,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

package digital.vmstudio.code.feature.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * Dynamic Environment Pills displayed in the Chat screen.
 * Shows live target contexts: Active Project/Repo, VPS Server target, and Google Cloud Connector.
 * Tapping a pill triggers the corresponding selector or settings sheet.
 */
@Composable
fun EnvironmentContextBar(
    workingDirectory: String,
    serverName: String?,
    githubRepoName: String?,
    onOpenProjectPicker: () -> Unit,
    onOpenServerPicker: () -> Unit,
    onOpenConnectors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = VmTheme.spacing.md, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Context Pill 1: GitHub / VM Hub / Project Workspace
            val isGitHub = !githubRepoName.isNullOrBlank() || workingDirectory.startsWith("/github/")
            val isVmHub = workingDirectory.startsWith("/vmhub/")
            val projectLabel = when {
                !githubRepoName.isNullOrBlank() -> "GitHub: $githubRepoName"
                workingDirectory.startsWith("/github/") -> {
                    val slug = workingDirectory.removePrefix("/github/").trimEnd('/')
                    "GitHub: $slug"
                }
                isVmHub -> {
                    val slug = workingDirectory.removePrefix("/vmhub/").trimEnd('/')
                    "VM Hub: $slug"
                }
                workingDirectory.isNotBlank() && workingDirectory != "/" -> {
                    workingDirectory.trimEnd('/').substringAfterLast('/')
                }
                else -> "Workspace: General"
            }
            val (pillIcon, pillTag, pillColor) = when {
                isGitHub -> Triple(Icons.Default.Code, "GITHUB", Color(0xFF6366F1))
                isVmHub -> Triple(Icons.Default.AccountTree, "VM HUB", Color(0xFFEC4899))
                else -> Triple(Icons.Default.Folder, "DIR", MaterialTheme.colorScheme.primary)
            }
            ContextPill(
                icon = pillIcon,
                label = projectLabel,
                tag = pillTag,
                accentColor = pillColor,
                onClick = onOpenProjectPicker,
            )

            // Context Pill 2: VPS Server Target
            val vpsLabel = serverName?.ifBlank { null } ?: "Local / Cloud AI"
            ContextPill(
                icon = Icons.Default.Dns,
                label = vpsLabel,
                tag = if (serverName != null) "VPS" else "CLOUD",
                accentColor = if (serverName != null) Color(0xFF10B981) else Color(0xFF8B5CF6),
                onClick = onOpenServerPicker,
            )

            // Context Pill 3: Google Drive & Connectors
            ContextPill(
                icon = Icons.Default.Cloud,
                label = "G-Suite & Sheets",
                tag = "CONNECT",
                accentColor = Color(0xFF3B82F6),
                onClick = onOpenConnectors,
            )
        }
    }
}

@Composable
private fun ContextPill(
    icon: ImageVector,
    label: String,
    tag: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(accentColor.copy(alpha = 0.15f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            Text(
                text = tag,
                color = accentColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

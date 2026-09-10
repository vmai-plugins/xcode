package digital.vmstudio.code.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * The app's card.
 *
 * Uses a hairline outline plus a raised container rather than elevation shadows:
 * on a dark developer surface, shadows read as smudges, while an outline reads as
 * a panel boundary.
 */
@Composable
fun VmCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            width = VmTheme.spacing.hairline,
            color = VmTheme.colors.divider,
        ),
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * Section heading used to break long settings and dashboard screens into scannable
 * groups without adding a card per group.
 */
@Composable
fun VmSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = VmTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.invoke()
    }
}

@Composable
fun VmDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = VmTheme.spacing.hairline,
        color = VmTheme.colors.divider,
    )
}

/**
 * Empty state with an explicit call to action.
 *
 * Empty screens in a tool are usually a "you have not set this up yet" moment, so
 * the action matters more than the illustration.
 */
@Composable
fun VmEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(VmTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.md),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            VmButton(text = actionLabel, onClick = onAction)
        }
    }
}

/**
 * Determinate or indeterminate progress with an optional caption.
 *
 * [progress] is null when the total is unknown — which is the common case for a
 * remote command or an SFTP transfer whose size the server did not report — so the
 * component must handle it rather than each call site faking a percentage.
 */
@Composable
fun VmProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    label: String? = null,
    trailingLabel: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.xs),
    ) {
        if (label != null || trailingLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                label?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                trailingLabel?.let {
                    Text(
                        text = it,
                        style = VmTheme.code.mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (progress == null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        } else {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

/**
 * Confirmation dialog.
 *
 * [destructive] is not cosmetic: it colours the confirm action as destructive and
 * is the component the safety layer uses for `rm -rf`-class approvals, so the
 * visual weight of the prompt matches the consequence.
 */
@Composable
fun VmDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = "Cancel",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.md),
                content = content,
            )
        },
        confirmButton = {
            VmButton(
                text = confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled,
                style = if (destructive) VmButtonStyle.Destructive else VmButtonStyle.Primary,
            )
        },
        dismissButton = dismissLabel?.let {
            {
                VmButton(text = it, onClick = onDismiss, style = VmButtonStyle.Tertiary)
            }
        },
    )
}

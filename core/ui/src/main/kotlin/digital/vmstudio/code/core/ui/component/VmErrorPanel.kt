package digital.vmstudio.code.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.ui.theme.VmTheme

/** An action offered alongside an error, e.g. "Edit server" or "Check connection". */
data class VmErrorAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Renders a [VmError] in full: what happened, why, the relevant identifying detail,
 * and what to do about it.
 *
 * This exists so the "never show a bare Unknown error" rule is structurally enforced
 * — the panel takes a typed error and has no code path that renders a lone string.
 * Retry is offered only when [VmError.retryable] is true, so the app never invites
 * the user to repeat an action that cannot succeed (a changed host key, for example).
 */
@Composable
fun VmErrorPanel(
    error: VmError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    actions: List<VmErrorAction> = emptyList(),
) {
    val spacing = VmTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(spacing.lg)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = buildString {
                    append(error.summary)
                    error.reason?.let { append(". ").append(it) }
                    error.suggestedAction?.let { append(". ").append(it) }
                }
            },
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = error.summary,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                error.reason?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        if (error.details.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                error.details.forEach { (key, value) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = value,
                            style = VmTheme.code.mono,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        error.suggestedAction?.let { suggestion ->
            Text(
                text = suggestion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        val showRetry = onRetry != null && error.retryable
        if (showRetry || actions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                if (showRetry) {
                    VmButton(
                        text = "Retry",
                        onClick = { onRetry?.invoke() },
                        style = VmButtonStyle.Secondary,
                        icon = Icons.Default.Refresh,
                    )
                }
                actions.forEach { action ->
                    VmButton(
                        text = action.label,
                        onClick = action.onClick,
                        style = VmButtonStyle.Tertiary,
                    )
                }
            }
        }
    }
}

/** Single-line variant for inline placement, e.g. under a text field. */
@Composable
fun VmInlineError(
    error: VmError,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = error.reason ?: error.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

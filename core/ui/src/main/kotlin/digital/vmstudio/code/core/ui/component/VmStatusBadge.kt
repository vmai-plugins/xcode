package digital.vmstudio.code.core.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * Connection / health state shown against servers, connectors and the AI provider.
 */
enum class VmStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    DEGRADED,
    ERROR,
    IDLE,
}

/**
 * A status dot with a label.
 *
 * The dot alone is never the whole signal: colour is paired with a text label and a
 * merged content description, so the state is legible to colour-blind users and to
 * TalkBack. The pulse on CONNECTING is the only animation, and it conveys
 * "in progress" rather than decorating.
 */
@Composable
fun VmStatusBadge(
    status: VmStatus,
    label: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val colors = VmTheme.colors
    val color = when (status) {
        VmStatus.CONNECTED -> colors.connected
        VmStatus.CONNECTING -> colors.connecting
        VmStatus.DISCONNECTED -> colors.disconnected
        VmStatus.DEGRADED -> colors.degraded
        VmStatus.ERROR -> MaterialTheme.colorScheme.error
        VmStatus.IDLE -> colors.disconnected
    }

    val dotAlpha = if (status == VmStatus.CONNECTING) {
        val transition = rememberInfiniteTransition(label = "status-pulse")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "status-pulse-alpha",
        ).value
    } else {
        1f
    }

    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "$label, ${status.describe()}"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
    ) {
        StatusDot(color = color, alpha = dotAlpha)
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color, alpha: Float) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}

private fun VmStatus.describe(): String = when (this) {
    VmStatus.CONNECTED -> "connected"
    VmStatus.CONNECTING -> "connecting"
    VmStatus.DISCONNECTED -> "disconnected"
    VmStatus.DEGRADED -> "degraded"
    VmStatus.ERROR -> "error"
    VmStatus.IDLE -> "idle"
}

/**
 * A compact labelled chip for metadata: branch name, path, environment, model.
 */
@Composable
fun VmChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    monospace: Boolean = false,
) {
    Text(
        text = text,
        style = if (monospace) VmTheme.code.mono else MaterialTheme.typography.labelMedium,
        color = contentColor,
        maxLines = 1,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(containerColor)
            .padding(horizontal = VmTheme.spacing.sm, vertical = VmTheme.spacing.xs),
    )
}

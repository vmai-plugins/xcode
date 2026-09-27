package digital.vmstudio.code.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.vmstudio.code.core.ui.theme.VmTheme

enum class VmButtonStyle {
    /** The single primary action on a surface. */
    Primary,
    /** Equal-weight alternative to a primary action. */
    Secondary,
    /** Low-emphasis action; no container. */
    Tertiary,
    /** Irreversible or data-losing action. */
    Destructive,
}

/**
 * The app's button.
 *
 * Handles the two things every call site otherwise re-implements: a loading state
 * that disables interaction without changing the button's width (avoiding layout
 * jump mid-action), and a state description so TalkBack announces "busy" rather
 * than silently reporting a disabled control.
 */
@Composable
fun VmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: VmButtonStyle = VmButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    contentDescription: String? = null,
) {
    val interactive = enabled && !loading
    val semanticsModifier = modifier.semantics {
        if (loading) stateDescription = "Busy"
    }

    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
        ) {
            AnimatedVisibility(visible = loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            }
            if (icon != null && !loading) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    val sizing = Modifier.defaultMinSize(minHeight = VmTheme.spacing.minTouchTarget)

    when (style) {
        VmButtonStyle.Primary -> Button(
            onClick = onClick,
            enabled = interactive,
            modifier = semanticsModifier.then(sizing),
            content = { content() },
        )

        VmButtonStyle.Secondary -> OutlinedButton(
            onClick = onClick,
            enabled = interactive,
            modifier = semanticsModifier.then(sizing),
            content = { content() },
        )

        VmButtonStyle.Tertiary -> TextButton(
            onClick = onClick,
            enabled = interactive,
            modifier = semanticsModifier.then(sizing),
            content = { content() },
        )

        VmButtonStyle.Destructive -> Button(
            onClick = onClick,
            enabled = interactive,
            modifier = semanticsModifier.then(sizing),
            colors = ButtonDefaults.buttonColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onError,
            ),
            content = { content() },
        )
    }
}

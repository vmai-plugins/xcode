package digital.vmstudio.code.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale. A 4dp base with named steps, so layout code reads as intent
 * ("space between related rows") rather than as arbitrary numbers.
 */
@Immutable
data class VmSpacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    /** Standard horizontal screen padding on compact widths. */
    val screenHorizontal: Dp = 16.dp,
    /** Minimum touch target; enforced on icon-only controls. */
    val minTouchTarget: Dp = 48.dp,
    val hairline: Dp = 1.dp,
)

val LocalVmSpacing = staticCompositionLocalOf { VmSpacing() }

/**
 * Restrained corner radii. Large radii read as consumer-app friendliness; this is
 * a tool, and panels that meet edges should look like panels.
 */
internal val VmShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

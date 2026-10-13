package digital.vmstudio.code.feature.projects

import androidx.compose.ui.graphics.Color

object ProjectColors {
    val Palette = listOf(
        ProjectColorOption("#6366F1", "Indigo Glow"),
        ProjectColorOption("#06B6D4", "Cyber Cyan"),
        ProjectColorOption("#10B981", "Neon Emerald"),
        ProjectColorOption("#F59E0B", "Radiant Amber"),
        ProjectColorOption("#EC4899", "Electric Pink"),
        ProjectColorOption("#8B5CF6", "Vibrant Violet"),
        ProjectColorOption("#3B82F6", "Cobalt Blue"),
        ProjectColorOption("#EF4444", "Crimson Red"),
        ProjectColorOption("#14B8A6", "Mint Teal"),
        ProjectColorOption("#F97316", "Sunset Orange"),
    )

    fun parseColor(hex: String?, fallbackSeed: String = ""): Color {
        if (!hex.isNullOrBlank()) {
            try {
                val cleanHex = hex.trim().removePrefix("#")
                val fullHex = if (cleanHex.length == 6) "FF$cleanHex" else cleanHex
                return Color(fullHex.toLong(16))
            } catch (_: Exception) {}
        }
        val safeHash = fallbackSeed.hashCode().let { if (it < 0) -it else it }
        val index = if (Palette.isNotEmpty()) safeHash % Palette.size else 0
        val fallbackHex = Palette[index].hex.removePrefix("#")
        return Color("FF$fallbackHex".toLong(16))
    }
}

data class ProjectColorOption(
    val hex: String,
    val name: String,
)

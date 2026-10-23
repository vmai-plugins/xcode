package digital.vmstudio.code.feature.projects

import androidx.compose.ui.graphics.Color

object ProjectColors {
    // Dedicated Brand Accents by Project Type
    val BrandGitHub = Color(0xFF8957E5) // GitHub Violet
    val BrandVPS = Color(0xFF10B981)    // Terminal Emerald
    val BrandVMHub = Color(0xFF0284C7)  // Electric Azure
    val BrandLocal = Color(0xFFF59E0B)  // Android Amber
    val BrandDrive = Color(0xFF2563EB)  // Cloud Royal Blue

    val Palette = listOf(
        ProjectColorOption("#8957E5", "GitHub Violet"),
        ProjectColorOption("#10B981", "Terminal Emerald"),
        ProjectColorOption("#0284C7", "Electric Azure"),
        ProjectColorOption("#F59E0B", "Android Amber"),
        ProjectColorOption("#6366F1", "Indigo Glow"),
        ProjectColorOption("#06B6D4", "Cyber Cyan"),
        ProjectColorOption("#EC4899", "Electric Pink"),
        ProjectColorOption("#EF4444", "Crimson Red"),
        ProjectColorOption("#14B8A6", "Mint Teal"),
        ProjectColorOption("#F97316", "Sunset Orange"),
    )

    fun colorForCategory(remotePath: String, isLocal: Boolean): Color = when {
        remotePath.startsWith("/github/") -> BrandGitHub
        remotePath.startsWith("/vmhub/") -> BrandVMHub
        remotePath.contains("drive", ignoreCase = true) -> BrandDrive
        isLocal -> BrandLocal
        else -> BrandVPS
    }

    fun parseColor(hex: String?, fallbackSeed: String = "", defaultFallbackHex: String? = null): Color {
        val effectiveHex = hex ?: defaultFallbackHex
        if (!effectiveHex.isNullOrBlank()) {
            try {
                val cleanHex = effectiveHex.trim().removePrefix("#")
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

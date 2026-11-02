package digital.vmstudio.code.feature.projects

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectColorsTest {

    @Test
    fun `minimum integer hash selects a valid fallback color`() {
        val seed = "polygenelubricants"

        assertEquals(Int.MIN_VALUE, seed.hashCode())
        val expectedIndex = Math.floorMod(seed.hashCode(), ProjectColors.Palette.size)
        assertEquals(
            ProjectColors.parseColor(ProjectColors.Palette[expectedIndex].hex),
            ProjectColors.parseColor(null, seed),
        )
    }

    @Test
    fun `invalid configured color uses the deterministic fallback seed`() {
        assertEquals(
            ProjectColors.parseColor(null, "Workspace"),
            ProjectColors.parseColor("not-a-color", "Workspace"),
        )
    }
}

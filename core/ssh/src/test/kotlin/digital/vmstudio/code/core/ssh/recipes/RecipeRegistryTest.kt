package digital.vmstudio.code.core.ssh.recipes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the recipe catalog: every recipe has a non-empty id, name, description,
 * at least one step, and progress weights that sum to 1.0 (so the UI progress bar
 * reaches 100% exactly at completion).
 */
class RecipeRegistryTest {

    @Test
    fun `all recipes have required fields`() {
        for (recipe in RecipeRegistry.all) {
            assertTrue("Recipe ${recipe.id} must have a non-empty name", recipe.name.isNotBlank())
            assertTrue(
                "Recipe ${recipe.id} must have a non-empty description",
                recipe.description.isNotBlank(),
            )
            assertTrue("Recipe ${recipe.id} must have at least one step", recipe.steps.isNotEmpty())
            assertTrue("Recipe ${recipe.id} must have a positive estimate", recipe.estimatedSeconds > 0)
        }
    }

    @Test
    fun `all recipe progress weights sum to 1_0`() {
        for (recipe in RecipeRegistry.all) {
            val sum = recipe.steps.sumOf { it.progressWeight }
            assertEquals(
                "Recipe ${recipe.id} progress weights must sum to 1.0, was $sum",
                1.0,
                sum,
                0.001,
            )
        }
    }

    @Test
    fun `all recipe steps have non-empty commands`() {
        for (recipe in RecipeRegistry.all) {
            for (step in recipe.steps) {
                assertTrue(
                    "Recipe ${recipe.id} step '${step.label}' must have a non-empty command",
                    step.command.isNotBlank(),
                )
                assertTrue(
                    "Recipe ${recipe.id} step '${step.label}' must have a positive weight",
                    step.progressWeight > 0.0,
                )
            }
        }
    }

    @Test
    fun `byId returns correct recipe`() {
        val wp = RecipeRegistry.byId("wordpress-sandbox")
        assertNotNull("wordpress-sandbox recipe should exist", wp)
        assertEquals("WordPress Sandbox", wp!!.name)
    }

    @Test
    fun `byId returns null for unknown id`() {
        assertNull(RecipeRegistry.byId("nonexistent-recipe"))
    }

    @Test
    fun `wordpress recipe has status probe`() {
        val wp = RecipeRegistry.wordpress
        assertNotNull("WordPress recipe should have a status probe", wp.statusProbe)
        assertTrue(
            "Status probe should check for wp-config.php",
            wp.statusProbe!!.contains("wp-config.php"),
        )
    }
}

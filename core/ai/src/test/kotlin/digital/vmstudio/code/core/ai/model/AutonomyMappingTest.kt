package digital.vmstudio.code.core.ai.model

import digital.vmstudio.code.core.common.preferences.AgentAutonomyLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The autonomy level was stored and read by nothing: Settings offered four graduated
 * levels while the only thing that reached the agent was the chat's permission mode.
 * These tests pin the mapping that now connects them, and in particular that the safe
 * end of the scale stays safe.
 */
class AutonomyMappingTest {

    @Test
    fun `every autonomy level maps to a permission mode`() {
        // A missing branch would silently fall back to some default and quietly widen
        // or narrow what the agent may do, so the whole enum is covered explicitly.
        assertEquals(AgentPermissionMode.MANUAL, AgentAutonomyLevel.ASK_EVERY_TIME.toPermissionMode())
        assertEquals(AgentPermissionMode.PLAN, AgentAutonomyLevel.SAFE_AUTO.toPermissionMode())
        assertEquals(AgentPermissionMode.ACCEPT_EDITS, AgentAutonomyLevel.DEVELOPER.toPermissionMode())
        assertEquals(AgentPermissionMode.BYPASS, AgentAutonomyLevel.FULL_AGENT.toPermissionMode())
    }

    @Test
    fun `the default autonomy level never writes to the users files`() {
        // SAFE_AUTO is the shipped default. If it ever mapped to a writing mode, every
        // user who never opened Settings would be running an agent that edits code.
        val default = AgentAutonomyLevel.SAFE_AUTO.toPermissionMode()

        assertEquals(AgentPermissionMode.PLAN, default)
    }

    @Test
    fun `only the most permissive level bypasses prompts`() {
        AgentAutonomyLevel.entries
            .filter { it != AgentAutonomyLevel.FULL_AGENT }
            .forEach { level ->
                assertEquals(
                    "$level must not bypass permission prompts",
                    false,
                    level.toPermissionMode() == AgentPermissionMode.BYPASS,
                )
            }
    }

    @Test
    fun `the mapping round-trips so both controls can agree`() {
        AgentAutonomyLevel.entries.forEach { level ->
            assertEquals(level, level.toPermissionMode().toAutonomyLevel())
        }
        AgentPermissionMode.entries.forEach { mode ->
            assertEquals(mode, mode.toAutonomyLevel().toPermissionMode())
        }
    }
}

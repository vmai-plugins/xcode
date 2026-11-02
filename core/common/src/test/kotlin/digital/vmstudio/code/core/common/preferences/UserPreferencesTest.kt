package digital.vmstudio.code.core.common.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesTest {

    @Test
    fun `context window defaults to huge`() {
        assertEquals(UserPreferences.CONTEXT_HUGE, UserPreferences().aiContextSize)
    }
}

package digital.vmstudio.code.core.common.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Read-only view of the preference store.
 *
 * Exists so policy code can depend on settings without depending on DataStore and,
 * through it, on an Android `Context`. That keeps components like the command guard
 * unit-testable on the JVM — which matters here specifically, because the guard's
 * job is to enforce a setting, and a rule that cannot be tested is how the safety
 * engine came to be written and never called.
 */
interface UserPreferencesSource {
    val preferences: Flow<UserPreferences>
}

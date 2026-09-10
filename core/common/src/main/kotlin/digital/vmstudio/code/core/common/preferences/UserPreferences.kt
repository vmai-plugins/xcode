package digital.vmstudio.code.core.common.preferences

/** Appearance choice. Mirrored by the UI theme; stored as a name string. */
enum class ThemePreference { SYSTEM, LIGHT, DARK }

/**
 * How much the AI agent may do without asking.
 *
 * Ordered from least to most autonomous. Regardless of level, operations the
 * command-safety layer classifies as destructive or production-affecting always
 * require an explicit confirmation — the level raises the floor, never the ceiling.
 */
enum class AgentAutonomyLevel {
    /** Every tool call is proposed and awaits approval. */
    ASK_EVERY_TIME,

    /** Read-only inspection runs automatically; anything with an effect is asked. */
    SAFE_AUTO,

    /** File edits and approved commands run automatically. */
    DEVELOPER,

    /** Full loop: edit, create, test, build and iterate without prompting. */
    FULL_AGENT,
    ;

    /** True when [other] is permitted at this level. */
    fun allows(other: AgentAutonomyLevel): Boolean = ordinal >= other.ordinal
}

/**
 * Snapshot of everything in the app's preference store.
 *
 * A single immutable value rather than a bag of individual flows, so screens that
 * depend on several settings recompose once and cannot observe a torn combination.
 */
data class UserPreferences(
    val hasCompletedOnboarding: Boolean = false,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val editorFontSizeSp: Float = 13f,
    val terminalFontSizeSp: Float = 12.5f,
    val editorSoftWrap: Boolean = false,
    val editorShowLineNumbers: Boolean = true,
    val editorTabSize: Int = 4,
    val editorUseSpaces: Boolean = true,
    val terminalScrollbackLines: Int = 5_000,
    val agentAutonomyLevel: AgentAutonomyLevel = AgentAutonomyLevel.SAFE_AUTO,
    /**
     * Context window at which the CLI auto-compacts.
     *
     * The only agent limit this app can enforce. Iteration and command ceilings were
     * removed rather than left inert: Claude Code owns the loop and runs tools as its
     * own subprocesses, so a setting promising to cap either would be a false
     * assurance the app cannot honour.
     */
    val agentMaxContextTokens: Int = 120_000,
    val confirmDestructiveCommands: Boolean = true,
    val aiProviderId: String = PROVIDER_CLAUDE_CODE,
    val aiBaseUrl: String = DEFAULT_AI_BASE_URL,
    /** Credential reference id for the AI API key, or null when not configured. */
    val aiApiKeyCredentialId: String? = null,
    val aiSelectedModelId: String? = null,
    /**
     * Model ids the gateway last reported via `/models`, persisted so the picker
     * survives process death. Empty until the first connection test syncs them;
     * never fabricated — the list is only ever what the gateway itself answered.
     */
    val aiAvailableModelIds: List<String> = emptyList(),
    val aiModelPreset: String = "balanced",
    val aiStreamingEnabled: Boolean = true,
    val gitUserName: String = "",
    val gitUserEmail: String = "",
    val notifyOnTaskComplete: Boolean = true,
    val notifyOnTransferComplete: Boolean = true,
    val notifyOnServerDisconnect: Boolean = true,
    val notifyOnBuildComplete: Boolean = true,
    val reduceMotion: Boolean = false,
    val maxCacheBytes: Long = 256L * 1024 * 1024,
    val verboseDiagnostics: Boolean = false,
    /** Last opened project, restored on launch. */
    val activeProjectId: String? = null,
) {
    companion object {
        const val DEFAULT_AI_BASE_URL = "https://ai.vmstudio.digital"

        /** Agentic backend: Claude Code driven over SSH. The default. */
        const val PROVIDER_CLAUDE_CODE = "claude-code"

        /** Chat-only backend: an HTTP gateway called directly from the device. */
        const val PROVIDER_OMNIROUTE = "omniroute"
    }
}

package digital.vmstudio.code.core.common.preferences

/** Appearance choice. Mirrored by the UI theme; stored as a name string. */
enum class ThemePreference { SYSTEM, LIGHT, DARK }

/** Release channel for app updates via GitHub. */
enum class ReleaseChannel {
    STABLE,
    PRE_RELEASE,
}

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
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val editorFontSizeSp: Float = 13f,
    val terminalFontSizeSp: Float = 12.5f,
    val terminalScrollbackLines: Int = 5_000,
    val agentAutonomyLevel: AgentAutonomyLevel = AgentAutonomyLevel.SAFE_AUTO,
    val hasCompletedOnboarding: Boolean = false,
    val hasSignedIn: Boolean = false,
    val hubApiKey: String? = DEFAULT_HUB_API_KEY,
    val hubBaseUrl: String = DEFAULT_HUB_BASE_URL,
    val biometricLockEnabled: Boolean = false,
    /**
     * Context window at which the CLI auto-compacts.
     *
     * The only agent limit this app can enforce. Iteration and command ceilings were
     * removed rather than left inert: Claude Code owns the loop and runs tools as its
     * own subprocesses, so a setting promising to cap either would be a false
     * assurance the app cannot honour.
     */
    val agentMaxContextTokens: Int = 120_000,
    val agentMaxIterations: Int = 25,
    val agentMaxCommandsPerRun: Int = 50,
    val confirmDestructiveCommands: Boolean = true,
    /** Retained for preference compatibility; OmniRoute is the only supported provider. */
    val aiProviderId: String = PROVIDER_OMNIROUTE,
    val aiBaseUrl: String = DEFAULT_AI_BASE_URL,
    /** Credential reference id for the AI API key, or null when using the built-in online gateway key. */
    val aiApiKeyCredentialId: String? = null,
    val aiSelectedModelId: String? = DEFAULT_AI_MODEL_ID,
    /**
     * Model ids the gateway last reported via `/models`, persisted so the picker
     * survives process death. Defaults to the core online OmniRoute combo models
     * until the first live sync populates the full catalog.
     */
    val aiAvailableModelIds: List<String> = DEFAULT_AI_MODELS,
    /** The subset of [aiAvailableModelIds] the gateway marked as free (no charge). */
    val aiFreeModelIds: Set<String> = DEFAULT_FREE_AI_MODELS,
    /** When [aiAvailableModelIds] was last refreshed from the gateway; 0 if never. */
    val aiModelsSyncedAtMillis: Long = 0L,
    val aiModelPreset: String = "balanced",
    /**
     * Whether OmniRoute may call tools (read/write/edit files, search, run
     * commands, fetch pages) instead of only chatting. On by default: the app is a
     * coding agent, and every write and risky command still goes through approval.
     */
    val aiToolsEnabled: Boolean = true,
    /**
     * Whether OmniRoute is allowed to fetch external web documentation and URLs via web_fetch.
     * When false, external network fetching by the agent is disabled for privacy/offline safety.
     */
    val aiWebAccessEnabled: Boolean = true,
    /**
     * How much conversation the gateway agent keeps before trimming: one of
     * [CONTEXT_SMALL], [CONTEXT_LARGE], [CONTEXT_HUGE]. Small protects free models
     * with short windows; large and huge suit models with long ones.
     */
    val aiContextSize: String = CONTEXT_LARGE,
    /** The model last picked in a chat; a new chat starts on it. */
    val lastChatModel: String? = null,
    /** The last chat was a general one (no project), so new chats open that way. */
    val lastChatGeneral: Boolean = false,
    /** The folder last used in a chat, per server id; a new chat on that server opens there. */
    val lastFolders: Map<String, String> = emptyMap(),
    val githubTokenCredentialId: String? = null,
    val githubUsername: String? = null,
    val userEmail: String? = null,
    val userName: String? = null,
    val userAvatarUrl: String? = null,
    val googleIdToken: String? = null,
    val googleDriveTokenCredentialId: String? = null,
    val googleSheetsTokenCredentialId: String? = null,
    val googleSheetsSpreadsheetId: String? = null,
    val autoConnectServerId: String? = null,
    val vpsAutoConnectEnabled: Boolean = true,
    val evolutionApiBaseUrl: String = DEFAULT_EVOLUTION_API_BASE_URL,
    val evolutionApiKeyCredentialId: String? = null,
    val evolutionApiInstance: String? = null,
    val evolutionApiRecipientNumber: String? = null,
    val evolutionApiNotificationsEnabled: Boolean = false,
    val releaseChannel: ReleaseChannel = ReleaseChannel.STABLE,
    val chatBubbleColorHex: String? = null,
    val defaultProjectColorHex: String? = null,
    val chatColors: Map<String, String> = emptyMap(),
) {
    companion object {
        const val DEFAULT_HUB_BASE_URL = "https://vmstudio.digital/wp-json/xcodes/v1"
        const val FALLBACK_HUB_BASE_URL = "https://apps.vmstudio.digital/wp-json/xcodes/v1"
        const val DEFAULT_HUB_API_KEY = "xc_b97a8a0607420e8996f3ce2be04ad0647c7f0ec5"
        const val DEFAULT_EVOLUTION_API_BASE_URL = "https://wa.vmstudio.digital"
        /** The only supported AI gateway. */
        const val DEFAULT_AI_BASE_URL = "https://ai.vmstudio.digital"
        const val DEFAULT_AI_MODEL_ID = "auto/best-coding"
        val DEFAULT_FREE_AI_MODELS = setOf(
            "auto/coding:free",
            "auto/best-free",
            "groq/openai/gpt-oss-120b",
            "groq/qwen/qwen3.8-27b",
            "groq/moonshotai/kimi-k2-instruct-0905",
            "groq/llama-3.3-70b-versatile",
        )
        val DEFAULT_AI_MODELS = listOf(
            "auto/best-coding",
            "auto/coding:free",
            "auto/best-free",
            "auto/best-reasoning",
            "auto/best-fast",
            "auto/best-vision",
            "auto/best-chat",
            "auto/best-coding-fast",
            "auto/pro-coding",
            "auto/pro-reasoning",
            "opus",
            "opus-nvidia",
            "Kimi Coding",
            "groq/openai/gpt-oss-120b",
            "groq/qwen/qwen3.8-27b",
            "groq/moonshotai/kimi-k2-instruct-0905",
            "groq/llama-3.3-70b-versatile",
        )

        const val CONTEXT_SMALL = "small"
        const val CONTEXT_LARGE = "large"
        const val CONTEXT_HUGE = "huge"

        /** Characters of history kept for each context size (about 4 per token). */
        fun contextChars(size: String): Int = when (size) {
            CONTEXT_SMALL -> SMALL_CONTEXT_CHARS
            CONTEXT_HUGE -> HUGE_CONTEXT_CHARS
            else -> LARGE_CONTEXT_CHARS
        }

        private const val SMALL_CONTEXT_CHARS = 100_000
        private const val LARGE_CONTEXT_CHARS = 400_000
        private const val HUGE_CONTEXT_CHARS = 1_200_000

        /** The app's sole AI provider. */
        const val PROVIDER_OMNIROUTE = "omniroute"
    }
}


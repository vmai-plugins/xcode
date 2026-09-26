package digital.vmstudio.code.core.common.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vmstudio_preferences",
)

/**
 * Reads and writes app-wide settings.
 *
 * Deliberately holds no secrets: the AI API key is referenced by credential id and
 * the value itself lives in the Keystore-backed credential store.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserPreferencesSource {

    private val store = context.preferencesDataStore

    override val preferences: Flow<UserPreferences> = store.data
        .catch { throwable ->
            // A corrupt preferences file must not crash the app on launch; fall
            // back to defaults and surface it in diagnostics instead.
            if (throwable is IOException) {
                VmLog.w(LogCategory.DATABASE, TAG, "Preferences unreadable; using defaults", throwable)
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { it.toUserPreferences() }
        .distinctUntilChanged()

    suspend fun setOnboardingComplete(complete: Boolean) = edit {
        it[Keys.ONBOARDING_COMPLETE] = complete
    }

    suspend fun setThemePreference(value: ThemePreference) = edit {
        it[Keys.THEME] = value.name
    }

    suspend fun setEditorFontSize(sp: Float) = edit {
        it[Keys.EDITOR_FONT_SIZE] = sp.coerceIn(8f, 32f)
    }

    suspend fun setTerminalFontSize(sp: Float) = edit {
        it[Keys.TERMINAL_FONT_SIZE] = sp.coerceIn(8f, 32f)
    }

    suspend fun setEditorSoftWrap(enabled: Boolean) = edit { it[Keys.EDITOR_SOFT_WRAP] = enabled }

    suspend fun setEditorShowLineNumbers(enabled: Boolean) = edit {
        it[Keys.EDITOR_LINE_NUMBERS] = enabled
    }

    suspend fun setEditorTabSize(size: Int) = edit {
        it[Keys.EDITOR_TAB_SIZE] = size.coerceIn(1, 8)
    }

    suspend fun setEditorUseSpaces(useSpaces: Boolean) = edit {
        it[Keys.EDITOR_USE_SPACES] = useSpaces
    }

    suspend fun setTerminalScrollback(lines: Int) = edit {
        it[Keys.TERMINAL_SCROLLBACK] = lines.coerceIn(500, 50_000)
    }

    suspend fun setAgentAutonomyLevel(level: AgentAutonomyLevel) = edit {
        it[Keys.AGENT_AUTONOMY] = level.name
    }

    /**
     * Sets the context budget the CLI auto-compacts at.
     *
     * Clamped to the range the CLI accepts; a value it would reject fails the whole
     * run, so it is corrected here rather than at the command line.
     */
    suspend fun setAgentContextBudget(maxContextTokens: Int) = edit {
        it[Keys.AGENT_MAX_CONTEXT_TOKENS] =
            maxContextTokens.coerceIn(MIN_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS)
    }

    suspend fun setConfirmDestructiveCommands(enabled: Boolean) = edit {
        it[Keys.CONFIRM_DESTRUCTIVE] = enabled
    }

    suspend fun setAiProvider(providerId: String, baseUrl: String) = edit {
        it[Keys.AI_PROVIDER] = providerId
        it[Keys.AI_BASE_URL] = baseUrl
    }

    suspend fun setAiApiKeyCredentialId(credentialId: String?) = edit {
        if (credentialId == null) {
            it.remove(Keys.AI_API_KEY_CREDENTIAL)
        } else {
            it[Keys.AI_API_KEY_CREDENTIAL] = credentialId
        }
    }

    suspend fun setAiModel(modelId: String?, preset: String) = edit {
        if (modelId == null) it.remove(Keys.AI_MODEL) else it[Keys.AI_MODEL] = modelId
        it[Keys.AI_MODEL_PRESET] = preset
    }

    /**
     * Persists the model ids the gateway reported, newline-packed. Sanitised so a
     * misbehaving gateway can neither inject separators into an id nor grow the
     * preferences file without bound.
     */
    suspend fun setAiModels(models: List<String>) = edit {
        val sanitised = sanitizeModelIds(models)
        if (sanitised.isEmpty()) {
            it.remove(Keys.AI_MODELS)
        } else {
            it[Keys.AI_MODELS] = sanitised.joinToString(MODEL_ID_SEPARATOR)
        }
    }

    suspend fun setAiStreamingEnabled(enabled: Boolean) = edit {
        it[Keys.AI_STREAMING] = enabled
    }

    suspend fun setAiToolsEnabled(enabled: Boolean) = edit {
        it[Keys.AI_TOOLS_ENABLED] = enabled
    }

    suspend fun setGitIdentity(name: String, email: String) = edit {
        it[Keys.GIT_NAME] = name
        it[Keys.GIT_EMAIL] = email
    }

    suspend fun setNotificationPreferences(
        taskComplete: Boolean,
        transferComplete: Boolean,
        serverDisconnect: Boolean,
        buildComplete: Boolean,
    ) = edit {
        it[Keys.NOTIFY_TASK] = taskComplete
        it[Keys.NOTIFY_TRANSFER] = transferComplete
        it[Keys.NOTIFY_DISCONNECT] = serverDisconnect
        it[Keys.NOTIFY_BUILD] = buildComplete
    }

    suspend fun setReduceMotion(enabled: Boolean) = edit { it[Keys.REDUCE_MOTION] = enabled }

    suspend fun setMaxCacheBytes(bytes: Long) = edit {
        it[Keys.MAX_CACHE_BYTES] = bytes.coerceAtLeast(16L * 1024 * 1024)
    }

    suspend fun setVerboseDiagnostics(enabled: Boolean) = edit {
        it[Keys.VERBOSE_DIAGNOSTICS] = enabled
    }

    suspend fun setActiveProjectId(projectId: String?) = edit {
        if (projectId == null) {
            it.remove(Keys.ACTIVE_PROJECT)
        } else {
            it[Keys.ACTIVE_PROJECT] = projectId
        }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit(block)
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            hasCompletedOnboarding = this[Keys.ONBOARDING_COMPLETE] ?: defaults.hasCompletedOnboarding,
            themePreference = this[Keys.THEME].toEnum(defaults.themePreference),
            editorFontSizeSp = this[Keys.EDITOR_FONT_SIZE] ?: defaults.editorFontSizeSp,
            terminalFontSizeSp = this[Keys.TERMINAL_FONT_SIZE] ?: defaults.terminalFontSizeSp,
            editorSoftWrap = this[Keys.EDITOR_SOFT_WRAP] ?: defaults.editorSoftWrap,
            editorShowLineNumbers = this[Keys.EDITOR_LINE_NUMBERS] ?: defaults.editorShowLineNumbers,
            editorTabSize = this[Keys.EDITOR_TAB_SIZE] ?: defaults.editorTabSize,
            editorUseSpaces = this[Keys.EDITOR_USE_SPACES] ?: defaults.editorUseSpaces,
            terminalScrollbackLines = this[Keys.TERMINAL_SCROLLBACK] ?: defaults.terminalScrollbackLines,
            agentAutonomyLevel = this[Keys.AGENT_AUTONOMY].toEnum(defaults.agentAutonomyLevel),
            agentMaxContextTokens = this[Keys.AGENT_MAX_CONTEXT_TOKENS] ?: defaults.agentMaxContextTokens,
            confirmDestructiveCommands = this[Keys.CONFIRM_DESTRUCTIVE] ?: defaults.confirmDestructiveCommands,
            aiProviderId = this[Keys.AI_PROVIDER] ?: defaults.aiProviderId,
            aiBaseUrl = this[Keys.AI_BASE_URL] ?: defaults.aiBaseUrl,
            aiApiKeyCredentialId = this[Keys.AI_API_KEY_CREDENTIAL],
            aiSelectedModelId = this[Keys.AI_MODEL],
            aiAvailableModelIds = parseModelIds(this[Keys.AI_MODELS]),
            aiModelPreset = this[Keys.AI_MODEL_PRESET] ?: defaults.aiModelPreset,
            aiStreamingEnabled = this[Keys.AI_STREAMING] ?: defaults.aiStreamingEnabled,
            aiToolsEnabled = this[Keys.AI_TOOLS_ENABLED] ?: defaults.aiToolsEnabled,
            gitUserName = this[Keys.GIT_NAME] ?: defaults.gitUserName,
            gitUserEmail = this[Keys.GIT_EMAIL] ?: defaults.gitUserEmail,
            notifyOnTaskComplete = this[Keys.NOTIFY_TASK] ?: defaults.notifyOnTaskComplete,
            notifyOnTransferComplete = this[Keys.NOTIFY_TRANSFER] ?: defaults.notifyOnTransferComplete,
            notifyOnServerDisconnect = this[Keys.NOTIFY_DISCONNECT] ?: defaults.notifyOnServerDisconnect,
            notifyOnBuildComplete = this[Keys.NOTIFY_BUILD] ?: defaults.notifyOnBuildComplete,
            reduceMotion = this[Keys.REDUCE_MOTION] ?: defaults.reduceMotion,
            maxCacheBytes = this[Keys.MAX_CACHE_BYTES] ?: defaults.maxCacheBytes,
            verboseDiagnostics = this[Keys.VERBOSE_DIAGNOSTICS] ?: defaults.verboseDiagnostics,
            activeProjectId = this[Keys.ACTIVE_PROJECT],
        )
    }

    /** Unknown stored names fall back to the default rather than throwing. */
    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val THEME = stringPreferencesKey("theme")
        val EDITOR_FONT_SIZE = floatPreferencesKey("editor_font_size")
        val TERMINAL_FONT_SIZE = floatPreferencesKey("terminal_font_size")
        val EDITOR_SOFT_WRAP = booleanPreferencesKey("editor_soft_wrap")
        val EDITOR_LINE_NUMBERS = booleanPreferencesKey("editor_line_numbers")
        val EDITOR_TAB_SIZE = intPreferencesKey("editor_tab_size")
        val EDITOR_USE_SPACES = booleanPreferencesKey("editor_use_spaces")
        val TERMINAL_SCROLLBACK = intPreferencesKey("terminal_scrollback")
        val AGENT_AUTONOMY = stringPreferencesKey("agent_autonomy")
        val AGENT_MAX_CONTEXT_TOKENS = intPreferencesKey("agent_max_context_tokens")
        val CONFIRM_DESTRUCTIVE = booleanPreferencesKey("confirm_destructive")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_API_KEY_CREDENTIAL = stringPreferencesKey("ai_api_key_credential")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_MODELS = stringPreferencesKey("ai_models")
        val AI_MODEL_PRESET = stringPreferencesKey("ai_model_preset")
        val AI_STREAMING = booleanPreferencesKey("ai_streaming")
        val AI_TOOLS_ENABLED = booleanPreferencesKey("ai_tools_enabled")
        val GIT_NAME = stringPreferencesKey("git_name")
        val GIT_EMAIL = stringPreferencesKey("git_email")
        val NOTIFY_TASK = booleanPreferencesKey("notify_task")
        val NOTIFY_TRANSFER = booleanPreferencesKey("notify_transfer")
        val NOTIFY_DISCONNECT = booleanPreferencesKey("notify_disconnect")
        val NOTIFY_BUILD = booleanPreferencesKey("notify_build")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val MAX_CACHE_BYTES = longPreferencesKey("max_cache_bytes")
        val VERBOSE_DIAGNOSTICS = booleanPreferencesKey("verbose_diagnostics")
        val ACTIVE_PROJECT = stringPreferencesKey("active_project")
    }

    private companion object {
        const val TAG = "UserPreferencesRepository"

        /** The range the CLI accepts for --autocompact. */
        const val MIN_CONTEXT_TOKENS = 100_000
        const val MAX_CONTEXT_TOKENS = 1_000_000
    }
}

/** Model ids are joined with this separator when persisted. */
internal const val MODEL_ID_SEPARATOR = "\n"

/** Upper bound so a gateway answering with thousands of ids cannot bloat the store. */
internal const val MAX_SYNCED_MODEL_IDS = 200

/** A model id longer than this is not a model id; it is garbage. */
internal const val MAX_MODEL_ID_LENGTH = 128

internal fun sanitizeModelIds(raw: List<String>): List<String> = raw
    .map { it.trim() }
    .filter { it.isNotEmpty() && it.length <= MAX_MODEL_ID_LENGTH }
    .distinct()
    .take(MAX_SYNCED_MODEL_IDS)

internal fun parseModelIds(raw: String?): List<String> =
    raw?.split(MODEL_ID_SEPARATOR)?.let(::sanitizeModelIds).orEmpty()

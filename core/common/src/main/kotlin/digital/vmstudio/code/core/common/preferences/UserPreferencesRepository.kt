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

    suspend fun setThemePreference(value: ThemePreference) = edit {
        it[Keys.THEME] = value.name
    }

    suspend fun setEditorFontSize(sp: Float) = edit {
        it[Keys.EDITOR_FONT_SIZE] = sp.coerceIn(8f, 32f)
    }

    suspend fun setTerminalFontSize(sp: Float) = edit {
        it[Keys.TERMINAL_FONT_SIZE] = sp.coerceIn(8f, 32f)
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

    suspend fun setLastChatModel(model: String) = edit { it[Keys.LAST_CHAT_MODEL] = model }

    suspend fun setLastChatGeneral(general: Boolean) = edit { it[Keys.LAST_CHAT_GENERAL] = general }

    suspend fun setLastFolder(serverId: String, path: String) = edit {
        val folders = parseFolders(it[Keys.LAST_FOLDERS]) + (serverId to path)
        it[Keys.LAST_FOLDERS] = formatFolders(folders)
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
            it[Keys.AI_MODELS_SYNCED_AT] = System.currentTimeMillis()
        }
    }

    /** Forgets the synced model list, e.g. when the gateway it came from changes. */
    suspend fun clearAiModels() = edit {
        it.remove(Keys.AI_MODELS)
        it.remove(Keys.AI_MODELS_SYNCED_AT)
    }

    suspend fun setAiContextSize(size: String) = edit {
        it[Keys.AI_CONTEXT_SIZE] = size
    }

    suspend fun setAiToolsEnabled(enabled: Boolean) = edit {
        it[Keys.AI_TOOLS_ENABLED] = enabled
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit(block)
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            themePreference = this[Keys.THEME].toEnum(defaults.themePreference),
            editorFontSizeSp = this[Keys.EDITOR_FONT_SIZE] ?: defaults.editorFontSizeSp,
            terminalFontSizeSp = this[Keys.TERMINAL_FONT_SIZE] ?: defaults.terminalFontSizeSp,
            terminalScrollbackLines = this[Keys.TERMINAL_SCROLLBACK] ?: defaults.terminalScrollbackLines,
            agentAutonomyLevel = this[Keys.AGENT_AUTONOMY].toEnum(defaults.agentAutonomyLevel),
            agentMaxContextTokens = this[Keys.AGENT_MAX_CONTEXT_TOKENS] ?: defaults.agentMaxContextTokens,
            confirmDestructiveCommands = this[Keys.CONFIRM_DESTRUCTIVE] ?: defaults.confirmDestructiveCommands,
            aiProviderId = this[Keys.AI_PROVIDER] ?: defaults.aiProviderId,
            aiBaseUrl = this[Keys.AI_BASE_URL] ?: defaults.aiBaseUrl,
            aiApiKeyCredentialId = this[Keys.AI_API_KEY_CREDENTIAL],
            aiSelectedModelId = this[Keys.AI_MODEL],
            aiAvailableModelIds = parseModelIds(this[Keys.AI_MODELS]),
            aiModelsSyncedAtMillis = this[Keys.AI_MODELS_SYNCED_AT] ?: defaults.aiModelsSyncedAtMillis,
            aiModelPreset = this[Keys.AI_MODEL_PRESET] ?: defaults.aiModelPreset,
            aiToolsEnabled = this[Keys.AI_TOOLS_ENABLED] ?: defaults.aiToolsEnabled,
            aiContextSize = this[Keys.AI_CONTEXT_SIZE] ?: defaults.aiContextSize,
            lastChatModel = this[Keys.LAST_CHAT_MODEL],
            lastChatGeneral = this[Keys.LAST_CHAT_GENERAL] ?: false,
            lastFolders = parseFolders(this[Keys.LAST_FOLDERS]),
        )
    }

    /** Unknown stored names fall back to the default rather than throwing. */
    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val EDITOR_FONT_SIZE = floatPreferencesKey("editor_font_size")
        val TERMINAL_FONT_SIZE = floatPreferencesKey("terminal_font_size")
        val TERMINAL_SCROLLBACK = intPreferencesKey("terminal_scrollback")
        val AGENT_AUTONOMY = stringPreferencesKey("agent_autonomy")
        val AGENT_MAX_CONTEXT_TOKENS = intPreferencesKey("agent_max_context_tokens")
        val CONFIRM_DESTRUCTIVE = booleanPreferencesKey("confirm_destructive")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_API_KEY_CREDENTIAL = stringPreferencesKey("ai_api_key_credential")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_MODELS = stringPreferencesKey("ai_models")
        val AI_MODELS_SYNCED_AT = longPreferencesKey("ai_models_synced_at")
        val AI_MODEL_PRESET = stringPreferencesKey("ai_model_preset")
        val AI_TOOLS_ENABLED = booleanPreferencesKey("ai_tools_enabled")
        val AI_CONTEXT_SIZE = stringPreferencesKey("ai_context_size")
        val LAST_CHAT_MODEL = stringPreferencesKey("last_chat_model")
        val LAST_CHAT_GENERAL = booleanPreferencesKey("last_chat_general")
        val LAST_FOLDERS = stringPreferencesKey("last_folders")
    }

    private companion object {
        const val TAG = "UserPreferencesRepository"
    }
}

/** Model ids are joined with this separator when persisted. */
internal const val MODEL_ID_SEPARATOR = "\n"

/**
 * Upper bound so a runaway answer cannot bloat the store. It was 200, which cut
 * real gateways off: OmniRoute serves far more, and everything past the 200th id
 * silently vanished from the picker. 5000 ids of ~40 characters is ~200 KB.
 */
internal const val MAX_SYNCED_MODEL_IDS = 5_000

/** A model id longer than this is not a model id; it is garbage. */
internal const val MAX_MODEL_ID_LENGTH = 128

internal fun sanitizeModelIds(raw: List<String>): List<String> = raw
    .map { it.trim() }
    .filter { it.isNotEmpty() && it.length <= MAX_MODEL_ID_LENGTH }
    .distinct()
    .take(MAX_SYNCED_MODEL_IDS)

internal fun parseModelIds(raw: String?): List<String> =
    raw?.split(MODEL_ID_SEPARATOR)?.let(::sanitizeModelIds).orEmpty()

/** `serverId<TAB>path` per line; entries that cannot be stored that way are skipped. */
internal fun formatFolders(folders: Map<String, String>): String =
    folders.filter { (id, path) -> id.none { it == '\t' || it == '\n' } && path.none { it == '\n' } }
        .entries.joinToString("\n") { (id, path) -> "$id\t$path" }

internal fun parseFolders(raw: String?): Map<String, String> =
    raw.orEmpty().lineSequence()
        .mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 && it[1].isNotBlank() } }
        .associate { (id, path) -> id to path }

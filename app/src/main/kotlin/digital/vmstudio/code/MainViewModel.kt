package digital.vmstudio.code

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.net.NetworkMonitor
import digital.vmstudio.code.core.common.preferences.ThemePreference
import digital.vmstudio.code.core.common.preferences.UserPreferences
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MainUiState {
    /** Preferences have not loaded yet; the splash screen stays up. */
    data object Loading : MainUiState

    data class Ready(
        val preferences: UserPreferences,
        val isOnline: Boolean,
    ) : MainUiState {
        val themeMode: ThemeMode
            get() = when (preferences.themePreference) {
                ThemePreference.SYSTEM -> ThemeMode.SYSTEM
                ThemePreference.LIGHT -> ThemeMode.LIGHT
                ThemePreference.DARK -> ThemeMode.DARK
            }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: UserPreferencesRepository,
    networkMonitor: NetworkMonitor,
    projectHubClient: digital.vmstudio.code.core.project.VmProjectHubClient,
    serverRepository: digital.vmstudio.code.core.ssh.repository.ServerRepository,
    connectionManager: digital.vmstudio.code.core.ssh.connection.SshConnectionManager,
) : ViewModel() {

    init {
        viewModelScope.launch {
            try {
                val prefs = preferencesRepository.preferences.first()
                if (!prefs.hubApiKey.isNullOrBlank()) {
                    projectHubClient.syncHubCredentialsAndVps()
                    projectHubClient.syncUniversalProjects()
                }

                val currentPrefs = preferencesRepository.preferences.first()
                if (currentPrefs.vpsAutoConnectEnabled) {
                    val servers = serverRepository.servers.first()
                    val targetServerId = currentPrefs.autoConnectServerId?.takeIf { id -> servers.any { it.id == id } }
                        ?: servers.firstOrNull { it.host.contains("vmstudio", ignoreCase = true) || it.host == "200.234.41.231" }?.id
                        ?: servers.firstOrNull()?.id

                    if (targetServerId != null) {
                        connectionManager.session(targetServerId)
                    }
                }
            } catch (_: Throwable) {
                // Background auto-sync and auto-connect failure is non-fatal
            }
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        preferencesRepository.preferences,
        networkMonitor.isOnline,
    ) { preferences, online ->
        MainUiState.Ready(preferences = preferences, isOnline = online)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MainUiState.Loading,
    )
}

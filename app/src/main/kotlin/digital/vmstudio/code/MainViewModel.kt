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
import kotlinx.coroutines.flow.stateIn
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
) : ViewModel() {

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

package digital.vmstudio.code.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.net.NetworkMonitor
import digital.vmstudio.code.core.database.dao.ActivityDao
import digital.vmstudio.code.core.database.dao.ProjectDao
import digital.vmstudio.code.core.database.entity.ActivityEntity
import digital.vmstudio.code.core.database.entity.ProjectEntity
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val isOnline: Boolean = true,
    val servers: List<Server> = emptyList(),
    val recentProjects: List<ProjectEntity> = emptyList(),
    val recentActivity: List<ActivityEntity> = emptyList(),
) {
    val hasAnySetup: Boolean get() = servers.isNotEmpty() || recentProjects.isNotEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    serverRepository: ServerRepository,
    projectDao: ProjectDao,
    activityDao: ActivityDao,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        serverRepository.servers,
        projectDao.observeRecent(RECENT_PROJECT_LIMIT),
        activityDao.observeRecent(RECENT_ACTIVITY_LIMIT),
        networkMonitor.isOnline,
    ) { servers, projects, activity, online ->
        HomeUiState(
            isLoading = false,
            isOnline = online,
            servers = servers,
            recentProjects = projects,
            recentActivity = activity,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private companion object {
        const val RECENT_PROJECT_LIMIT = 5
        const val RECENT_ACTIVITY_LIMIT = 8
    }
}

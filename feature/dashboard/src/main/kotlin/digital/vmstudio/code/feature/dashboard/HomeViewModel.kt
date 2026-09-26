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
import digital.vmstudio.code.core.update.UpdateManager
import digital.vmstudio.code.core.update.UpdateState
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
    val updateState: UpdateState = UpdateState.Idle,
) {
    val hasAnySetup: Boolean get() = servers.isNotEmpty() || recentProjects.isNotEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    serverRepository: ServerRepository,
    projectDao: ProjectDao,
    activityDao: ActivityDao,
    networkMonitor: NetworkMonitor,
    private val updateManager: UpdateManager,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        serverRepository.servers,
        projectDao.observeRecent(RECENT_PROJECT_LIMIT),
        activityDao.observeRecent(RECENT_ACTIVITY_LIMIT),
        networkMonitor.isOnline,
        updateManager.state,
    ) { servers, projects, activity, online, updateState ->
        HomeUiState(
            isLoading = false,
            isOnline = online,
            servers = servers,
            recentProjects = projects,
            recentActivity = activity,
            updateState = updateState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun downloadUpdate() = updateManager.downloadAndInstall()

    fun retryInstall() = updateManager.retryInstall()

    fun dismissUpdate() = updateManager.dismiss()

    private companion object {
        const val RECENT_PROJECT_LIMIT = 5
        const val RECENT_ACTIVITY_LIMIT = 8
    }
}

package digital.vmstudio.code.feature.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.onFailure
import digital.vmstudio.code.core.ssh.apps.AppProcessManager
import digital.vmstudio.code.core.ssh.apps.AppProject
import digital.vmstudio.code.core.ssh.apps.AppScanner
import digital.vmstudio.code.core.ssh.command.CommandResult
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Default directory scanned for deployed apps when the user has not set one. */
internal const val DEFAULT_APPS_ROOT = "~/apps"

/** A log tail waiting to be shown, or the failure to produce one. */
data class AppLogSheetState(
    val appName: String,
    val appPath: String,
    val content: String? = null,
    val error: VmError? = null,
)

data class ServerAppsUiState(
    val serverName: String = "",
    val appsRoot: String = DEFAULT_APPS_ROOT,
    val isLoading: Boolean = true,
    val apps: List<AppProject> = emptyList(),
    /** Names of apps with an action in flight; their buttons are disabled. */
    val busyAppNames: Set<String> = emptySet(),
    val error: VmError? = null,
    val isRefreshing: Boolean = false,
    val logSheet: AppLogSheetState? = null,
) {
    val runningCount: Int get() = apps.count { it.isRunning }

    val hasAnyApp: Boolean get() = apps.isNotEmpty()
}

/**
 * Apps Hub for one server: lists applications discovered under the apps root,
 * and routes start/stop/restart/clone through the command guard so the safety
 * policy applies to a button tap exactly as it does to a typed command.
 */
@HiltViewModel
class ServerAppsViewModel @Inject constructor(
    private val scanner: AppScanner,
    private val processManager: AppProcessManager,
    serverRepository: ServerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = requireNotNull(savedStateHandle[ARG_SERVER_ID]) {
        "ServerApps requires a serverId"
    }

    private val serverName = MutableStateFlow("")
    private val apps = MutableStateFlow<List<AppProject>>(emptyList())
    private val initialLoading = MutableStateFlow(true)
    private val refreshing = MutableStateFlow(false)
    private val busy = MutableStateFlow<Set<String>>(emptySet())
    private val error = MutableStateFlow<VmError?>(null)
    private val logSheet = MutableStateFlow<AppLogSheetState?>(null)

    /** First-stage state: `combine` has no typed overload beyond five flows. */
    private data class ScanState(
        val serverName: String,
        val apps: List<AppProject>,
        val initialLoading: Boolean,
        val isRefreshing: Boolean,
        val busyAppNames: Set<String>,
    )

    private val scanState = combine(
        serverName,
        apps,
        initialLoading,
        refreshing,
        busy,
    ) { name, list, loading, refreshingNow, busyNow ->
        ScanState(name, list, loading, refreshingNow, busyNow)
    }

    val uiState: StateFlow<ServerAppsUiState> = combine(
        scanState,
        combine(error, logSheet) { currentError, sheet -> currentError to sheet },
        serverRepository.observe(serverId),
    ) { scan, (currentError, sheet), server ->
        ServerAppsUiState(
            serverName = server?.name ?: scan.serverName,
            isLoading = scan.initialLoading,
            apps = scan.apps,
            busyAppNames = scan.busyAppNames,
            error = currentError,
            isRefreshing = scan.isRefreshing,
            logSheet = sheet,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServerAppsUiState(),
    )

    init {
        viewModelScope.launch { scan() }
    }

    /** Re-scans via the pull-to-refresh / retry path, after the initial load. */
    fun refresh() = viewModelScope.launch {
        scan(isRefresh = true)
    }

    suspend fun scan(isRefresh: Boolean = false) {
        if (isRefresh) refreshing.value = true else initialLoading.value = true
        error.value = null
        when (val result = scanner.scan(serverId)) {
            is VmResult.Success -> apps.value = result.value
            is VmResult.Failure -> error.value = result.error
        }
        initialLoading.value = false
        refreshing.value = false
    }

    fun start(app: AppProject) = runAction(app) { processManager.start(serverId, it) }

    fun stop(app: AppProject) = runAction(app) { processManager.stop(serverId, it) }

    fun restart(app: AppProject) = runAction(app) { processManager.restart(serverId, it) }

    fun clone(repoUrl: String, customName: String?, branch: String?) = viewModelScope.launch {
        val url = repoUrl.trim()
        if (url.isBlank()) return@launch
        busy.value = busy.value + CLONE_BUSY_TOKEN
        error.value = null
        try {
            when (val result = processManager.cloneFromGitHub(
                serverId = serverId,
                repoUrl = url,
                appsRoot = uiState.value.appsRoot,
                customName = customName,
                branch = branch,
            )) {
                is VmResult.Failure -> error.value = result.error
                is VmResult.Success -> scan()
            }
        } finally {
            busy.value = busy.value - CLONE_BUSY_TOKEN
        }
    }

    fun requestLogs(app: AppProject) = viewModelScope.launch {
        busy.value = busy.value + app.name
        try {
            when (val result = processManager.logs(serverId, app)) {
                is VmResult.Failure -> logSheet.value = AppLogSheetState(
                    appName = app.name,
                    appPath = app.path,
                    error = result.error,
                )
                is VmResult.Success -> logSheet.value = AppLogSheetState(
                    appName = app.name,
                    appPath = app.path,
                    content = result.value.combinedOutput(),
                )
            }
        } finally {
            busy.value = busy.value - app.name
        }
    }

    fun refreshLogs() = logSheet.value?.let {
        requestLogs(AppProject(name = it.appName, path = it.appPath))
    }

    fun closeLogs() {
        logSheet.value = null
    }

    private fun runAction(
        app: AppProject,
        action: suspend (AppProject) -> VmResult<CommandResult>,
    ) {
        busy.value = busy.value + app.name
        error.value = null
        viewModelScope.launch {
            try {
                action(app).onFailure { error.value = it }
                scan()
            } finally {
                busy.value = busy.value - app.name
            }
        }
    }

    companion object {
        const val ARG_SERVER_ID = "serverId"
        private const val CLONE_BUSY_TOKEN = "\u0000clone"
    }
}
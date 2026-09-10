package digital.vmstudio.code.feature.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import digital.vmstudio.code.core.ssh.metrics.ServerMetrics
import digital.vmstudio.code.core.ssh.metrics.ServerMetricsCollector
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

/**
 * One telemetry sample, or the reason there is none.
 *
 * [metrics] keeps the last good sample through transient failures, so a dropped
 * connection leaves the dashboard up with a stale-notice instead of blanking it.
 */
data class ServerMetricsUiState(
    val serverName: String = "",
    val isLoading: Boolean = true,
    val metrics: ServerMetrics? = null,
    val error: VmError? = null,
    val fetchedAtMillis: Long = 0L,
)

/**
 * Polls server health while the sheet is on screen.
 *
 * Polling is bound to subscription: `WhileSubscribed` stops the loop a moment
 * after the sheet is dismissed, so the device does not keep SSH channels open for
 * a dashboard nobody is looking at. Auto-connects through the shared connection
 * manager, which also means a pending host-key decision surfaces here as the
 * same structured error the detail screen shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ServerMetricsViewModel @Inject constructor(
    private val connectionManager: SshConnectionManager,
    private val metricsCollector: ServerMetricsCollector,
    serverRepository: ServerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = requireNotNull(savedStateHandle[ARG_SERVER_ID]) {
        "ServerMetrics requires a serverId"
    }

    /** Bumped by [refresh] to cancel the in-flight cycle and fetch immediately. */
    private val refreshTrigger = MutableStateFlow(0)

    private var lastMetrics: ServerMetrics? = null

    val uiState: StateFlow<ServerMetricsUiState> = combine(
        serverRepository.observe(serverId),
        refreshTrigger,
    ) { server, _ -> server }
        .flatMapLatest { server ->
            flow {
                while (true) {
                    val (metrics, error) = fetchSample()
                    emit(
                        ServerMetricsUiState(
                            serverName = server?.name.orEmpty(),
                            isLoading = false,
                            metrics = metrics,
                            error = error,
                            fetchedAtMillis = metrics?.collectedAtMillis ?: 0L,
                        ),
                    )
                    delay(POLL_INTERVAL_MILLIS)
                }
            }.onStart {
                emit(ServerMetricsUiState(serverName = "", isLoading = true))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MILLIS),
            initialValue = ServerMetricsUiState(),
        )

    private suspend fun fetchSample(): Pair<ServerMetrics?, VmError?> {
        return when (val sessionResult = connectionManager.session(serverId)) {
            is VmResult.Failure -> lastMetrics to sessionResult.error
            is VmResult.Success -> when (val metricsResult = metricsCollector.collect(sessionResult.value)) {
                is VmResult.Failure -> lastMetrics to metricsResult.error
                is VmResult.Success -> {
                    lastMetrics = metricsResult.value
                    metricsResult.value to null
                }
            }
        }
    }

    fun refresh() {
        refreshTrigger.update { it + 1 }
    }

    companion object {
        const val ARG_SERVER_ID = "serverId"
        private const val POLL_INTERVAL_MILLIS = 5_000L
        private const val SUBSCRIPTION_GRACE_MILLIS = 5_000L
    }
}

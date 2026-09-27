package digital.vmstudio.code.core.update

import digital.vmstudio.code.core.common.dispatcher.ApplicationScope
import digital.vmstudio.code.core.common.result.VmResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the self-update flow, shared by every screen that
 * shows it (a dashboard banner and a Settings entry both reflect the same check
 * and the same in-flight download, rather than each running its own).
 *
 * Runs on [ApplicationScope] rather than a ViewModel's scope: a download started
 * from one screen must survive navigating away from it.
 */
@Singleton
class UpdateManager @Inject constructor(
    private val checker: UpdateChecker,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val checkMutex = Mutex()

    fun checkForUpdate() {
        if (!checkMutex.tryLock()) return
        _state.value = UpdateState.Checking
        scope.launch {
            try {
                _state.value = when (val result = checker.checkForUpdate()) {
                    is VmResult.Success -> result.value?.let { UpdateState.Available(it) }
                        ?: UpdateState.UpToDate
                    is VmResult.Failure -> UpdateState.Failed(result.error)
                }
            } finally {
                checkMutex.unlock()
            }
        }
    }

    /** Downloads the update found by the last [checkForUpdate] and installs it. */
    fun downloadAndInstall() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        scope.launch {
            _state.value = UpdateState.Downloading(info, progress = 0f)
            when (
                val result = downloader.download(info.apkDownloadUrl, info.apkAssetName) { progress ->
                    _state.value = UpdateState.Downloading(info, progress)
                }
            ) {
                is VmResult.Success -> {
                    _state.value = UpdateState.ReadyToInstall(info, result.value)
                    installer.install(result.value)
                }
                is VmResult.Failure -> _state.value = UpdateState.Failed(result.error)
            }
        }
    }

    /** Re-launches the installer for an APK already downloaded this session. */
    fun retryInstall() {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return
        installer.install(ready.apkFile)
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }
}

package digital.vmstudio.code

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import digital.vmstudio.code.core.common.dispatcher.ApplicationScope
import digital.vmstudio.code.core.common.log.DiagnosticsLogSink
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.LogLevel
import digital.vmstudio.code.core.common.log.LogcatSink
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.onFailure
import digital.vmstudio.code.core.database.StartupReconciler
import digital.vmstudio.code.core.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VmApplication : Application() {

    @Inject lateinit var diagnosticsLogSink: DiagnosticsLogSink

    @Inject lateinit var startupReconciler: StartupReconciler

    @Inject lateinit var updateManager: UpdateManager

    @Inject lateinit var projectsHubSyncManager: ProjectsHubSyncManager

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        installLogging()
        reconcileInterruptedWork()
        syncProjectsHub()
        updateManager.checkForUpdate()
    }

    private fun installLogging() {
        VmLog.configure(if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO)
        // The in-memory diagnostics buffer is always installed so a user can export
        // a report from a release build; logcat output is debug-only.
        VmLog.addSink(diagnosticsLogSink)
        if (BuildConfig.DEBUG) VmLog.addSink(LogcatSink())
        VmLog.i(LogCategory.UI, TAG, "VMStudio Code ${BuildConfig.VERSION_NAME} starting")
    }

    /**
     * Nothing in-flight survives process death, so rows left in a running state are
     * reconciled at startup and the UI never shows work that cannot still be
     * happening. Runs off the main thread; launch is not awaited because nothing on
     * the first frame depends on it.
     */
    private fun reconcileInterruptedWork() {
        applicationScope.launch {
            startupReconciler.reconcile().onFailure { error ->
                VmLog.e(LogCategory.DATABASE, TAG, "Startup reconciliation failed: ${error.summary}")
            }
        }
    }

    /**
     * Provisions default VPS ecosystem servers and synchronizes active projects from
     * the VM Project Hub WordPress REST API in the background.
     */
    private fun syncProjectsHub() {
        applicationScope.launch {
            projectsHubSyncManager.syncAll().onFailure { error ->
                VmLog.w(LogCategory.CONNECTOR, TAG, "Initial VM Project Hub sync deferred: ${error.summary}")
            }
        }
    }

    private companion object {
        const val TAG = "VmApplication"
    }
}

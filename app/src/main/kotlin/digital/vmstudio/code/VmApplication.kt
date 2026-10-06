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
import digital.vmstudio.code.crash.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VmApplication : Application() {

    @Inject lateinit var diagnosticsLogSink: DiagnosticsLogSink

    // Lazy: building them pulled Room, OkHttp and TLS setup onto the main thread
    // before the first frame.
    @Inject lateinit var startupReconciler: dagger.Lazy<StartupReconciler>

    @Inject lateinit var updateManager: dagger.Lazy<UpdateManager>

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        installLogging()
        CrashReporter.install(this, BuildConfig.VERSION_NAME) { diagnosticsLogSink.exportAsText() }
        reconcileInterruptedWork()
        checkForUpdateLater()
    }

    /** Off the startup path, and at most once a day rather than on every launch. */
    private fun checkForUpdateLater() {
        applicationScope.launch {
            val prefs = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L) < UPDATE_CHECK_INTERVAL_MILLIS) return@launch
            prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, now).apply()
            updateManager.get().checkForUpdate()
        }
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
            startupReconciler.get().reconcile().onFailure { error ->
                VmLog.e(LogCategory.DATABASE, TAG, "Startup reconciliation failed: ${error.summary}")
            }
        }
    }

    private companion object {
        const val TAG = "VmApplication"
        const val UPDATE_PREFS = "update_check"
        const val KEY_LAST_UPDATE_CHECK = "last_check_at"
        const val UPDATE_CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
    }
}

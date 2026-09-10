package digital.vmstudio.code.core.security.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clipboard access that treats sensitive content differently from ordinary text.
 *
 * Android's clipboard is readable by the foreground app and, on older releases,
 * surfaced in a system preview toast. Marking content sensitive suppresses that
 * preview on Android 13+ and lets us keep an explicit record of the fact that a
 * secret was copied, which the UI uses to warn the user.
 */
@Singleton
class SecureClipboard @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager: ClipboardManager?
        get() = context.getSystemService(ClipboardManager::class.java)

    /** Copies non-sensitive text such as a file path or a commit hash. */
    fun copyPlain(label: String, text: String): Boolean =
        copy(label, text, sensitive = false)

    /**
     * Copies a value the app knows to be a secret. Returns false if the clipboard
     * is unavailable so the caller can tell the user rather than silently no-op.
     */
    fun copySensitive(label: String, text: String): Boolean =
        copy(label, text, sensitive = true)

    private fun copy(label: String, text: String, sensitive: Boolean): Boolean {
        val clipboard = manager ?: return false
        val clip = ClipData.newPlainText(label, text)
        if (sensitive) {
            val extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            clip.description.extras = extras
        }
        return runCatching {
            clipboard.setPrimaryClip(clip)
            // The value itself is never logged; only the fact of the copy.
            VmLog.i(
                LogCategory.SECURITY,
                TAG,
                "Copied ${if (sensitive) "sensitive" else "plain"} value to clipboard: $label",
            )
            true
        }.getOrElse {
            VmLog.w(LogCategory.SECURITY, TAG, "Clipboard write failed", it)
            false
        }
    }

    /**
     * Clears the clipboard if it still holds content this app placed there.
     * Called when leaving a screen that exposed a secret.
     */
    fun clearIfOwned(label: String) {
        val clipboard = manager ?: return
        runCatching {
            val current = clipboard.primaryClip ?: return
            if (current.description?.label == label) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }

    private companion object {
        const val TAG = "SecureClipboard"
    }
}

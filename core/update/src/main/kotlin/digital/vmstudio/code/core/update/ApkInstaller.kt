package digital.vmstudio.code.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Launches the system package installer for a downloaded APK.
 *
 * If the user has not yet allowed "install unknown apps" for this app, the
 * installer itself shows that system prompt before proceeding, or this launcher
 * redirects to the unknown app sources permission setting directly.
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun install(apkFile: File): VmResult<Unit> = runCatching {
        val packageManager = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return@runCatching
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        Unit
    }.fold(
        onSuccess = { VmResult.Success(Unit) },
        onFailure = { failure ->
            VmResult.Failure(
                VmError.Unexpected(
                    summary = "Could not open the installer",
                    reason = failure.message,
                    suggestedAction = "Allow this app to install unknown apps in Android settings, then try again.",
                    cause = failure,
                ),
            )
        },
    )

    /**
     * True when [apkFile]'s signing certificate matches this app's own.
     *
     * The release pipeline delivers the APK over HTTPS with no separate checksum
     * or detached signature to check it against, so a compromised release asset
     * (a stolen maintainer token, a tampered GitHub release) would otherwise be
     * downloaded and handed straight to the installer with no independent check.
     * Android's installer would eventually refuse a signature mismatch on its own,
     * but only after the user has already been prompted to install - checking here
     * first turns that into a clear, immediate refusal instead of a confusing
     * silent installer failure.
     */
    fun signatureMatchesInstalledApp(apkFile: File): Boolean {
        val packageManager = context.packageManager
        val archiveCertificates = runCatching { archiveSigningCertificates(packageManager, apkFile) }
            .getOrNull()
        val installedCertificates = runCatching { installedSigningCertificates(packageManager) }
            .getOrNull()
        if (archiveCertificates.isNullOrEmpty() || installedCertificates.isNullOrEmpty()) return false
        return archiveCertificates.toSet() == installedCertificates.toSet()
    }

    private fun archiveSigningCertificates(packageManager: PackageManager, apkFile: File): List<String> {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES).toLong(),
                ),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES,
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
        }
        // Fix for package archive metadata parsing across OEM distributions:
        info?.applicationInfo?.let { appInfo ->
            appInfo.sourceDir = apkFile.absolutePath
            appInfo.publicSourceDir = apkFile.absolutePath
        }
        return info?.let(::signingFingerprints).orEmpty()
    }

    private fun installedSigningCertificates(packageManager: PackageManager): List<String> {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES).toLong(),
                ),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES,
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        return signingFingerprints(info)
    }

    private fun signingFingerprints(info: PackageInfo): List<String> {
        @Suppress("DEPRECATION")
        val legacySignatures: Array<Signature>? = info.signatures
        // On Android 9+ (API 28+), signingInfo has either apkContentsSigners (if multiple
        // simultaneous signers) or signingCertificateHistory (for single-signer apps).
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            when {
                signingInfo == null -> legacySignatures
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            } ?: legacySignatures
        } else {
            legacySignatures
        }
        return signatures.orEmpty().map { signature ->
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}


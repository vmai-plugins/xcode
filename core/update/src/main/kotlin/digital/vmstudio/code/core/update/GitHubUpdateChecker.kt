package digital.vmstudio.code.core.update

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.preferences.ReleaseChannel
import digital.vmstudio.code.core.common.preferences.UserPreferencesSource
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.map
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.common.version.AppVersionProvider
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import digital.vmstudio.code.core.update.model.GitHubRelease
import digital.vmstudio.code.core.update.model.UpdateInfo
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks GitHub's Releases API for a release newer than the installed version.
 *
 * Supports both STABLE (full releases via latest) and PRE_RELEASE (Nightlies/Betas),
 * and automatically attaches the configured GitHub Personal Access Token if present
 * to avoid unauthenticated API rate limits.
 */
@Singleton
class GitHubUpdateChecker @Inject constructor(
    private val httpClient: VmHttpClient,
    private val json: Json,
    private val appVersionProvider: AppVersionProvider,
    private val preferences: UserPreferencesSource,
    private val credentialStore: SecureCredentialStore,
) : UpdateChecker {

    override suspend fun checkForUpdate(): VmResult<UpdateInfo?> {
        val prefs = preferences.preferences.first()
        val isPreRelease = prefs.releaseChannel == ReleaseChannel.PRE_RELEASE

        val endpoint = if (isPreRelease) {
            "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=5"
        } else {
            "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/vnd.github+json")

        // Include user's stored GitHub PAT if available to bypass IP rate limits
        prefs.githubTokenCredentialId?.let { credId ->
            (credentialStore.read(credId) as? VmResult.Success)?.value?.let { secret ->
                try {
                    requestBuilder.header("Authorization", "Bearer ${secret.reveal()}")
                } finally {
                    secret.wipe()
                }
            }
        }

        val request = requestBuilder.build()

        return httpClient.execute(request, RetryPolicy.NONE)
            .flatMap { body ->
                if (isPreRelease) {
                    parseReleases(body).map { releases ->
                        releases.firstNotNullOfOrNull { it.toUpdateInfoOrNull(allowPreRelease = true) }
                    }
                } else {
                    parseRelease(body).map { release ->
                        release.toUpdateInfoOrNull(allowPreRelease = false)
                    }
                }
            }
    }

    private fun parseRelease(body: String): VmResult<GitHubRelease> = vmCatching(
        mapper = {
            VmError.Unexpected(
                summary = "Could not read the latest release",
                reason = it.message,
                cause = it,
            )
        },
    ) {
        json.decodeFromString<GitHubRelease>(body)
    }

    private fun parseReleases(body: String): VmResult<List<GitHubRelease>> = vmCatching(
        mapper = {
            VmError.Unexpected(
                summary = "Could not read releases list",
                reason = it.message,
                cause = it,
            )
        },
    ) {
        json.decodeFromString<List<GitHubRelease>>(body)
    }

    private fun GitHubRelease.toUpdateInfoOrNull(allowPreRelease: Boolean): UpdateInfo? {
        if (draft) return null
        if (!allowPreRelease && prerelease) return null
        val remoteVersion = tagName.removePrefix("v")

        // For stable channel, ensure remoteVersion is newer than current.
        // For nightly channel, allow if tag is nightly or newer.
        val isNewer = if (remoteVersion.equals("nightly", ignoreCase = true)) {
            allowPreRelease
        } else {
            remoteVersion.isNewerThan(appVersionProvider.versionName)
        }
        if (!isNewer) return null

        val apk = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        return UpdateInfo(
            versionName = remoteVersion,
            releaseNotes = body?.takeIf { it.isNotBlank() },
            htmlUrl = htmlUrl,
            apkDownloadUrl = apk.browserDownloadUrl,
            apkAssetName = apk.name,
        )
    }

    private companion object {
        const val OWNER = "vmai-plugins"
        const val REPO = "xcode"
    }
}

/**
 * Plain numeric semver compare ("1.2.10" > "1.2.9"). A non-numeric suffix on a
 * segment (a "-beta" tag) is ignored rather than rejected, so a malformed tag fails
 * open to "not newer" instead of crashing the check.
 */
private fun String.isNewerThan(other: String): Boolean {
    val theseParts = versionParts()
    val otherParts = other.versionParts()
    for (i in 0 until maxOf(theseParts.size, otherParts.size)) {
        val a = theseParts.getOrElse(i) { 0 }
        val b = otherParts.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

private fun String.versionParts(): List<Int> =
    removePrefix("v").split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

package digital.vmstudio.code.core.update

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.map
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.common.version.AppVersionProvider
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import digital.vmstudio.code.core.update.model.GitHubRelease
import digital.vmstudio.code.core.update.model.UpdateInfo
import kotlinx.serialization.json.Json
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks GitHub's Releases API for a release newer than the installed version.
 *
 * The app has no Play Store distribution, so this is the only update signal it
 * has: whatever the maintainer most recently tagged and attached an APK to.
 */
@Singleton
class GitHubUpdateChecker @Inject constructor(
    private val httpClient: VmHttpClient,
    private val json: Json,
    private val appVersionProvider: AppVersionProvider,
) : UpdateChecker {

    override suspend fun checkForUpdate(): VmResult<UpdateInfo?> {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()

        // A single attempt: an update check that silently retries for a while on a
        // slow network would delay the screen it's backing for no real benefit.
        return httpClient.execute(request, RetryPolicy.NONE)
            .flatMap(::parseRelease)
            .map { release -> release.toUpdateInfoOrNull() }
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

    private fun GitHubRelease.toUpdateInfoOrNull(): UpdateInfo? {
        if (draft || prerelease) return null
        val remoteVersion = tagName.removePrefix("v")
        if (!remoteVersion.isNewerThan(appVersionProvider.versionName)) return null
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

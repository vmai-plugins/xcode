package digital.vmstudio.code.core.update.model

/** A newer release than the one installed, with what's needed to fetch and show it. */
data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String?,
    val htmlUrl: String,
    val apkDownloadUrl: String,
    val apkAssetName: String,
)

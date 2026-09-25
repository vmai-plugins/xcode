package digital.vmstudio.code.core.common.version

/**
 * The installed app's own version, as a seam so library modules (which cannot see
 * the `:app` module's generated `BuildConfig`) can still compare it against a
 * remote version. Bound in the `:app` module, where `BuildConfig.VERSION_NAME`
 * actually lives.
 */
interface AppVersionProvider {
    val versionName: String
}

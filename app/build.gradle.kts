import java.util.Properties

plugins {
    id("vmstudio.android.application")
    id("vmstudio.android.compose")
    id("vmstudio.android.hilt")
}

/**
 * Release signing material, from the environment first (CI) and a gitignored
 * properties file second (local).
 *
 * Nothing here fails when signing is not configured: the release build stays
 * unsigned so a contributor without the keystore can still verify that R8 and lint
 * pass, which is the part of the release build that actually breaks.
 */
val signingProperties = Properties().apply {
    rootProject.file("keystore.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { load(it) }
}

fun signingValue(envName: String, propertyName: String): String? =
    System.getenv(envName) ?: signingProperties.getProperty(propertyName)

val releaseStoreFile = signingValue("VMSTUDIO_KEYSTORE_FILE", "storeFile")
val releaseStorePassword = signingValue("VMSTUDIO_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("VMSTUDIO_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("VMSTUDIO_KEY_PASSWORD", "keyPassword")

val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() } && file(releaseStoreFile!!).exists()

android {
    namespace = "digital.vmstudio.code"

    signingConfigs {
        // One debug key for every machine. Without it each CI runner signed test
        // builds with its own throwaway ~/.android/debug.keystore, so no nightly
        // could install over the previous one ("package conflicts") and updating
        // meant uninstalling, which wiped servers and keys. Debug keys are not
        // secret: this one only signs test builds, never a release.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // v1 is required for API 26-27; v2/v3 for everything since.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "VERBOSE_DIAGNOSTICS", "true")
        }
        getByName("release") {
            buildConfigField("boolean", "VERBOSE_DIAGNOSTICS", "false")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // The release build exactly (R8 shrinking and all), signed with the debug
        // key so CI can install it on an emulator and prove it starts. Shrinking
        // bugs (a stripped serializer, a Hilt class) only show at runtime, and only
        // in this variant. Its own app id keeps it apart from every real install.
        create("smoke") {
            initWith(getByName("release"))
            applicationIdSuffix = ".smoke"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

// Feature modules are added here as each phase lands, so the app only ever ships
// screens that are actually implemented. See DEVELOPMENT.md for the phase order.
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.ui)
    implementation(projects.core.security)
    implementation(projects.core.database)
    implementation(projects.core.ssh)
    implementation(projects.core.sftp)
    implementation(projects.core.terminal)
    implementation(projects.core.ai)
    implementation(projects.core.connectors)
    implementation(projects.core.update)

    implementation(projects.feature.servers)
    implementation(projects.feature.settings)
    implementation(projects.feature.terminal)
    implementation(projects.feature.files)
    implementation(projects.feature.ai)
    implementation(projects.feature.projects)
    implementation(projects.feature.editor)
    implementation(projects.feature.tasks)
    implementation(projects.feature.connectors)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.extended)
}
